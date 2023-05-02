package weka;



public class ClassifierInfo 
{
	private final String datasetInfo;
	private String classifier;
	private Double numberTrain;
	private Double buggyInTrain;
	private Double buggyInTest;

	private String balancing;
	private String featureSelection;
	private String sensitivity;
	private Integer fp;
	private Integer tp;
	private Integer fn;
	private Integer tn;
	private Integer release;
	private Double precision;
	private Double recall;
	private Double auc;
	private Double kappa;
	



	//get
	public ClassifierInfo(String dataset)
	{
		this.datasetInfo = dataset;
	}
	public String getDatasetInfo()
	{
		return this.datasetInfo;
	}
	public String getClassifierType()
	{
		return this.classifier;
	}
	public String getBalancing()
	{
		return this.balancing;
	}
	public String getFeatureSelection()
	{
		return this.featureSelection;
	}
	public String getSensitivity()
	{
		return this.sensitivity;
	}
	public Integer getFp()
	{
		return this.fp;
	}
	public Integer getTn()
	{
		return this.tn;
	}
	public Integer getFn()
	{
		return this.fn;
	}
	public Double getRecall()
	{
		return this.recall;
	}
	public Double getPrecision()
	{
		return this.precision;
	}
	public Double getNumberTrain()
	{
		return this.numberTrain;
	}
	public Double getBuggyInTrain()
	{
		return this.buggyInTrain;
	}
	public Double getKappa()
	{
		return this.kappa;
	}
	public Double getBuggyInTest()
	{
		return this.buggyInTest;
	}
	public Double getAuc()
	{
		return this.auc;
	}

	//set


	public void setClassifierType(String classifier)
	{
		this.classifier = classifier;
	}

	public void setBalancingMethod(String balancing)
	{
		this.balancing = balancing;
	}

	public void setFeatureSelectionMethod(String f)
	{
		this.featureSelection = f;
	}

	public void setSensitivityMethod(String s)
	{
		this.sensitivity = s;
	}
	public Integer getRelease()
	{
		return this.release;
	}
	public void setReleaseNumber(Integer release)
	{
		this.release = release;
	}
	public Integer getTp()
	{
		return this.tp;
	}
	public void setTruePositives(Integer t)
	{
		this.tp = t;
	}

	public void setFalsePositives(Integer f)
	{
		this.fp = f;
	}

	public void setTrueNegatives(Integer t)
	{
		this.tn = t;
	}

	public void setFalseNegatives(Integer f)
	{
		this.fn = f;
	}

	public void setRecall(Double recall)
	{
		this.recall = recall;
	}

	public void setPrecision(Double precision)
	{
		this.precision = precision;
	}

	public void setKappa(Double kappa)
	{
		this.kappa = kappa;
	}

	public void setAuc(Double auc)
	{
		this.auc = auc;
	}

	public void setNumberTrainTest(Double numberTrain)
	{
		this.numberTrain = numberTrain;
	}

	public void setBuggyInTest(Double buggyInTest)
	{
		this.buggyInTest = buggyInTest;
	}

	public void setBuggyInTrain(Double buggyInTrain)
	{
		this.buggyInTrain = buggyInTrain;
	}
}