package weka;





import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
		String dataSetA = "/Users/giuliamenichini/eclipse-workspace/ISW2/bookkeeperDataset.arff";
		computeAccuracy(dataSetA,"BOOKKEEPER");
		

	}

	//calcolo probabilità di train rispetto a train + test
	private static double  calculateTrainingTestingRatio(Instances train, Instances test) {
	    return (train.size()) / ((double) train.size() + (double) test.size());
	}

	private static int calculateModuleNumber(String projName) {
	    switch (projName) {
	        case "BOOKKEEPER":
	            return 6;
	        case "OPENJPA":
	            return 16;
	        default:
	            return 1;
	    }
	}


	private static int calculateReleaseNumber( int version, int mod) {

	    return (version % mod) + 1;
	}

	private static double calculateBuggy(Instances instances) {

	    return numOfBuggy(instances);
	}

	public static Measure createMeasureObject(Instances train, Instances test, String[] s, String projName, int version) {
	    Measure m = new Measure(projName);
	    m.setReleaseNumber(calculateReleaseNumber(version, calculateModuleNumber(projName)));
	    m.setClassifier(s[0]);
	    m.setBalancingMethod(s[1]);
	    m.setFeatureSelectionMethod(s[2]);
	    m.setSensitivityMethod(s[3]);
	    m.setTrainingTestingRatio(calculateTrainingTestingRatio(train, test));
	    m.setBuggyPercentageInTrainingSet(calculateBuggy(train));
	    m.setBuggyPercentageInTestingSet(calculateBuggy(test));
	    return m;
	}



	private static void evaluateAndAddMeasure(Evaluation e, Instances train, Instances test, String[] s, String projName, List<Measure> measures, int version) {
	    Measure measure = createMeasureObject(train, test, s, projName, version);
	    measure.setAuc(e.areaUnderROC(1));
	    measure.setKappa(e.kappa());
	    measure.setRecall(e.recall(1));
	    measure.setPrecision(e.precision(1));
	    measure.setTrueNegatives((int) e.numTrueNegatives(1));
	    measure.setTruePositives((int) e.numTruePositives(1));
	    measure.setFalseNegatives((int) e.numFalseNegatives(1));
	    measure.setFalsePositives((int) e.numFalsePositives(1));
	    measures.add(measure);
	}

	public static AttributeSelection createFeatureSelectionFilter() throws Exception {
	    AttributeSelection filter = new AttributeSelection();
	    filter.setEvaluator(new CfsSubsetEval());
	    filter.setSearch(new BestFirst());
	    return filter;
	    
	}
	//da rivedere
	public void walkForward(String featureSelection, String balancing, String costEvaluation, String classifier, List<Instances> sets, List<Measure> measures, String projectName) throws Exception {
	    int version = 0;
	    for (int i = 1; i < sets.size(); i++) {
	        Instances testSet = new Instances(sets.get(i));
	        Instances trainingSet = createTrainingSet(sets, i);
	        trainingSet.setClassIndex(trainingSet.numAttributes() - 1);
	        testSet.setClassIndex(testSet.numAttributes() - 1);
	        switch(featureSelection) {
	            case "BEST FIRST":
	                List<Instances> filteredSets = featureSelection(trainingSet, testSet);
	                trainingSet = filteredSets.get(1);
	                testSet = filteredSets.get(0);
	                break;
	            default:
	                
	                break;
	        }
	        Classifier classifierInstance = generateClassifier(classifier);
	        switch(balancing) {
	            case "NO":
	                //do nothing
	                break;
	            default:
	                classifierInstance = balancing(classifierInstance, balancing, trainingSet);
	                break;
	        }
	        Evaluation evaluation = evaluateModel(costEvaluation, classifierInstance, trainingSet, testSet);
	        String[] labels = {classifier, balancing, featureSelection, costEvaluation};
	        if (evaluation == null) {
	            logger.log(Level.INFO, "Errore valutando il modello");
	        } else {
	            createMeasureObject(trainingSet, testSet, labels, projectName, version);
	            evaluateAndAddMeasure(evaluation, trainingSet, testSet, labels, projectName, measures, version);
	            version++;
	        }
	    }
	}


//da rivedere
	private Instances createTrainingSet(List<Instances> sets, int endIndex) {
	    Instances train = new Instances(sets.get(0));
	    for (int i = 1; i < endIndex; i++) {
	        train.addAll(sets.get(i));
	    }
	    return train;
	}


	public static List<Instances> applyFeatureSelection(AttributeSelection filter, Instances train, Instances test) throws Exception {
		Instances[] filteredData = new Instances[]{new Instances(train), new Instances(test)};
		filter.setInputFormat(train);
		for (int i = 0; i < filteredData.length; i++) {
			filteredData[i] = Filter.useFilter(filteredData[i], filter);
			filteredData[i].setClassIndex(filteredData[i].numAttributes() - 1);
			}
		return Arrays.asList(filteredData[1], filteredData[0]);
		}
	
	public static List<Instances> featureSelection(Instances train, Instances test) throws Exception {
		return applyFeatureSelection(createFeatureSelectionFilter(), train, test);
		}



	
	private static Filter getBalancingFilter(String balancing, Instances train) throws Exception {
		Filter filter = null;
		switch (balancing) {
			case "OVERSAMPLING":
				filter = getResampleFilter(train, false, 0.1, 2 * getSampleSizePerc(train), new String[]{"-B", "1.0", "-Z", "130.3"});
			break;
			case "UNDERSAMPLING":
				filter = getSpreadSubsampleFilter(train, new String[]{"-M", "1.0"});
			break;
			case "SMOTE":
				filter = getSMOTEFilter(train);
			break;
			}
			if (filter != null) {
				filter.setInputFormat(train);
			}
			return filter;
		}

	private static Resample getResampleFilter(Instances data, boolean noReplacement, double biasToUniformClass, double sampleSizePercent, String[] options) throws Exception {
		return new Resample() {private static final long serialVersionUID = 1L;

		{
		setNoReplacement(noReplacement);
		setBiasToUniformClass(biasToUniformClass);
		setSampleSizePercent(sampleSizePercent);
		setOptions(options);
		}};
	}
		

	private static SpreadSubsample getSpreadSubsampleFilter(Instances data, String[] options) throws Exception {
		return new SpreadSubsample() {private static final long serialVersionUID = 1L;

		{
		setOptions(options);
		}};
		}

	private static SMOTE getSMOTEFilter(Instances data) throws Exception {
		return new SMOTE();
		}



	private static FilteredClassifier balancing(Classifier c, String balancing, Instances train) throws Exception {
		return new FilteredClassifier();
		}



	
	
	private static Evaluation evaluateModel(String costEvaluation, Classifier c, Instances train, Instances test) {
	    Evaluation ev = null;

	    switch(costEvaluation) {
	        case "NO":
	            try {
	                c.buildClassifier(train);
	                ev = new Evaluation(test);
	                ev.evaluateModel(c, test);
	            } catch (Exception e) {
	                logger.log(Level.INFO, "Errore durante la valutazione del modello");
	            }
	            break;
	        case "SENSITIVE THRESHOLD":
	            CostSensitiveClassifier c1 = new CostSensitiveClassifier();
	            c1.setClassifier(c);
	            c1.setCostMatrix(createCostMatrix(10.0, 1.0));
	            c1.setMinimizeExpectedCost(true);
	            try {
	                c1.buildClassifier(train);
	                ev = new Evaluation(test, c1.getCostMatrix());
	                ev.evaluateModel(c1, test);
	            } catch (Exception e) {
	                logger.log(Level.INFO, "Errore sensitiveness");
	            }
	            break;
	        case "SENSITIVE LEARNING":
	            CostSensitiveClassifier c2 = new CostSensitiveClassifier();
	            c2.setClassifier(c);
	            c2.setCostMatrix(createCostMatrix(10.0, 1.0));
	            c2.setMinimizeExpectedCost(false);
	            try {
	                Instances weightedTrain = reweight(train);
	                c2.buildClassifier(weightedTrain);
	                ev = new Evaluation(test, c2.getCostMatrix());
	                ev.evaluateModel(c2, test);
	            } catch (Exception e) {
	                logger.log(Level.INFO, "Errore sensitiveness");
	            }
	            break;
	        default:
	            logger.log(Level.INFO, "Cost evaluation non valida");
	            break;
	    }

	    return ev;
	}



	private static double getSampleSizePerc(Instances train) {
	    double numOfBuggy = numOfBuggy(train);
	    double nonnumOfBuggy = 1 - numOfBuggy;
	    double sampleSizePerc = Math.max(numOfBuggy, nonnumOfBuggy) * 100;
	    return sampleSizePerc;
	}


	
	private static double numOfBuggy(Instances train) {
		int numBuggy = (int) train.stream()
	            .filter(instance -> instance.stringValue(instance.classAttribute()).equals("true"))
	            .count();
	    return (double) numBuggy / train.size();
	}

	private static CostMatrix createCostMatrix(double weightFalsePositive, double weightFalseNegative) {
		CostMatrix costMatrix = new CostMatrix(2); 
	    costMatrix.setCell(0, 0, 0.0); 
	    costMatrix.setCell(1, 0, weightFalsePositive); 
	    costMatrix.setCell(0, 1, weightFalseNegative); 
	    costMatrix.setCell(1, 1, 0.0); 
	    return costMatrix;
	}


	private static Instances reweight(Instances train) {
	    int numNo = train.numInstances() - buggyCount(train);
	    int numYes = buggyCount(train);

	    if (numNo <= numYes) {
	        return train;
	    }

	    Instances oversampledTrain = new Instances(train);
	    oversampledTrain.randomize(new Random());
	    int i = 0;

	    for (Instance inst : train) {
	        oversampledTrain.add(inst);
	        if (inst.classValue() == 1) {
	            int timesToAdd = numNo / numYes - 1;
	            for (int j = 0; j < timesToAdd; j++) {
	                oversampledTrain.add(inst);
	            }
	            if (numNo % numYes > i) {
	                oversampledTrain.add(inst);
	            }
	            i++;
	        }
	    }

	    return oversampledTrain;
	}


	private static int buggyCount(Instances data) {
	    return (int) data.stream()
	            .filter(instance -> instance.stringValue(instance.classIndex()).equals("true"))
	            .count();
	}


	

//da rivedere
	//Metodo di controllo dell'applicativo
	public static void computeAccuracy(String datasetPath, String projName) throws Exception {
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
	private static ArrayList<Instances> getSets(String datasetPath) throws Exception {
	    Instances data = DataSource.read(datasetPath);
	    data.sort(0);
	    return extractVersions(data);
	}

	private static ArrayList<Instances> extractVersions(Instances data) {
	    return IntStream.rangeClosed(1, (int) data.lastInstance().value(0))
	            .mapToObj(version -> extractInstancesForVersion(data, version))
	            .collect(Collectors.toCollection(ArrayList::new));
	}

	private static Instances extractInstancesForVersion(Instances data, int version) {
	    Instances versionInstances = data.stream()
	            .filter(instance -> (int) instance.value(0) == version)
	            .collect(Collectors.toCollection(() -> {
	                Instances instances = new Instances(data, 0);
	                instances.setRelationName("version_" + version);
	                return instances;
	            }));
	    return versionInstances;
	}




	
	public static void printMeasures(String project, List<Measure> measures) {
	    String outName = project + "Output.csv";
	    
	    try (PrintWriter writer = new PrintWriter(new FileWriter(outName))) {
	        writer.println("Dataset,#TrainingRelease,%Training,%Defective in training,%Defective in testing,Classifier,Balancing,Feature Selection,Sensitivity,TP,FP,TN,FN,Precision,Recall,AUC,Kappa");
	        
	        measures.forEach(measure -> {
				try {
					createCSV(writer, measure);
				} catch (IOException e) {
					
					e.printStackTrace();
				}
			});
	    } catch (IOException e) {
	        logger.log(Level.INFO, "Errore durante la scrittura del csv.");
	    }
	}


	private static void createCSV(PrintWriter writer, Measure measure) throws IOException {
	    writer.append(measure.getDataset());
	    writer.append(",");
	    writer.append(measure.getRelease().toString());
	    writer.append(",");
	    writer.append(measure.getTrainPercentage().toString());
	    writer.append(",");
	    writer.append(measure.getDefectInTrainPercentage().toString());
	    writer.append(",");
	    writer.append(measure.getDefectInTestPercentage().toString());
	    writer.append(",");
	    writer.append(measure.getClassifier());
	    writer.append(",");
	    writer.append(measure.getBalancing());
	    writer.append(",");
	    writer.append(measure.getFeatureSelection());
	    writer.append(",");
	    writer.append(measure.getSensitivity());
	    writer.append(",");
	    writer.append(measure.getTp().toString());
	    writer.append(",");
	    writer.append(measure.getFp().toString());
	    writer.append(",");
	    writer.append(measure.getTn().toString());
	    writer.append(",");
	    writer.append(measure.getFn().toString());
	    writer.append(",");
	    writer.append(measure.getPrecision().toString());
	    writer.append(",");
	    writer.append(measure.getRecall().toString());
	    writer.append(",");
	    writer.append(measure.getAuc().toString());
	    writer.append(",");
	    writer.append(measure.getKappa().toString());
	    writer.append("\n");
		

	}

		
	public static Classifier generateClassifier(String type) {
		Map<String, Supplier<Classifier>> classifierMap = new HashMap<>();
	    classifierMap.put("RANDOM FOREST", RandomForest::new);
	    classifierMap.put("NAIVE BAYES", NaiveBayes::new);
	    classifierMap.put("IBK", IBk::new);

	    return classifierMap.getOrDefault(type, () -> null).get();
	}


}