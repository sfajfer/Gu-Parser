package sfajfer.GuParser;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GuParser {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String JSON_OUTPUT_PATH = "frontend/src/assets/gu-index.json";

    public void parseAndPopulate(String fileName) {

        int idCounter = 0;

        System.out.println("Starting Gu Index refinement process using file: " + fileName);

        MongoDatabase db = mongoTemplate.getDb();
        MongoCollection<Document> collection = db.getCollection("GuIndex");
        collection.drop();

        // Accumulate every parsed Gu so we can write them all to JSON at the end
        List<Document> allGuEntries = new ArrayList<>();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                System.err.println("FATAL ERROR: Could not find " + fileName + " in resources.");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            Document currentGu = null;
            StringBuilder effectBuilder = new StringBuilder();
            StringBuilder combatActionsBuilder = new StringBuilder();
            StringBuilder descriptionBuilder = new StringBuilder();
            Document steedDoc = null;
            Document currentTable = null;

            boolean inEffect = false;
            boolean inCombatActions = false;
            boolean inPath = false;
            String currentPath = "Unknown";

            Pattern rankPattern = Pattern.compile("\\*Rank\\s+([\\d,\\- ]+?)\\s+([A-Za-z].*)\\*");
            Pattern keywordPattern = Pattern.compile("\\[\\*\\*(.*?)\\*\\*\\]");

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.startsWith("## ")) {
                    currentPath = trimmed.substring(3)
                            .replace("$", "").replace("\\centerline{", "")
                            .replace("}", "").replace("*", "").trim();
                    inPath = true;
                    continue;
                }

                if (trimmed.startsWith("*") && inPath) {
                    inPath = false;
                    continue;
                }

                if (trimmed.equals("::: columns") || trimmed.equals(":::") || trimmed.equals("\\newpage")) continue;

                if (trimmed.isEmpty()) {
                    if (inEffect && effectBuilder.length() > 0) effectBuilder.append("\n\n");
                    else if (inCombatActions && combatActionsBuilder.length() > 0) combatActionsBuilder.append("\n\n");
                    continue;
                }

                if (trimmed.startsWith("### ")) {
                    saveGu(collection, currentGu, effectBuilder, descriptionBuilder, steedDoc, combatActionsBuilder, allGuEntries, idCounter);
                    idCounter++;

                    currentGu = new Document("Name", trimmed.substring(4).trim());
                    currentGu.append("Path", currentPath);
                    effectBuilder = new StringBuilder();
                    combatActionsBuilder = new StringBuilder();
                    descriptionBuilder = new StringBuilder();
                    steedDoc = null;
                    currentTable = null;
                    inEffect = false;
                    inCombatActions = false;
                    continue;
                }

                if (currentGu == null) continue;

                else if (trimmed.startsWith("*") && trimmed.endsWith("*") 
                        && !trimmed.startsWith("*Rank")
                        && !currentGu.containsKey("Rank")) {
                    descriptionBuilder.append(trimmed);
                }

                if (trimmed.startsWith("*Rank ") && trimmed.endsWith("*")) {
                    Matcher m = rankPattern.matcher(trimmed);
                    if (m.find()) {
                        String rankRaw = m.group(1).trim();
                        String type    = m.group(2).trim();

                        List<Integer> ranks = new ArrayList<>();
                        for (String part : rankRaw.split(",")) {
                            part = part.trim();
                            if (part.contains("-")) {
                                String[] range = part.split("-");
                                int start = Integer.parseInt(range[0].trim());
                                int end   = Integer.parseInt(range[1].trim());
                                for (int i = start; i <= end; i++) ranks.add(i);
                            } else {
                                ranks.add(Integer.parseInt(part));
                            }
                        }

                        currentGu.append("Rank", ranks);
                        currentGu.append("Type", type);
                    }
                }
                else if (trimmed.startsWith("Cost:"))    currentGu.append("Cost",  trimmed.substring(5).trim());
                else if (trimmed.startsWith("Range:"))   currentGu.append("Range", trimmed.substring(6).trim());
                else if (trimmed.startsWith("Health:")) currentGu.append("Health", trimmed.substring(7).trim());
                else if (trimmed.startsWith("Food:"))    currentGu.append("Food", trimmed.substring(5).trim());
                else if (trimmed.startsWith("Keywords:")) {
                    Matcher m = keywordPattern.matcher(trimmed);
                    List<String> keywords = new ArrayList<>();
                    while (m.find()) keywords.add(m.group(1));
                    currentGu.append("Keywords", keywords);
                }
                else if (trimmed.startsWith("CR:")) {
                    try { steedDoc = new Document("CR", Integer.parseInt(trimmed.substring(3).trim())); }
                    catch (NumberFormatException e) { steedDoc = new Document("CR", trimmed.substring(3).trim()); }
                }
                else if (steedDoc != null && trimmed.contains("\\textbf{Attributes}")) {
                    currentTable = new Document();
                    steedDoc.append("Attributes", currentTable);
                }
                else if (steedDoc != null && trimmed.contains("\\textbf{Skills}")) {
                    currentTable = new Document();
                    steedDoc.append("Skills", currentTable);
                }
                else if (currentTable != null && trimmed.contains("&")) {
                    String cleaned = trimmed.replace("\\hline", "").replace("\\\\", "").trim();
                    String[] parts = cleaned.split("&");
                    if (parts.length == 2 && !parts[0].contains("\\textbf")) {
                        currentTable.append(parts[0].trim(), parts[1].trim());
                    }
                }
                else if (trimmed.startsWith("\\end{tabular}")) currentTable = null;
                else if (trimmed.contains("***Combat Actions***")) {
                    inCombatActions = true;
                    inEffect = false;
                }
                else if (trimmed.startsWith("Effect:")) {
                    inEffect = true;
                    inCombatActions = false;
                    effectBuilder.append(trimmed.substring(7).trim()).append("\n");
                }
                else if (inEffect)         effectBuilder.append(trimmed).append("\n");
                else if (inCombatActions)  combatActionsBuilder.append(trimmed).append("\n");
            }

            // Save the very last Gu in the file
            saveGu(collection, currentGu, effectBuilder, descriptionBuilder, steedDoc, combatActionsBuilder, allGuEntries, idCounter);
            System.out.println("Gu Index successfully refined into MongoDB.");

            // Write all accumulated entries to a JSON file
            writeJsonFile(allGuEntries);

        } catch (IOException e) {
            System.err.println("Failed to read Gu Index file: " + e.getMessage());
        }
    }

    private void saveGu(MongoCollection<Document> collection, Document currentGu,
                    StringBuilder effectBuilder, StringBuilder descriptionBuilder,
                    Document steedDoc, StringBuilder combatActionsBuilder,
                    List<Document> allGuEntries, int id) {
        if (currentGu != null) {
            String effect = effectBuilder.toString().trim();
            if (descriptionBuilder.length() > 0) {
                effect = effect + "\n\n" + descriptionBuilder.toString().trim();
                System.out.println(descriptionBuilder.toString());
            }
            currentGu.append("Effect", effect);
            currentGu.append("id", id);
            if (steedDoc != null) {
                if (combatActionsBuilder.length() > 0) {
                    steedDoc.append("CombatActions", combatActionsBuilder.toString().trim());
                }
                currentGu.append("Steed", steedDoc);
            }

            collection.insertOne(currentGu);

            Document copy = new Document(currentGu);
            copy.remove("_id");
            allGuEntries.add(copy);
        }
    }

    /**
     * Serialises every parsed Gu entry to a pretty-printed JSON array and writes
     * it to {@link #JSON_OUTPUT_PATH}.  The frontend can fetch this file the same
     * way it would query the REST API — the field names are identical to those
     * stored in MongoDB.
     */
    private void writeJsonFile(List<Document> entries) {
        try {
            Path outputPath = Paths.get(JSON_OUTPUT_PATH);
            Files.createDirectories(outputPath.getParent());

            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < entries.size(); i++) {
                sb.append("  ").append(toLowerCamelKeys(entries.get(i)));
                if (i < entries.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");

            Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
            System.out.println("JSON export written to: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write JSON export: " + e.getMessage());
        }
    }

    private String toLowerCamelKeys(Document doc) {
        Document result = new Document();
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String newKey = Character.toLowerCase(key.charAt(0)) + key.substring(1);

            // Recursively handle nested Documents (e.g. Steed)
            if (value instanceof Document) {
                value = Document.parse(toLowerCamelKeys((Document) value));
            }

            result.append(newKey, value);
        }
        return result.toJson();
    }
}