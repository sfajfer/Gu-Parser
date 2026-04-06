package sfajfer.GuParser;

import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "GuIndex")
public class Gu {
    @Id
    private String id;

    @Field("Path")
    private String path;

    @Field("Name")
    private String name;

    @Field("Rank")
    private List<Integer> rank;

    @Field("Type")
    private String type;

    @Field("Cost")
    private String cost;

    @Field("Range")
    private String range;

    @Field("Health")
    private Integer health;

    @Field("Food")
    private String food;

    @Field("Keywords")
    private List<String> keywords;

    @Field("Effect")
    private String effect;

    @Field("Steed")
    private SteedStats steed; // Nested object for the LaTeX table data

// --- GETTERS AND SETTERS ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Integer> getRank() { return rank; }
    public void setRank(List<Integer> rank) { this.rank = rank; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCost() { return cost; }
    public void setCost(String cost) { this.cost = cost; }

    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }

    public Integer getHealth() { return health; }
    public void setHealth(Integer health) { this.health = health; }

    public String getFood() { return food; }
    public void setFood(String food) { this.food = food; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }

    public SteedStats getSteed() { return steed; }
    public void setSteed(SteedStats steed) { this.steed = steed; }
}

class SteedStats {
    private Integer cr;
    private Map<String, String> attributes;
    private Map<String, String> skills;
    private String combatActions;

    public Integer getCr() { return cr; }
    public void setCr(Integer cr) { this.cr = cr; }

    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }

    public Map<String, String> getSkills() { return skills; }
    public void setSkills(Map<String, String> skills) { this.skills = skills; }

    public String getCombatActions() { return combatActions; }
    public void setCombatActions(String combatActions) { this.combatActions = combatActions; }
}
