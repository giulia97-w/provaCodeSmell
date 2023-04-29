package weka;

import weka.core.Instances;

public class EvaluationInfo {
	private Instances trainData;
	private Instances testData;
	private String[] metrics;
	private int version;

	public EvaluationInfo(Instances trainData, Instances testData, String[] metrics, int version) {
	    this.trainData = trainData;
	    this.testData = testData;
	    this.metrics = metrics;
	    this.version = version;
	}

	public Instances getTrainData() {
	    return trainData;
	}

	public Instances getTestData() {
	    return testData;
	}

	public String[] getMetrics() {
	    return metrics;
	}

	public int getVersion() {
	    return version;
	}


}
