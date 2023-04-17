package weka;



public class Measure 
{
	private final String dataset;
	private String classifier;
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
	private Double trainPercentage;
	private Double defectiveInTrainingPercentage;
	private Double defectiveInTestingPercentage;
	
	
	
	//get
	public Measure(String dataset)
	{
		this.dataset = dataset;
	}
	public String getDataset()
	{
		return this.dataset;
	}
	public String getClassifier()
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
	public Double getTrainPercentage()
	{
		return this.trainPercentage;
	}
	public Double getDefectInTrainPercentage()
	{
		return this.defectiveInTrainingPercentage;
	}
	public Double getKappa()
	{
		return this.kappa;
	}
	public Double getDefectInTestPercentage()
	{
		return this.defectiveInTestingPercentage;
	}
	public Double getAuc()
	{
		return this.auc;
	}
	
	//set
	
	
	public void setClassifier(String classifier)
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
	
	public void setTrainingTestingRatio(Double p)
	{
		this.trainPercentage = p;
	}
	
	public void setBuggyPercentageInTestingSet(Double p)
	{
		this.defectiveInTrainingPercentage = p;
	}
	
	public void setBuggyPercentageInTrainingSet(Double p)
	{
		this.defectiveInTestingPercentage = p;
	}
}