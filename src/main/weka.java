package weka;





import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
		String projectName = "BOOKKEEPER";
		
		String nomeFile = projectName.toLowerCase() + "Dataset.csv" ;
		String progetto =  " relativo al progetto " + projectName;
		String path = "/Users/giuliamenichini/eclipse-workspace/ISW2/" + nomeFile ;
	    logger.info("Caricando dataset: " + nomeFile + progetto);

		createFile(path,"BOOKKEEPER");
		

	}
	//Questo codice calcola il rapporto tra la dimensione del training set
	//e la somma delle dimensioni del training set e del test set. 
	//Il valore restituito è un numero compreso tra 0 e 1 che rappresenta 
	//la frazione del totale delle istanze che sono nel training set. 
	//Ad esempio, se il rapporto è 0,8, significa che l'80% delle istanze totali 
	//sono nel training set e il restante 20% è nel test set.
	private static double calculateTrainingTestingRatio(Instances train, Instances test) {
	    double trainSize = train.numInstances();
	    double testSize = test.numInstances();
	    return trainSize / (trainSize + testSize);
	}

	

	

	private static double calculateBuggy(Instances instances) {

	    return numOfBuggy(instances);
	}
	// metodo per ipostare nome del progetto, release, classificatore utilizzato, bilanciamento, featureSelection
	//sensitivity, defectiveInTraining, defectiveInTesting, trainPercentage, testPercentage
	public static Measure createMeasureObject(Instances train, Instances test, String[] s, String projName, int version) {
	    Measure m = new Measure(projName);
	    m.setReleaseNumber(version + 1);
	    m.setClassifier(s[0]);
	    m.setBalancingMethod(s[1]);
	    m.setFeatureSelectionMethod(s[2]);
	    m.setSensitivityMethod(s[3]);
	    m.setTrainingTestingRatio(calculateTrainingTestingRatio(train, test));
	    m.setBuggyPercentageInTrainingSet(calculateBuggy(train));
	    m.setBuggyPercentageInTestingSet(calculateBuggy(test));
	    return m;
	}


	//prende in input le info e setta le metriche di valutazione con tp,fp,fn,tn
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


	
	//filtro di selezione delle feature, apploca CfsSubsetEval che valuta l'importanza di ogni feature
	//in base alla relazione con la classe target e con gli altri attributi
	//BestFirst ricerca il sottoinsieme migliore utilizzando una strategia di ricerca forward
	//la ricerca è indipendente dall'algoritmo di addestramento
	public static AttributeSelection createFeatureSelectionFilter() throws Exception {
	    AttributeSelection filter = new AttributeSelection();
	    filter.setEvaluator(new CfsSubsetEval());
	    filter.setSearch(new BestFirst());
	    return filter;
	    
	}
	//creazione set di addestramento e di test . Il set di test è creato a partire dall'insieme corrente mentre il train
	//tutte meno una infine si impostano gli attributi.
	public Instances[] createTrainingAndTestSet(List<Instances> sets, int currentIndex) {
	    Instances testSet = new Instances(sets.get(currentIndex));
	    Instances trainingSet = createTrainingSet(sets, currentIndex);
	    trainingSet.setClassIndex(trainingSet.numAttributes() - 1);
	    testSet.setClassIndex(testSet.numAttributes() - 1);
	    Instances[] trainingAndTestSet = {trainingSet, testSet};
	    return trainingAndTestSet;
	}
	// applicazione algoritmo di FS al train e al test in particolare BESTFIRST
	public Instances[] applyFeatureSelection(String featureSelection, Instances trainingSet, Instances testSet) throws Exception {
	    Instances filteredTrainingSet = trainingSet;
	    Instances filteredTestSet = testSet;
	    switch(featureSelection) {
	        case "BEST FIRST":
	            List<Instances> filteredSets = featureSelection(trainingSet, testSet);
	            filteredTrainingSet = filteredSets.get(1);
	            filteredTestSet = filteredSets.get(0);
	            break;
	        default:
	            break;
	    }
	    Instances[] filteredTrainingAndTestSet = {filteredTrainingSet, filteredTestSet};
	    return filteredTrainingAndTestSet;
	}
	//nessuna applicazione del bilanciamento
	public Evaluation trainAndEvaluateModel(String balancing, String classifier, String costEvaluation, Instances trainingSet, Instances testSet) throws Exception {
	    Classifier classifierInstance = chooseClassificationType(classifier);
	    switch(balancing) {
	        case "NO":
	            break;
	        default:
	            classifierInstance = balancing(classifierInstance, balancing, trainingSet);
	            break;
	    }
	    Evaluation evaluation = evaluateModel(costEvaluation, classifierInstance, trainingSet, testSet);
	    return evaluation;
	}
	//Iterazione di un loop su i dati di train e test con l'applicazione di FS, balancing e classification. 
	//Per ogni iterazione i dati vengono salvati e si va avanti per ogni versione
	public void walkForward(String featureSelection, String balancing, String costEvaluation, String classifier, List<Instances> sets, List<Measure> measures, String projectName) throws Exception {
	    int version = 0;
	    for (int i = 1; i < sets.size(); i++) {
	        Instances[] trainingAndTestSet = createTrainingAndTestSet(sets, i);
	        Instances trainingSet = trainingAndTestSet[0];
	        Instances testSet = trainingAndTestSet[1];
	        
	        Instances[] filteredTrainingAndTestSet = applyFeatureSelection(featureSelection, trainingSet, testSet);
	        trainingSet = filteredTrainingAndTestSet[0];
	        testSet = filteredTrainingAndTestSet[1];
	        Evaluation evaluation = trainAndEvaluateModel(balancing, classifier, costEvaluation, trainingSet, testSet);
	        
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

	//input = indice che specifica il set di test che verrà escluso dal set di addestramento.
	// se la lista è vuota viene restituita istanza vuota
	private Instances createTrainingSet(List<Instances> sets, int endIndex) {
	    Optional<Instances> merged = sets.stream()
	            .limit(endIndex)
	            .reduce((train, next) -> {
	                Instances mergedSet = new Instances(train, 0);
	                for (Instance instance : next) {
	                    mergedSet.add(instance);
	                }
	                return mergedSet;
	            });
	    return merged.orElse(new Instances(sets.get(0), 0));
	}

	//copia istante train e test applica FS
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


	//oversampling: aumento numero istanze della classe minoritaria
	//undersampling: riduco numero istanze della classe maggioritaria
	//smote: genero sinteticamente nuove istanze della classe minoritaria
	private static Filter getBalancingFilter(String balancing, Instances train) throws Exception {
	    Filter filter = null;
	    if ("OVERSAMPLING".equals(balancing)) {
	        filter = getResampleFilter(train, false, 0.1, 2 * foundPerc(train), new String[]{"-B", "1.0", "-Z", "5"});
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
	//data: istanze su cui fare campionamento
	//noReplacement: campionamento con o senza rimpiazzamento
	//biasUniformClass: double indica importanza tra classe minoritaria e maggioritaria più è vicino a 1 più si da
	//importanza alla classe minoritaria 
	//sampleSizePercent: percentuale istanze da mantenere dopo campionamento
	//options: array di stringhe per info aggiuntive
	private static Resample getResampleFilter(Instances data, boolean noReplacement, double biasToUniformClass, double sampleSizePercent, String[] options) throws Exception {
		Resample resample = new Resample();
	    resample.setNoReplacement(noReplacement);
	    resample.setBiasToUniformClass(biasToUniformClass);
	    resample.setSampleSizePercent(sampleSizePercent);
	    resample.setOptions(options);
	    resample.setInputFormat(data);
	    return resample;
	}
	//filtro per sottocampionamento
	private static SpreadSubsample getSpreadSubsampleFilter(Instances data, String[] options) throws Exception {
		SpreadSubsample spreadSubsample = new SpreadSubsample();
	    spreadSubsample.setOptions(options);
	    spreadSubsample.setInputFormat(data);
	    return spreadSubsample;
	}
	//smote function
	private static SMOTE getSMOTEFilter(Instances data) throws Exception {
		SMOTE smote = new SMOTE();
	    smote.setInputFormat(data);
	    return smote;
	}

	//applica filtro prima di passare i dati al classificatore
	private static FilteredClassifier createFilteredClassifier(Classifier c, Filter filter) {
		FilteredClassifier fc = new FilteredClassifier();
	    fc.setClassifier(c);
	    if (filter != null) {
	        fc.setFilter(filter);
	    }
	    return fc;
	}

	private static FilteredClassifier balancing(Classifier c, String balancing, Instances train) throws Exception {
		Filter filter = getBalancingFilter(balancing, train);
	    return createFilteredClassifier(c, filter);
	}

	
	//sesitiveTreshold imposta matrice di costo specifica per le classi in modo da dare più peso alla class. di una classe
	//sensitiveLearning imposta matrice di costo ed esegue reweight per dare più peso ad alcune istanze
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
	            c1.setCostMatrix(createCostMatrix(1.0, 10.0));
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
	//filtraggio bilanciato 
	private static double foundPerc(Instances train) {
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

	//percentuale buggy  = true
	private static double numOfBuggy(Instances train) {
		int numBuggy = (int) train.stream()
	            .filter(instance -> instance.stringValue(instance.classAttribute()).equals("true"))
	            .count();
	    return (double) numBuggy / train.size();
	}
	//peso assegnato alla matrice di costo
	private static CostMatrix createCostMatrix(double weightFalsePositive, double weightFalseNegative) {
		CostMatrix costMatrix = new CostMatrix(2); 
	    costMatrix.setCell(0, 0, 0.0); 
	    costMatrix.setCell(1, 0, weightFalsePositive); 
	    costMatrix.setCell(0, 1, weightFalseNegative); 
	    costMatrix.setCell(1, 1, 0.0); 
	    return costMatrix;
	}

	//aggiunge numero di istanze classe minoritaria
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
	//conta numero di buggy
	private static int buggyCount(Instances data) {
	    return (int) data.stream()
	            .filter(instance -> instance.stringValue(instance.classIndex()).equals("true"))
	            .count();
	}

	


	//Metodo di controllo dell'applicativo
	public static void createFile(String datasetPath, String projName) throws Exception {
	    logger.info("Creando il file di output");
	    ArrayList<Instances> sets = getSets(datasetPath);
	    List<List<String>> modelConfigs = getModelConfigurations();
	    ArrayList<Measure> measures = computeMeasures(modelConfigs, sets, projName);
	    toCSV(projName, measures);
	    logger.info("File creato!");
	}
	public static List<List<String>> getModelConfigurations() {
	    List<String> fS = Arrays.asList("NO", "BEST FIRST");
	    List<String> b = Arrays.asList("NO", "UNDERSAMPLING", "OVERSAMPLING", "SMOTE");
	    List<String> cE = Arrays.asList("NO", "SENSITIVE THRESHOLD", "SENSITIVE LEARNING");
	    List<String> c = Arrays.asList("RANDOM FOREST", "NAIVE BAYES", "IBK");

	    return fS.stream()
	            .flatMap(a -> b.stream()
	                    .flatMap(g -> cE.stream()
	                            .flatMap(z -> c.stream()
	                                    .map(d -> Arrays.asList(a, g, z, d)))))
	            .collect(Collectors.toList());
	}


	    
	public static ArrayList<Measure> computeMeasures(List<List<String>> modelConfigs, ArrayList<Instances> sets, String projName) throws Exception {
	    ArrayList<Measure> measures = new ArrayList<>();
	    weka v = new weka();

	    for (List<String> config : modelConfigs) {
	        String a = config.get(0);
	        String b = config.get(1);
	        String c = config.get(2);
	        String d = config.get(3);

	        v.walkForward(a, b, c, d, sets, measures, projName);
	    }

	    return measures;
	}
	
	//ordina istanze dataset in base alla versione
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


	
	public static void toCSV(String project, List<Measure> measures) {
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

		
	public static Classifier chooseClassificationType(String type) {
		Map<String, Supplier<Classifier>> classifierMap = new HashMap<>();
	    classifierMap.put("RANDOM FOREST", RandomForest::new);
	    classifierMap.put("NAIVE BAYES", NaiveBayes::new);
	    classifierMap.put("IBK", IBk::new);

	    return classifierMap.getOrDefault(type, () -> null).get();
	}


}