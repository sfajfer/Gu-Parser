package sfajfer.GuParser;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GuParser {

    @Autowired
    private MongoTemplate mongoTemplate;

    public void parseAndPopulate(String fileName) {
        System.out.println("Starting Gu Index refinement process using file: " + fileName);
        
        MongoDatabase db = mongoTemplate.getDb();
        MongoCollection<Document> collection = db.getCollection("GuIndex");
        collection.drop();

        // Use getResourceAsStream to read from inside the JAR or classpath
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
            Document steedDoc = null;
            Document currentTable = null;
            
            boolean inEffect = false;
            boolean inCombatActions = false;
            String currentPath = "Unknown";

            Pattern rankPattern = Pattern.compile("\\*Rank\\s+([\\d,\\-\\s]+)\\s+(.+)\\*");
            Pattern keywordPattern = Pattern.compile("\\[\\*\\*(.*?)\\*\\*\\]");

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.startsWith("## ")) {
                    currentPath = trimmed.substring(3)
                            .replace("$", "").replace("\\centerline{", "")
                            .replace("}", "").replace("*", "").trim();
                    continue;
                }

                if (trimmed.equals("::: columns") || trimmed.equals(":::")) continue;

                if (trimmed.isEmpty()) {
                    if (inEffect && effectBuilder.length() > 0) effectBuilder.append("\n\n");
                    else if (inCombatActions && combatActionsBuilder.length() > 0) combatActionsBuilder.append("\n\n");
                    continue;
                }

                if (trimmed.startsWith("### ")) {
                    saveGu(collection, currentPath, currentGu, effectBuilder, steedDoc, combatActionsBuilder);
                    
                    currentGu = new Document("Name", trimmed.substring(4).trim());
                    effectBuilder = new StringBuilder();
                    combatActionsBuilder = new StringBuilder();
                    steedDoc = null;
                    currentTable = null;
                    inEffect = false;
                    inCombatActions = false;
                    continue;
                }

                if (currentGu == null) continue;

                // --- Parsing Logic ---
                if (trimmed.startsWith("*Rank ") && trimmed.endsWith("*")) {
                    Matcher m = rankPattern.matcher(trimmed);
                    if (m.find()) {
                        String rankRaw = m.group(1).trim(); // This will be "3, 5" or "1-3, 5"
                        String type = m.group(2).trim();   // This will be "Attack"
                        
                        List<Integer> ranks = new ArrayList<>();
                        
                        // Split by comma first
                        String[] parts = rankRaw.split(",");
                        for (String part : parts) {
                            part = part.trim();
                            if (part.contains("-")) {
                                // Handle ranges like "1-3"
                                String[] range = part.split("-");
                                int start = Integer.parseInt(range[0].trim());
                                int end = Integer.parseInt(range[1].trim());
                                for (int i = start; i <= end; i++) ranks.add(i);
                            } else {
                                // Handle single numbers like "5"
                                ranks.add(Integer.parseInt(part));
                            }
                        }
                        
                        currentGu.append("rank", ranks);
                        currentGu.append("type", type);
                    }
                }
                else if (trimmed.startsWith("Cost:")) currentGu.append("Cost", trimmed.substring(5).trim());
                else if (trimmed.startsWith("Range:")) currentGu.append("Range", trimmed.substring(6).trim());
                else if (trimmed.startsWith("Health:")) {
                    try { currentGu.append("Health", Integer.parseInt(trimmed.substring(7).trim())); }
                    catch (NumberFormatException e) { currentGu.append("Health", 0); }
                } 
                else if (trimmed.startsWith("Food:")) currentGu.append("Food", trimmed.substring(5).trim());
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
                else if (inEffect) effectBuilder.append(trimmed).append("\n");
                else if (inCombatActions) combatActionsBuilder.append(trimmed).append("\n");
            }

            saveGu(collection, currentPath, currentGu, effectBuilder, steedDoc, combatActionsBuilder);
            System.out.println("Gu Index successfully refined into MongoDB.");

        } catch (IOException e) {
            System.err.println("Failed to read Gu Index file: " + e.getMessage());
        }
    }

    private void saveGu(MongoCollection<Document> collection, String currentPath, Document currentGu, 
                        StringBuilder effectBuilder, Document steedDoc, 
                        StringBuilder combatActionsBuilder) {
        if (currentGu != null) {
            currentGu.append("Path", currentPath);
            currentGu.append("Effect", effectBuilder.toString().trim());
            if (steedDoc != null) {
                if (combatActionsBuilder.length() > 0) {
                    steedDoc.append("CombatActions", combatActionsBuilder.toString().trim());
                }
                currentGu.append("Steed", steedDoc);
            }
            collection.insertOne(currentGu);
        }
    }
}