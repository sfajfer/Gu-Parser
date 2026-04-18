package sfajfer.GuParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuParserLocal {

    private static final String JSON_OUTPUT_PATH = "frontend/src/assets/gu-index.json";

    public static void main(String[] args) {
        GuParserLocal parser = new GuParserLocal();
        parser.parseAndPopulate("Gu Index.md");
    }

    public void parseAndPopulate(String fileName) {
        int idCounter = 0;

        System.out.println("Starting Gu Index refinement process using file: " + fileName);

        // Accumulate every parsed Gu so we can write them all to JSON at the end
        List<Map<String, Object>> allGuEntries = new ArrayList<>();

        File file = new File(fileName);
        if (!file.exists()) {
            System.err.println("FATAL ERROR: Could not find " + fileName + " in the current directory.");
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            Map<String, Object> currentGu = null;
            StringBuilder effectBuilder = new StringBuilder();
            StringBuilder combatActionsBuilder = new StringBuilder();
            StringBuilder descriptionBuilder = new StringBuilder();
            Map<String, Object> steedDoc = null;
            Map<String, Object> currentTable = null;

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
                    saveGu(currentGu, effectBuilder, descriptionBuilder, steedDoc, combatActionsBuilder, allGuEntries, idCounter);
                    idCounter++;

                    currentGu = new LinkedHashMap<>();
                    currentGu.put("Name", trimmed.substring(4).trim());
                    currentGu.put("Path", currentPath);
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
                        String type = m.group(2).trim();

                        List<Integer> ranks = new ArrayList<>();
                        for (String part : rankRaw.split(",")) {
                            part = part.trim();
                            if (part.isEmpty()) continue;
                            if (part.contains("-")) {
                                String[] range = part.split("-");
                                int start = Integer.parseInt(range[0].trim());
                                int end = Integer.parseInt(range[1].trim());
                                for (int i = start; i <= end; i++) ranks.add(i);
                            } else {
                                ranks.add(Integer.parseInt(part));
                            }
                        }

                        currentGu.put("Rank", ranks);
                        currentGu.put("Type", type);
                    }
                }
                else if (trimmed.startsWith("Cost:"))    currentGu.put("Cost",  trimmed.substring(5).trim());
                else if (trimmed.startsWith("Range:"))   currentGu.put("Range", trimmed.substring(6).trim());
                else if (trimmed.startsWith("Health:"))  currentGu.put("Health", trimmed.substring(7).trim());
                else if (trimmed.startsWith("Food:"))    currentGu.put("Food", trimmed.substring(5).trim());
                else if (trimmed.startsWith("Keywords:")) {
                    Matcher m = keywordPattern.matcher(trimmed);
                    List<String> keywords = new ArrayList<>();
                    while (m.find()) keywords.add(m.group(1));
                    currentGu.put("Keywords", keywords);
                }
                else if (trimmed.startsWith("CR:")) {
                    steedDoc = new LinkedHashMap<>();
                    try { steedDoc.put("CR", Integer.parseInt(trimmed.substring(3).trim())); }
                    catch (NumberFormatException e) { steedDoc.put("CR", trimmed.substring(3).trim()); }
                }
                else if (steedDoc != null && trimmed.contains("\\textbf{Attributes}")) {
                    currentTable = new LinkedHashMap<>();
                    steedDoc.put("Attributes", currentTable);
                }
                else if (steedDoc != null && trimmed.contains("\\textbf{Skills}")) {
                    currentTable = new LinkedHashMap<>();
                    steedDoc.put("Skills", currentTable);
                }
                else if (currentTable != null && trimmed.contains("&")) {
                    String cleaned = trimmed.replace("\\hline", "").replace("\\\\", "").trim();
                    String[] parts = cleaned.split("&");
                    if (parts.length == 2 && !parts[0].contains("\\textbf")) {
                        currentTable.put(parts[0].trim(), parts[1].trim());
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
                else if (inEffect) {
                    if (trimmed.startsWith("|")) { // Markdown tables
                        if (effectBuilder.length() > 0 && effectBuilder.charAt(effectBuilder.length() - 1) == ' ') {
                            effectBuilder.setLength(effectBuilder.length() - 1);
                        }
                        effectBuilder.append(trimmed).append("\n");
                    } else {
                        if (effectBuilder.length() > 0 && effectBuilder.charAt(effectBuilder.length() - 1) == '\n') {
                            effectBuilder.append(" ");
                        }
                        effectBuilder.append(trimmed).append("\n ");
                    }
                }
                else if (inCombatActions) combatActionsBuilder.append(trimmed).append("\n");
            }

            // Save the very last Gu in the file
            saveGu(currentGu, effectBuilder, descriptionBuilder, steedDoc, combatActionsBuilder, allGuEntries, idCounter);
            System.out.println("Gu Index successfully refined internally.");

            // Write all accumulated entries to a JSON file
            writeJsonFile(allGuEntries);

        } catch (IOException e) {
            System.err.println("Failed to read Gu Index file: " + e.getMessage());
        }
    }

    private List<String> parseEffectIntoArray(String rawEffect) {
        List<String> effectArray = new ArrayList<>();
        if (rawEffect == null || rawEffect.trim().isEmpty()) {
            return effectArray;
        }

        Pattern pattern = Pattern.compile("(<span>.*?</span>)");
        Matcher matcher = pattern.matcher(rawEffect);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String textBefore = rawEffect.substring(lastEnd, matcher.start());
                if (!textBefore.isEmpty()) effectArray.add(textBefore);
            }

            effectArray.add(matcher.group(1));

            lastEnd = matcher.end();
        }

        if (lastEnd < rawEffect.length()) {
            String remainingText = rawEffect.substring(lastEnd);
            if (!remainingText.trim().isEmpty()) {
                effectArray.add(remainingText);
            }
        }

        return effectArray;
    }

    private void saveGu(Map<String, Object> currentGu,
                        StringBuilder effectBuilder, StringBuilder descriptionBuilder,
                        Map<String, Object> steedDoc, StringBuilder combatActionsBuilder,
                        List<Map<String, Object>> allGuEntries, int id) {
        if (currentGu != null) {
            String effectStr = effectBuilder.toString().trim();
            
            // Apply table-safety to description appending as well
            if (descriptionBuilder.length() > 0) {
                String desc = descriptionBuilder.toString().trim();
                String separator = desc.startsWith("|") ? "\n" : "\n ";
                effectStr = effectStr + separator + desc;
            }

            currentGu.put("id", id);
            
            if (steedDoc != null) {
                if (combatActionsBuilder.length() > 0) {
                    steedDoc.put("CombatActions", combatActionsBuilder.toString().trim());
                }
                currentGu.put("Steed", steedDoc);
            }

            if (effectStr.length() > 0) {
                List<String> parsedEffects = parseEffectIntoArray(effectStr);
                currentGu.put("Effect", parsedEffects);
            } else {
                currentGu.put("Effect", new ArrayList<>());
            }

            Map<String, Object> copy = new LinkedHashMap<>(currentGu);
            copy.remove("_id");
            allGuEntries.add(copy);
        }
    }

    private void writeJsonFile(List<Map<String, Object>> entries) {
        try {
            Path outputPath = Paths.get(JSON_OUTPUT_PATH);
            Files.createDirectories(outputPath.getParent());

            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < entries.size(); i++) {
                sb.append("  ").append(toLowerCamelKeysJson(entries.get(i)));
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

    // Custom recursive JSON builder to replace Document.toJson() 
    // while perfectly preserving the camelCase conversion logic
    @SuppressWarnings("unchecked")
    private String toLowerCamelKeysJson(Map<String, Object> map) {
        StringBuilder result = new StringBuilder();
        result.append("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) result.append(", ");
            first = false;

            String key = entry.getKey();
            Object value = entry.getValue();
            String newKey = Character.toLowerCase(key.charAt(0)) + key.substring(1);

            result.append("\"").append(newKey).append("\": ");
            
            if (value == null) {
                result.append("null");
            } else if (value instanceof String) {
                String str = (String) value;
                str = str.replace("\\", "\\\\")
                         .replace("\"", "\\\"")
                         .replace("\n", "\\n")
                         .replace("\r", "\\r")
                         .replace("\t", "\\t");
                result.append("\"").append(str).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                result.append(value);
            } else if (value instanceof List) {
                result.append(listToJson((List<?>) value));
            } else if (value instanceof Map) {
                // Recursively handle nested Maps (e.g. Steed, Attributes, Skills)
                result.append(toLowerCamelKeysJson((Map<String, Object>) value));
            } else {
                result.append("\"").append(value.toString()).append("\"");
            }
        }
        result.append("}");
        return result.toString();
    }

    private String listToJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            Object val = list.get(i);
            if (val instanceof String) {
                String str = ((String) val).replace("\\", "\\\\")
                                           .replace("\"", "\\\"")
                                           .replace("\n", "\\n")
                                           .replace("\r", "\\r")
                                           .replace("\t", "\\t");
                sb.append("\"").append(str).append("\"");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapVal = (Map<String, Object>) val;
                sb.append(toLowerCamelKeysJson(mapVal));
            } else {
                sb.append("\"").append(val != null ? val.toString() : "null").append("\"");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}