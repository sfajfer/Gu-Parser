package sfajfer.GuParser;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GuParser {

    @Autowired
    private MongoTemplate mongoTemplate;

    public void parseAndPopulate(String filePath) {
        System.out.println("Starting Gu Index refinement process...");
        
        MongoDatabase db = mongoTemplate.getDb();
        MongoCollection<Document> collection = db.getCollection("GuIndex");

        collection.drop(); // Wipes the collection to prevent duplicates

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            Document currentGu = null;
            
            StringBuilder effectBuilder = new StringBuilder();
            StringBuilder combatActionsBuilder = new StringBuilder();
            Document steedDoc = null;
            Document currentTable = null;
            
            boolean inEffect = false;
            boolean inCombatActions = false;

            String currentPath = "Unknown";

            Pattern rankPattern = Pattern.compile("\\*Rank\\s+(\\d+)(?:-(\\d+))?\\s+(.+)\\*");
            Pattern keywordPattern = Pattern.compile("\\[\\*\\*(.*?)\\*\\*\\]");

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.startsWith("## ")) {
                    currentPath = trimmed.substring(3)
                            .replace("$", "")
                            .replace("\\centerline{", "")
                            .replace("}", "")
                            .replace("*", "")
                            .trim();
                    continue;
                }

                if (trimmed.equals("::: columns") || trimmed.equals(":::")) {
                    continue;
                }

                if (trimmed.isEmpty()) {
                    if (inEffect && effectBuilder.length() > 0) effectBuilder.append("\n\n");
                    else if (inCombatActions && combatActionsBuilder.length() > 0) combatActionsBuilder.append("\n\n");
                    continue;
                }

                if (trimmed.startsWith("### ")) {
                    // SAVE PREVIOUS GU
                    saveGu(collection, currentPath, currentGu, effectBuilder, steedDoc, combatActionsBuilder);

                    // RESET FOR NEW GU
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

                if (trimmed.startsWith("*Rank ") && trimmed.endsWith("*")) {
                    Matcher m = rankPattern.matcher(trimmed);
                    if (m.find()) {
                        int startRank = Integer.parseInt(m.group(1));
                        int endRank = m.group(2) != null ? Integer.parseInt(m.group(2)) : startRank;
                        List<Integer> ranks = new ArrayList<>();
                        for (int i = startRank; i <= endRank; i++) ranks.add(i);
                        currentGu.append("Rank", ranks);
                        currentGu.append("Type", m.group(3).trim());
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
                else if (trimmed.contains("***Combat Actions***")) inCombatActions = true;
                else if (trimmed.startsWith("Effect:")) {
                    inEffect = true;
                    inCombatActions = false;
                    effectBuilder.append(trimmed.substring(7).trim()).append("\n");
                } 
                else if (inEffect) effectBuilder.append(trimmed).append("\n");
                else if (inCombatActions) combatActionsBuilder.append(trimmed).append("\n");
            }

            // FINAL SAVE FOR THE LAST GU IN THE FILE
            saveGu(collection, currentPath, currentGu, effectBuilder, steedDoc, combatActionsBuilder);
            System.out.println("Gu Index successfully refined into MongoDB.");

        } catch (IOException e) {
            System.err.println("Failed to read Gu Index file: " + e.getMessage());
        }
    }

    // THE MISSING PIECE
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