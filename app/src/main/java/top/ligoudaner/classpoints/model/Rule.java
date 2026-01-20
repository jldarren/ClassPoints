package top.ligoudaner.classpoints.model;

public class Rule {
    public String category;
    public String description;
    public double score;

    public Rule(String category, String description, double score) {
        this.category = category;
        this.description = description;
        this.score = score;
    }

    @Override
    public String toString() {
        return description + " (" + (score > 0 ? "+" : "") + score + ")";
    }
}
