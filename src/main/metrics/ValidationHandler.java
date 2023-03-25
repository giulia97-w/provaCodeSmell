package logic;



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

public class ValidationHandler 
{
	private static final Logger logger =  Logger.getLogger(ValidationHandler.class.getName());
	
	//Walk forward: ogni volta prendo incrementalmente il test set e i precedenti come train set iterando le istanze
	public static void main(String[] args) throws Exception
	{
		String dataSetA = "/Users/giuliamenichini/eclipse-workspace/weka/BOOKKEEPERDatasetInfo.arff";
		computeAccuracy(dataSetA,"BOOKKEEPER");
	}

	//Preparo la misura e la aggiungo alle altre
	private static double calculateRatio(Instances train, Instances test) {
	    return (train.size()) / ((double) train.size() + (double) test.size());
	}

	private static int calculateMod(String projName) {
	    int mod = 1;
	    if (projName.equals("BOOKKEEPER"))
	        mod = 6;
	    if (projName.equals("AVRO"))
	        mod = 16;
	    return mod;
	}

	private static int calculateRelease(String projName, int version, int mod) {
	    return (version % mod) + 1;
	}

	private static double calculateDefectPercentage(Instances instances) {
	    return buggyPercentage(instances);
	}

	private static Measure createMeasure(String classifier, String balancing, String featureSelection, String sensitivity, int release, double trainPercentage, double defectInTestPercentage, double defectInTrainPercentage, String projName) {
	    Measure m = new Measure(projName);
	    m.setRelease(release);
	    m.setClassifier(classifier);
	    m.setBalancing(balancing);
	    m.setFeatureSelection(featureSelection);
	    m.setSensitivity(sensitivity);
	    m.setTrainPercentage(trainPercentage);
	    m.setDefectInTestPercentage(defectInTestPercentage);
	    m.setDefectInTrainPercentage(defectInTrainPercentage);
	    return m;
	}

	public static Measure createMeasure(Instances train, Instances test, String[] s, String projName, int version) {
	    double ratio = calculateRatio(train, test);
	    int mod = calculateMod(projName);
	    int release = calculateRelease(projName, version, mod);
	    double defectInTrainPercentage = calculateDefectPercentage(train);
	    double defectInTestPercentage = calculateDefectPercentage(test);
	    return createMeasure(s[0], s[1], s[2], s[3], release, ratio, defectInTestPercentage, defectInTrainPercentage, projName);
	}

	
	
	

	private static void evaluateAndAddMeasure(Evaluation eval, Instances train, Instances test, String[] s, String projName, List<Measure> measures, int version) {
	    Measure m = createMeasure(train, test, s, projName, version);

	    m.setAuc(eval.areaUnderROC(1));
	    m.setKappa(eval.kappa());
	    m.setRecall(eval.recall(1));
	    m.setPrecision(eval.precision(1));
	    m.setTn((int) eval.numTrueNegatives(1));
	    m.setTp((int) eval.numTruePositives(1));
	    m.setFn((int) eval.numFalseNegatives(1));
	    m.setFp((int) eval.numFalsePositives(1));

	    measures.add(m);
	}

	public static AttributeSelection createFeatureSelectionFilter() throws Exception {
	    AttributeSelection filter = new AttributeSelection();
	    filter.setEvaluator(new CfsSubsetEval());
	    filter.setSearch(new BestFirst());
	    return filter;
	}
	
	public void walkForward(String featureSelection, String balancing, String costEvaluation, String classifier, List<Instances> sets, List<Measure> m, String projName) throws Exception {
	    int version = 0;

	    for (int i = 1; i < sets.size(); i++) {
	        Instances test = new Instances(sets.get(i));
	        Instances train = createTrainingSet(sets, i);

	        train.setClassIndex(train.numAttributes() - 1);
	        test.setClassIndex(test.numAttributes() - 1);

	        if (featureSelection.equals("BEST FIRST")) {
	            List<Instances> filteredSets = featureSelection(train, test);
	            train = filteredSets.get(1);
	            test = filteredSets.get(0);
	        }

	        Classifier c =generateClassifier(classifier);

	        if (!balancing.equals("NO")) {
	            c = balancing(c, balancing, train, test.size() + train.size());
	        }

	        Evaluation eval = costSensitiveness(costEvaluation, c, train, test);

	        String[] s = {classifier, balancing, featureSelection, costEvaluation};

	        if (eval == null) {
	            try {
	                eval = new Evaluation(test);
	            } catch (Exception e) {
	                logger.log(Level.INFO, "An error has occurred evaluating a model.");
	            }
	        }

	        if (eval != null) {
	            createMeasure(train, test, s, projName, version);
	            evaluateAndAddMeasure(eval, train, test, s, projName, m, version);
	            version++;
	        }
	    }
	}

	private Instances createTrainingSet(List<Instances> sets, int endIndex) {
	    Instances train = new Instances(sets.get(0));
	    for (int i = 1; i < endIndex; i++) {
	        train.addAll(sets.get(i));
	    }
	    return train;
	}


	public static List<Instances> applyFeatureSelection(AttributeSelection filter, Instances train, Instances test) throws Exception {
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
	    AttributeSelection filter = createFeatureSelectionFilter();
	    return applyFeatureSelection(filter, train, test);
	}



	
	private static Filter getBalancingFilter(String balancing, Instances train, int dim) throws Exception {
	    Filter filter = null;
	    switch (balancing) {
	        case "OVERSAMPLING":
	            Resample resample = new Resample();
	            resample.setNoReplacement(false);
	            resample.setBiasToUniformClass(0.1);
	            double sizePerc = 2 * (getSampleSizePerc(train, dim));
	            resample.setSampleSizePercent(sizePerc);
	            String[] overOpts = new String[]{"-B", "1.0", "-Z", "130.3"};
	            resample.setOptions(overOpts);
	            filter = resample;
	            break;
	        case "UNDERSAMPLING":
	            SpreadSubsample spreadSubsample = new SpreadSubsample();
	            String[] opts = new String[]{"-M", "1.0"};
	            spreadSubsample.setOptions(opts);
	            filter = spreadSubsample;
	            break;
	        case "SMOTE":
	            SMOTE smote = new SMOTE();
	            filter = smote;
	            break;
	        default:
	            break;
	    }
	    if (filter != null) {
	        filter.setInputFormat(train);
	    }
	    return filter;
	}
	private static FilteredClassifier createFilteredClassifier(Classifier c, Filter filter) {
	    FilteredClassifier fc = new FilteredClassifier();
	    fc.setClassifier(c);
	    if (filter != null) {
	        fc.setFilter(filter);
	    }
	    return fc;
	}

	private static FilteredClassifier balancing(Classifier c, String balancing, Instances train, int dim) throws Exception {
	    Filter filter = getBalancingFilter(balancing, train, dim);
	    return createFilteredClassifier(c, filter);
	}

	private static Evaluation costSensitiveness(String costEvaluation, Classifier c, Instances train, Instances test) {
	    Evaluation ev = null;
	    if (!costEvaluation.equals("NO")) {
	        ev = costSensitiveEvaluation(costEvaluation, c, train, test);
	    } else {
	        ev = standardEvaluation(c, train, test);
	    }
	    return ev;
	}

	private static Evaluation costSensitiveEvaluation(String costEvaluation, Classifier c, Instances train, Instances test) {
	    Evaluation ev = null;
	    CostSensitiveClassifier c1 = new CostSensitiveClassifier();
	    if (costEvaluation.equals("SENSITIVE THRESHOLD")) {
	        c1.setMinimizeExpectedCost(true);
	    } else if (costEvaluation.equals("SENSITIVE LEARNING")) {
	        c1.setMinimizeExpectedCost(false);
	        train = reweight(train);
	    }
	    c1.setClassifier(c);
	    c1.setCostMatrix(createCostMatrix());
	    try {
	        c1.buildClassifier(train);
	        ev = new Evaluation(test, c1.getCostMatrix());
	        ev.evaluateModel(c1, test);
	    } catch (Exception e) {
	        logger.log(Level.INFO, "An error has occurred handling classifier sensitiveness.");
	    }
	    return ev;
	}

	private static Evaluation standardEvaluation(Classifier c, Instances train, Instances test) {
	    Evaluation ev = null;
	    try {
	        c.buildClassifier(train);
	        ev = new Evaluation(test);
	        ev.evaluateModel(c, test);
	    } catch (Exception e) {
	        logger.log(Level.INFO, "An error has occurred evaluating a model.");
	    }
	    return ev;
	}

	private static double getSampleSizePerc(Instances train, int dim) {
	    double buggyPercentage = buggyPercentage(train);
	    double nonBuggyPercentage = 1 - buggyPercentage;
	    double majorityPercentage = Math.max(buggyPercentage, nonBuggyPercentage);
	    double minorityPercentage = Math.min(buggyPercentage, nonBuggyPercentage);
	    double sampleSizePerc = majorityPercentage * 100;
	    if (majorityPercentage == nonBuggyPercentage) {
	        sampleSizePerc = minorityPercentage * 100;
	    }
	    return sampleSizePerc;
	}

	
	private static double buggyPercentage(Instances train) {
	    int numBuggyInstances = (int) train.stream()
	            .filter(instance -> instance.stringValue(instance.classAttribute()).equals("Yes"))
	            .count();
	    return (double) numBuggyInstances / train.size();
	}

	private static CostMatrix createCostMatrix() {
	    CostMatrix costMatrix = new CostMatrix(2); // matrice 2x2
	    costMatrix.setCell(0, 0, 0.0); // Costo per vero positivo
	    costMatrix.setCell(1, 0, 1.0); // Costo per falso positivo
	    costMatrix.setCell(0, 1, 10.0); // Costo per falso negativo
	    costMatrix.setCell(1, 1, 0.0); // Costo per vero negativo
	    return costMatrix;
	}


	private static Instances reweight(Instances train) {
	    Instances train2 = new Instances(train);

	    double numNo = train.numInstances() - buggyCount(train);
	    double numYes = buggyCount(train);

	    if (numNo > numYes) {
	        int oversampleRatio = (int) Math.ceil(numNo / numYes);
	        for (int i = 0; i < train.numInstances(); i++) {
	            Instance instance = train.instance(i);
	            if (instance.stringValue(instance.classIndex()).equals("Yes")) {
	                for (int j = 0; j < oversampleRatio - 1; j++) {
	                    train2.add(instance);
	                }
	            }
	        }
	    }

	    return train2;
	}

	private static int buggyCount(Instances instances) {
	    int count = 0;
	    for (int i = 0; i < instances.numInstances(); i++) {
	        Instance instance = instances.instance(i);
	        if (instance.stringValue(instance.classIndex()).equals("Yes")) {
	            count++;
	        }
	    }
	    return count;
	}
	
	
	
	
	
	//Metodo di controllo dell'applicativo
	public static void computeAccuracy(String datasetPath, String projName) throws Exception {
		ArrayList<Instances> sets = getSets(datasetPath);
		List<String> featureSelection = Arrays.asList("NO", "BEST FIRST");
		List<String> balancing = Arrays.asList("NO", "UNDERSAMPLING", "OVERSAMPLING", "SMOTE");
		List<String> costEvaluation = Arrays.asList("NO", "SENSITIVE THRESHOLD", "SENSITIVE LEARNING");
		List<String> classifiers = Arrays.asList("RANDOM FOREST", "NAIVE BAYES", "IBK");
		ArrayList<Measure> measures = new ArrayList<>();
		ValidationHandler v = new ValidationHandler();
		
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
	}
	
	//Ottengo una lista dove ogni elemento è l'insieme delle istanze divise per versione
	private static ArrayList<Instances> getSets(String datasetPath) {
	    ArrayList<Instances> sets = new ArrayList<>();

	    try {
	        Instances data = DataSource.read(datasetPath);
	        // Ordina le istanze per version index
	        data.sort(0);
	        int numVersions = (int) data.lastInstance().value(0);
	        for (int v = 1; v <= numVersions; v++) {
	            Instances versionInstances = new Instances(data, 0);
	            for (int i = 0; i < data.numInstances(); i++) {
	                Instance instance = data.instance(i);
	                if ((int) instance.value(0) == v) {
	                    versionInstances.add(instance);
	                }
	            }
	            sets.add(versionInstances);
	        }
	    } catch (Exception e) {
	        logger.log(Level.INFO, "An error has occurred acquiring the dataset.");
	    }
	    return sets;
	}


	public static void printMeasures(String project, List<Measure> measures) {
	    String outName = project + "Output.csv";

	    try (FileWriter fileWriter = new FileWriter(outName)) {
	        fileWriter.append("Dataset,#TrainingRelease,%Training,%Defective in training,%Defective in testing,Classifier,Balancing,Feature Selection,Sensitivity,TP,FP,TN,FN,Precision,Recall,AUC,Kappa");
	        fileWriter.append("\n");
	        for (Measure measure : measures) {
	            writeMeasure(fileWriter, measure);
	        }
	    } catch (IOException e) {
	        logger.log(Level.INFO, "Error occurred writing the CSV.");
	    }
	}

	private static void writeMeasure(FileWriter fileWriter, Measure measure) throws IOException {
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
	    Map<String, Supplier<Classifier>> classifierMap = new HashMap<>();
	    classifierMap.put("RANDOM FOREST", RandomForest::new);
	    classifierMap.put("NAIVE BAYES", NaiveBayes::new);
	    classifierMap.put("IBK", IBk::new);

	    return classifierMap.getOrDefault(type, () -> null).get();
	}


}