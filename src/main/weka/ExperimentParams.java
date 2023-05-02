package weka;

public class ExperimentParams {
    private String featureSelection;
    private String balancing;
    private String costEvaluation;
    private String classifier;
    

    public ExperimentParams(String featureSelection, String balancing, String costEvaluation, String classifier) {
        this.featureSelection = featureSelection;
        this.balancing = balancing;
        this.costEvaluation = costEvaluation;
        this.classifier = classifier;
    }

    public String getFeatureSelection() {
        return featureSelection;
    }

    public String getBalancing() {
        return balancing;
    }

    public String getCostEvaluation() {
        return costEvaluation;
    }

    public String getClassifier() {
        return classifier;
    }
}

