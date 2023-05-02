package weka;



public class ClassifierInfo 
{
	private final String datasetInfo;
	private Double numberTrain;
	private Double numberBuggyTrain;
	private Double numberBuggyTest;
	private String classifierType;
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
	public String getclassifierType()
	{
		return this.classifierType;
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
	public Double getnumberTrain()
	{
		return this.numberTrain;
	}
	public Double getBuggyTrain()
	{
		return this.numberBuggyTrain;
	}
	public Double getKappa()
	{
		return this.kappa;
	}
	public Double getBuggyTest()
	{
		return this.numberBuggyTest;
	}
	public Double getAuc()
	{
		return this.auc;
	}
	
	//set
	
	
	public void setclassifierType(String classifierType)
	{
		this.classifierType = classifierType;
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
	
	public void setNumberTrainTest(Double p)
	{
		this.numberTrain = p;
	}
	
	public void setNumberBuggyTest(Double numberBuggyTest)
	{
		this.numberBuggyTest = numberBuggyTest;
	}
	
	public void setNumberBuggyTrain(Double numberBuggyTrain)
	{
		this.numberBuggyTrain = numberBuggyTrain;
	}
}