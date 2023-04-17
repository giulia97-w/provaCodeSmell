package weka;





import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SpreadSubsample;
import weka.filters.supervised.instance.SMOTE;

public class weka{ 

	private static final Logger logger =  Logger.getLogger(weka.class.getName());
	
	public static void main(String[] args) throws Exception
	{
		String dataSetA = "/Users/giuliamenichini/eclipse-workspace/ISW2/openjpaDataset.arff";
		computeAccuracy(dataSetA,"OPENJPA");
		

	}

	//calcolo probabilità di train rispetto a train + test
	private static double  calculateTrainingTestingRatio(Instances train, Instances test) {
	    return (train.size()) / ((double) train.size() + (double) test.size());
	}

	private static int calculateModuleNumber(String projName) {
		logger.info("sono qui 11");

	    int mod = 1;
	    if (projName.equals("BOOKKEEPER"))
	        mod = 6;
	    if (projName.equals("OPENJPA"))
	        mod = 16;
	    return mod;
	}

	private static int calculateReleaseNumber( int version, int mod) {
		logger.info("sono qui 12");

	    return (version % mod) + 1;
	}

	private static double calculateBuggy(Instances instances) {
		logger.info("sono qui 13");

	    return numOfBuggy(instances);
	}

	public static Measure createMeasureObject(Instances train, Instances test, String[] s, String projName, int version) {
	    double trainingTestingRatio =  calculateTrainingTestingRatio(train, test);
	    int moduleNumber = calculateModuleNumber(projName);
	    int releaseNumber = calculateReleaseNumber(version, moduleNumber);
	    double  buggyPercentageInTrainingSet = calculateBuggy(train);
	    double buggyPercentageInTestingSet = calculateBuggy(test);
		logger.info("sono qui 1");

	    Measure m = new Measure(projName);
	    m.setReleaseNumber(releaseNumber);
	    m.setClassifier(s[0]);
	    m.setBalancingMethod(s[1]);
	    m.setFeatureSelectionMethod(s[2]);
	    m.setSensitivityMethod(s[3]);
	    m.setTrainingTestingRatio(trainingTestingRatio);
	    m.setBuggyPercentageInTestingSet(buggyPercentageInTestingSet);
	    m.setBuggyPercentageInTrainingSet( buggyPercentageInTrainingSet);
		logger.info("sono qui 2");

	    return m;
	}


	private static void evaluateAndAddMeasure(Evaluation e, Instances train, Instances test, String[] s, String projName, List<Measure> measures, int version) {
	    Measure measure = createMeasureObject(train, test, s, projName, version);
	    logger.info("sono qui 3");
	    measure.setAuc(e.areaUnderROC(1));
	    measure.setKappa(e.kappa());
	    measure.setRecall(e.recall(1));
	    measure.setPrecision(e.precision(1));
	    measure.setTrueNegatives((int) e.numTrueNegatives(1));
	    measure.setTruePositives((int) e.numTruePositives(1));
	    measure.setFalseNegatives((int) e.numFalseNegatives(1));
	    measure.setFalsePositives((int) e.numFalsePositives(1));
	    logger.info("sono qui 4");
	    measures.add(measure);
	}

	public static AttributeSelection createFeatureSelectionFilter() throws Exception {
		logger.info("sono qui 5");
	    AttributeSelection filter = new AttributeSelection();
	    filter.setEvaluator(new CfsSubsetEval());
	    filter.setSearch(new BestFirst());
	    logger.info("sono qui 6");
	    return filter;
	    
	}
	
	public void walkForward(String featureSelection, String balancing, String costEvaluation, String classifier, List<Instances> sets, List<Measure> measures, String projectName) throws Exception {
	    int version = 0;
	    logger.info("sono qui 7");
	    for (int i = 1; i < sets.size(); i++) {
	        Instances testSet = new Instances(sets.get(i));
	        Instances trainingSet = createTrainingSet(sets, i);
	        trainingSet.setClassIndex(trainingSet.numAttributes() - 1);
	        testSet.setClassIndex(testSet.numAttributes() - 1);
	        if ("BEST FIRST".equals(featureSelection)) {
	            List<Instances> filteredSets = featureSelection(trainingSet, testSet);
	            trainingSet = filteredSets.get(1);
	            testSet = filteredSets.get(0);
	        }
	        Classifier classifierInstance = generateClassifier(classifier);
	        if (!"NO".equals(balancing)) {
	            classifierInstance = balancing(classifierInstance, balancing, trainingSet);
	        }
	        Evaluation evaluation = evaluateModel(costEvaluation, classifierInstance, trainingSet, testSet);
	        String[] labels = {classifier, balancing, featureSelection, costEvaluation};
	        if (evaluation == null) {
	            logger.log(Level.INFO, "Errore valutando il modello");
	        } else {
	        	createMeasureObject(trainingSet, testSet, labels, projectName, version);
	            evaluateAndAddMeasure(evaluation, trainingSet, testSet, labels, projectName, measures, version);
	            version++;
	            logger.info("sono qui 8");
	        }
	    }
	}


	private Instances createTrainingSet(List<Instances> sets, int endIndex) {
	    Instances train = new Instances(sets.get(0));
	    logger.info("sono qui 9");
	    for (int i = 1; i < endIndex; i++) {
	        train.addAll(sets.get(i));
	    }
	    logger.info("sono qui 10");
	    return train;
	}


	public static List<Instances> applyFeatureSelection(AttributeSelection filter, Instances train, Instances test) throws Exception {
		logger.info("sono qui 14");
		Instances trainFiltered = new Instances(train);
	    Instances testFiltered = new Instances(test);
	    filter.setInputFormat(train);
	    trainFiltered = Filter.useFilter(trainFiltered, filter);
	    trainFiltered.setClassIndex(trainFiltered.numAttributes() - 1);
	    testFiltered = Filter.useFilter(testFiltered, filter);
	    testFiltered.setClassIndex(testFiltered.numAttributes() - 1);
	    return Arrays.asList(testFiltered, trainFiltered);
	}
	
	public static List<Instances> featureSelection(Instances train, Instances test) throws Exception {
		logger.info("sono qui 15");
	    AttributeSelection filter = createFeatureSelectionFilter();
	    return applyFeatureSelection(filter, train, test);
	}



	
	private static Filter getBalancingFilter(String balancing, Instances train) throws Exception {
	    Filter filter = null;
	    logger.info("sono qui 16");
	    if ("OVERSAMPLING".equals(balancing)) {
	        filter = getResampleFilter(train, false, 0.1, 2 * getSampleSizePerc(train), new String[]{"-B", "1.0", "-Z", "130.3"});
	    } else if ("UNDERSAMPLING".equals(balancing)) {
	        filter = getSpreadSubsampleFilter(train, new String[]{"-M", "1.0"});
	    } else if ("SMOTE".equals(balancing)) {
	        filter = getSMOTEFilter(train);
	    }

	    if (filter != null) {
	        filter.setInputFormat(train);
	    }

	    return filter;
	}

	private static Resample getResampleFilter(Instances data, boolean noReplacement, double biasToUniformClass, double sampleSizePercent, String[] options) throws Exception {
		logger.info("sono qui 17");
		Resample resample = new Resample();
	    resample.setNoReplacement(noReplacement);
	    resample.setBiasToUniformClass(biasToUniformClass);
	    resample.setSampleSizePercent(sampleSizePercent);
	    resample.setOptions(options);
	    resample.setInputFormat(data);
	    return resample;
	}

	private static SpreadSubsample getSpreadSubsampleFilter(Instances data, String[] options) throws Exception {
		logger.info("sono qui 18");
		SpreadSubsample spreadSubsample = new SpreadSubsample();
	    spreadSubsample.setOptions(options);
	    spreadSubsample.setInputFormat(data);
	    return spreadSubsample;
	}

	private static SMOTE getSMOTEFilter(Instances data) throws Exception {
		logger.info("sono qui 19");
		SMOTE smote = new SMOTE();
	    smote.setInputFormat(data);
	    return smote;
	}


	private static FilteredClassifier createFilteredClassifier(Classifier c, Filter filter) {
		logger.info("sono qui 20");
		FilteredClassifier fc = new FilteredClassifier();
	    fc.setClassifier(c);
	    if (filter != null) {
	        fc.setFilter(filter);
	    }
	    return fc;
	}

	private static FilteredClassifier balancing(Classifier c, String balancing, Instances train) throws Exception {
		logger.info("sono qui 21");
		Filter filter = getBalancingFilter(balancing, train);
	    return createFilteredClassifier(c, filter);
	}

	
	
	private static Evaluation evaluateModel(String costEvaluation, Classifier c, Instances train, Instances test) {
		logger.info("sono qui 22");
		Evaluation ev = null;
	    if (!costEvaluation.equals("NO")) {
	        CostSensitiveClassifier c1 = new CostSensitiveClassifier();
	        if (costEvaluation.equals("SENSITIVE THRESHOLD")) {
	            c1.setMinimizeExpectedCost(true);
	        } else if (costEvaluation.equals("SENSITIVE LEARNING")) {
	            c1.setMinimizeExpectedCost(false);
	            train = reweight(train);
	        }
	        c1.setClassifier(c);
	        c1.setCostMatrix(createCostMatrix(10.0, 1.0));
	        try {
	            c1.buildClassifier(train);
	            ev = new Evaluation(test, c1.getCostMatrix());
	            ev.evaluateModel(c1, test);
	        } catch (Exception e) {
	            logger.log(Level.INFO, "Errore sensitiveness");
	        }
	    } else {
	        try {
	            c.buildClassifier(train);
	            ev = new Evaluation(test);
	            ev.evaluateModel(c, test);
	        } catch (Exception e) {
	            logger.log(Level.INFO, "Errore durante la valutazione del modello");
	        }
	    }
	    return ev;
	}


	private static double getSampleSizePerc(Instances train) {
		logger.info("sono qui 23");
		double numOfBuggy = numOfBuggy(train);
	    double nonnumOfBuggy = 1 - numOfBuggy;
	    double majorityPercentage = Math.max(numOfBuggy, nonnumOfBuggy);
	    double minorityPercentage = Math.min(numOfBuggy, nonnumOfBuggy);
	    double sampleSizePerc = majorityPercentage * 100;
	    if (majorityPercentage == nonnumOfBuggy) {
	        sampleSizePerc = minorityPercentage * 100;
	    }
	    return sampleSizePerc;
	}

	
	private static double numOfBuggy(Instances train) {
		logger.info("sono qui 24");
		int numBuggy = (int) train.stream()
	            .filter(instance -> instance.stringValue(instance.classAttribute()).equals("true"))
	            .count();
	    return (double) numBuggy / train.size();
	}

	private static CostMatrix createCostMatrix(double weightFalsePositive, double weightFalseNegative) {
		logger.info("sono qui 25");
		CostMatrix costMatrix = new CostMatrix(2); 
	    costMatrix.setCell(0, 0, 0.0); 
	    costMatrix.setCell(1, 0, weightFalsePositive); 
	    costMatrix.setCell(0, 1, weightFalseNegative); 
	    costMatrix.setCell(1, 1, 0.0); 
	    return costMatrix;
	}


	private static Instances reweight(Instances train) {
		logger.info("sono qui 26");
		Instances train2 = new Instances(train);

	    double numNo = train.numInstances() - buggyCount(train);
	    double numYes = buggyCount(train);

	    if (numNo > numYes) {
	        int oversampleRatio = calculateOversampleRatio(numNo, numYes);
	        train2 = oversampleBuggyInstances(train, oversampleRatio);
	    }

	    return train2;
	}

	private static int buggyCount(Instances data) {
		logger.info("sono qui 27");
		int count = 0;
	    for (int i = 0; i < data.numInstances(); i++) {
	        Instance instance = data.instance(i);
	        if (instance.stringValue(instance.classIndex()).equals("true")) {
	            count++;
	        }
	    }
	    return count;
	}

	private static int calculateOversampleRatio(double numNo, double numYes) {
		logger.info("sono qui 28");
		return (int) Math.ceil(numNo / numYes);
	}

	private static Instances oversampleBuggyInstances(Instances data, int oversampleRatio) {
		logger.info("sono qui 29");
		Instances oversampledData = new Instances(data);
	    for (int i = 0; i < data.numInstances(); i++) {
	        Instance instance = data.instance(i);
	        if (instance.stringValue(instance.classIndex()).equals("Yes")) {
	            for (int j = 0; j < oversampleRatio - 1; j++) {
	                oversampledData.add(instance);
	            }
	        }
	    }
	    return oversampledData;
	}


	//Metodo di controllo dell'applicativo
	public static void computeAccuracy(String datasetPath, String projName) throws Exception {
		logger.info("sono qui 30");
		logger.info("Creando il file di output");
		ArrayList<Instances> sets = getSets(datasetPath);
		List<String> featureSelection = Arrays.asList("NO", "BEST FIRST");
		List<String> balancing = Arrays.asList("NO", "UNDERSAMPLING", "OVERSAMPLING", "SMOTE");
		List<String> costEvaluation = Arrays.asList("NO", "SENSITIVE THRESHOLD", "SENSITIVE LEARNING");
		List<String> classifiers = Arrays.asList("RANDOM FOREST", "NAIVE BAYES", "IBK");
		ArrayList<Measure> measures = new ArrayList<>();
		weka v = new weka();
		
		for (int i = 0; i < featureSelection.size(); i++) {
		    String f = featureSelection.get(i);
		    for (int j = 0; j < balancing.size(); j++) {
		        String b = balancing.get(j);
		        for (int k = 0; k < costEvaluation.size(); k++) {
		            String e = costEvaluation.get(k);
		            for (int l = 0; l < classifiers.size(); l++) {
		                String c = classifiers.get(l);
		                v.walkForward(f, b, e, c, sets, measures, projName);
		            }
		        }
		    }
		}
		printMeasures(projName, measures);
		logger.info("File creato!");
	}
	
	//Ottengo una lista dove ogni elemento è l'insieme delle istanze divise per versione
	private static ArrayList<Instances> getSets(String datasetPath) {
		logger.info("sono qui 31");
		Instances data = readData(datasetPath);
	    ArrayList<Instances> sets = extractVersions(data);
	    return sets;
	}

	private static Instances readData(String datasetPath) {
		logger.info("sono qui 32");
		Instances data = null;
	    try {
	        data = DataSource.read(datasetPath);
	        data.sort(0);
	    } catch (Exception e) {
	        logger.log(Level.INFO, "An error has occurred acquiring the dataset.");
	    }
	    return data;
	}

	private static ArrayList<Instances> extractVersions(Instances data) {
		logger.info("sono qui 33");
		ArrayList<Instances> sets = new ArrayList<>();
	    int numVersions = (int) data.lastInstance().value(0);
	    for (int v = 1; v <= numVersions; v++) {
	        Instances versionInstances = extractInstancesForVersion(data, v);
	        sets.add(versionInstances);
	    }
	    return sets;
	}

	private static Instances extractInstancesForVersion(Instances data, int version) {
		logger.info("sono qui 34");
		Instances versionInstances = new Instances(data, 0);
	    for (int i = 0; i < data.numInstances(); i++) {
	        Instance instance = data.instance(i);
	        if ((int) instance.value(0) == version) {
	            versionInstances.add(instance);
	        }
	    }
	    return versionInstances;
	}


	
	public static void printMeasures(String project, List<Measure> measures) {
		logger.info("sono qui 35");
		String outName = project + "Output.csv";
	    
	    try (FileWriter fileWriter = new FileWriter(outName)) {
	        fileWriter.append("Dataset,#TrainingRelease,%Training,%Defective in training,%Defective in testing,Classifier,Balancing,Feature Selection,Sensitivity,TP,FP,TN,FN,Precision,Recall,AUC,Kappa");
	        fileWriter.append("\n");
	        for (Measure measure : measures) {
	            writeMeasure(fileWriter, measure);
	        }
	    } catch (IOException e) {
	        logger.log(Level.INFO, "Errore durante la scrittura del csv.");
	    }
	}

	private static void writeMeasure(FileWriter fileWriter, Measure measure) throws IOException {
		logger.info("sono qui 36");
	    fileWriter.append(measure.getDataset());
	    fileWriter.append(",");
	    fileWriter.append(measure.getRelease().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getTrainPercentage().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getDefectInTrainPercentage().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getDefectInTestPercentage().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getClassifier());
	    fileWriter.append(",");
	    fileWriter.append(measure.getBalancing());
	    fileWriter.append(",");
	    fileWriter.append(measure.getFeatureSelection());
	    fileWriter.append(",");
	    fileWriter.append(measure.getSensitivity());
	    fileWriter.append(",");
	    fileWriter.append(measure.getTp().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getFp().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getTn().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getFn().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getPrecision().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getRecall().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getAuc().toString());
	    fileWriter.append(",");
	    fileWriter.append(measure.getKappa().toString());
	    fileWriter.append("\n");
		

	}

		
	public static Classifier generateClassifier(String type) {
		logger.info("sono qui 37");
		Map<String, Supplier<Classifier>> classifierMap = new HashMap<>();
	    classifierMap.put("RANDOM FOREST", RandomForest::new);
	    classifierMap.put("NAIVE BAYES", NaiveBayes::new);
	    classifierMap.put("IBK", IBk::new);

	    return classifierMap.getOrDefault(type, () -> null).get();
	}


}