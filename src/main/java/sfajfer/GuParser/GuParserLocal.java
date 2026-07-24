package sfajfer.GuParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private static final String JSON_OUTPUT_PATH = "../Gu-SRD/src/assets/gu-index.json";
    private static final String BEAST_OUTPUT_PATH = "../Gu-SRD/src/assets/beast-index.json";

    public static void main(String[] args) {
        GuParserLocal parser = new GuParserLocal();
        parser.parseAndPopulate("Gu Index.md");
        parser.parseBeast("Beast Index.md");
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
                    descriptionBuilder.append("\n " + trimmed);
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
                else if (trimmed.startsWith("Previous Rank:")) currentGu.put("PreviousRank", trimmed.substring(14).trim());
                else if (trimmed.startsWith("Next Rank:")) currentGu.put("NextRank", trimmed.substring(10).trim());
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

    private static final Pattern OVERRIDE_TIER_PATTERN =
            Pattern.compile("^(Hundred|Thousand|Myriad|Emperor):\\s*\\{(.*)$");
    private static final Pattern OVERRIDE_ENTRY_PATTERN =
            Pattern.compile("([A-Za-z][A-Za-z ]*?):\\s*[\"\u201C]([^\"\u201D]*)[\"\u201D]");

    public void parseBeast(String fileName) {
        int idCounter = 0;

        System.out.println("Starting Beast Index refinement process using file: " + fileName);

        // Accumulate every parsed Beast so we can write them all to JSON at the end
        List<Map<String, Object>> allBeastEntries = new ArrayList<>();

        File file = new File(fileName);
        if (!file.exists()) {
            System.err.println("FATAL ERROR: Could not find " + fileName + " in the current directory.");
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;

            Map<String, Object> currentBeast = null;
            Map<String, Object> primaryAttributes = null;
            Map<String, Object> secondaryAttributes = null;
            Map<String, Object> hordeRules = null;
            Map<String, Object> overrides = null;
            List<String> features = null;
            List<String> combatActions = null;

            // Description, PrimaryAttributes, SecondaryAttributes, Features, CombatActions, HordeRules, Override
            String currentSection = null;

            boolean inOverrideBlock = false;
            String overrideTier = null;
            StringBuilder overrideBlockBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.isEmpty()) continue;

                // Section markers: "<!-- Description -->", "<!-- Features ->", etc.
                if (trimmed.startsWith("<!--")) {
                    String marker = trimmed.replace("-->", "").replace("<!--", "").replace("\u2192", "").trim();
                    if (marker.equalsIgnoreCase("Description")) currentSection = "Description";
                    else if (marker.equalsIgnoreCase("Primary Attributes")) currentSection = "PrimaryAttributes";
                    else if (marker.equalsIgnoreCase("Secondary Attributes")) currentSection = "SecondaryAttributes";
                    else if (marker.equalsIgnoreCase("Features")) currentSection = "Features";
                    else if (marker.equalsIgnoreCase("Combat Actions")) currentSection = "CombatActions";
                    else if (marker.equalsIgnoreCase("Horde Rules")) currentSection = "HordeRules";
                    else if (marker.equalsIgnoreCase("Override")) currentSection = "Override";
                    continue;
                }

                if (trimmed.startsWith("### ")) {
                    saveBeast(currentBeast, primaryAttributes, secondaryAttributes, features,
                            combatActions, hordeRules, overrides, allBeastEntries, idCounter);
                    idCounter++;

                    currentBeast = new LinkedHashMap<>();
                    currentBeast.put("Name", trimmed.substring(4).trim());

                    primaryAttributes = new LinkedHashMap<>();
                    secondaryAttributes = new LinkedHashMap<>();
                    hordeRules = new LinkedHashMap<>();
                    overrides = new LinkedHashMap<>();
                    features = new ArrayList<>();
                    combatActions = new ArrayList<>();

                    currentSection = null;
                    inOverrideBlock = false;
                    overrideTier = null;
                    overrideBlockBuilder = new StringBuilder();
                    continue;
                }

                if (currentBeast == null) continue;

                // Override blocks can span multiple lines: "Hundred: { ... }"
                if ("Override".equals(currentSection)) {
                    if (inOverrideBlock) {
                        if (trimmed.contains("}")) {
                            overrideBlockBuilder.append(" ").append(trimmed.substring(0, trimmed.indexOf('}')));
                            overrides.put(overrideTier, parseOverrideBlock(overrideBlockBuilder.toString()));
                            inOverrideBlock = false;
                            overrideTier = null;
                            overrideBlockBuilder = new StringBuilder();
                        } else {
                            overrideBlockBuilder.append(" ").append(trimmed);
                        }
                        continue;
                    }

                    Matcher tierMatcher = OVERRIDE_TIER_PATTERN.matcher(trimmed);
                    if (tierMatcher.find()) {
                        String tier = tierMatcher.group(1);
                        String rest = tierMatcher.group(2);
                        if (rest.contains("}")) {
                            overrides.put(tier, parseOverrideBlock(rest.substring(0, rest.indexOf('}'))));
                        } else {
                            overrideTier = tier;
                            inOverrideBlock = true;
                            overrideBlockBuilder = new StringBuilder(rest);
                        }
                    }
                    continue;
                }

                if (currentSection == null) continue;

                switch (currentSection) {
                    case "Description":
                        if (trimmed.startsWith("Description:")) {
                            currentBeast.put("Description", trimmed.substring("Description:".length()).trim());
                        }
                        break;

                    case "PrimaryAttributes":
                        putKeyValue(primaryAttributes, trimmed);
                        break;

                    case "SecondaryAttributes":
                        putKeyValue(secondaryAttributes, trimmed);
                        break;

                    case "Features":
                        if (trimmed.startsWith("Feature:")) {
                            String content = trimmed.substring("Feature:".length()).trim();
                            if (!content.isEmpty()) features.add(content);
                        }
                        break;

                    case "CombatActions":
                        if (trimmed.startsWith("Action:")) {
                            String content = trimmed.substring("Action:".length()).trim();
                            if (!content.isEmpty()) combatActions.add(content);
                        }
                        break;

                    case "HordeRules":
                        if (trimmed.startsWith("Grade:")) {
                            hordeRules.put("Grade", trimmed.substring("Grade:".length()).trim());
                        } else if (trimmed.startsWith("Upkeep:")) {
                            String raw = trimmed.substring("Upkeep:".length()).trim();
                            try { hordeRules.put("Upkeep", Integer.parseInt(raw.replace(",", ""))); }
                            catch (NumberFormatException e) { hordeRules.put("Upkeep", raw); }
                        } else if (trimmed.startsWith("Primary Biomes:")) {
                            hordeRules.put("PrimaryBiomes", splitToList(trimmed.substring("Primary Biomes:".length())));
                        } else if (trimmed.startsWith("Secondary Biomes:")) {
                            hordeRules.put("SecondaryBiomes", splitToList(trimmed.substring("Secondary Biomes:".length())));
                        }
                        break;

                    default:
                        break;
                }
            }

            // Save the very last Beast in the file
            saveBeast(currentBeast, primaryAttributes, secondaryAttributes, features,
                    combatActions, hordeRules, overrides, allBeastEntries, idCounter);
            System.out.println("Beast Index successfully refined internally.");

            // Write all accumulated entries to a JSON file
            writeJsonFile(allBeastEntries, BEAST_OUTPUT_PATH);

        } catch (IOException e) {
            System.err.println("Failed to read Beast Index file: " + e.getMessage());
        }
    }

    private void putKeyValue(Map<String, Object> map, String line) {
        int idx = line.indexOf(':');
        if (idx < 0) return;
        String key = line.substring(0, idx).trim();
        String value = line.substring(idx + 1).trim();
        if (value.isEmpty()) return;
        try {
            map.put(key, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            map.put(key, value);
        }
    }

    private List<String> splitToList(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null) return list;
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) list.add(p);
        }
        return list;
    }

    // Parses the content of a single override tier block, e.g.
    //   Feature: "Jade Eyes - The Jade Stone Monkey can see perfectly ..."
    // into { "Jade Eyes": "Jade Eyes - The Jade Stone Monkey can see perfectly ..." }
    private Map<String, Object> parseOverrideBlock(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content == null) return result;
        String cleaned = content.trim();
        if (cleaned.isEmpty()) return result;

        Matcher m = OVERRIDE_ENTRY_PATTERN.matcher(cleaned);
        boolean found = false;
        while (m.find()) {
            found = true;
            String fieldType = m.group(1).trim();
            String value = m.group(2).trim();

            // Feature/Action overrides are written as "Name - Description";
            // use the name as the override key so it maps onto the original entry.
            int dashIdx = value.indexOf(" - ");
            if (dashIdx > 0) {
                result.put(value.substring(0, dashIdx).trim(), value);
            } else {
                result.put(fieldType, value);
            }
        }

        if (!found) {
            // Fallback for unquoted "Key - Value" pairs
            for (String part : cleaned.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                int dashIdx = p.indexOf('-');
                if (dashIdx > 0) {
                    result.put(p.substring(0, dashIdx).trim(), p.substring(dashIdx + 1).trim());
                }
            }
        }

        return result;
    }

    private void saveBeast(Map<String, Object> currentBeast,
                            Map<String, Object> primaryAttributes,
                            Map<String, Object> secondaryAttributes,
                            List<String> features,
                            List<String> combatActions,
                            Map<String, Object> hordeRules,
                            Map<String, Object> overrides,
                            List<Map<String, Object>> allBeastEntries,
                            int id) {
        if (currentBeast == null) return;

        currentBeast.put("id", id);

        if (primaryAttributes != null && !primaryAttributes.isEmpty()) {
            currentBeast.put("PrimaryAttributes", primaryAttributes);
        }
        if (secondaryAttributes != null && !secondaryAttributes.isEmpty()) {
            currentBeast.put("SecondaryAttributes", secondaryAttributes);
        }
        if (features != null) currentBeast.put("Features", features);
        if (combatActions != null) currentBeast.put("CombatActions", combatActions);
        if (hordeRules != null && !hordeRules.isEmpty()) {
            currentBeast.put("HordeRules", hordeRules);
        }
        if (overrides != null && !overrides.isEmpty()) {
            currentBeast.put("Override", overrides);
        }

        allBeastEntries.add(currentBeast);
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

            currentGu.put("id", id);
            
            if (steedDoc != null) {
                if (combatActionsBuilder.length() > 0) {
                    steedDoc.put("CombatActions", combatActionsBuilder.toString().trim());
                }
                currentGu.put("Steed", steedDoc);
            }

            // Parse effect into array and add description as a separate item if present
            List<String> effectItems = new ArrayList<>();
            if (effectStr.length() > 0) {
                effectItems.addAll(parseEffectIntoArray(effectStr));
            }
            
            currentGu.put("Effect", effectItems);
            
            if (descriptionBuilder.length() > 0) {
                currentGu.put("Description", descriptionBuilder.toString().trim());
            }

            Map<String, Object> copy = new LinkedHashMap<>(currentGu);
            copy.remove("_id");
            allGuEntries.add(copy);
        }
    }

    private void writeJsonFile(List<Map<String, Object>> entries) {
        writeJsonFile(entries, JSON_OUTPUT_PATH);
    }

    private void writeJsonFile(List<Map<String, Object>> entries, String outputPathStr) {
        try {
            Path outputPath = Paths.get(outputPathStr);
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