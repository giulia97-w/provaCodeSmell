package main;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.json.JSONException;

import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class MainClass {
	
	public static final String PROJECT = "BOOKKEEPER";
	public static final String PROJECT1 = "OPENJPA";
	static String nameGit = "git";
	static String endPath = "/." + nameGit ;
	
	static String name = "giuliamenichini";
	static String percorso = "/Users/" + name + "/";
	public static final String PATH = System.getProperty("user.dir");
	public static final String DATASET_FILENAME = "Dataset.csv";
	public static final String URI_OPENJPA = PATH + PROJECT1 + DATASET_FILENAME;
	public static final String URI_BOOKKEEPER = PATH + PROJECT1 + DATASET_FILENAME;


	static String uriArffOpenjpa = PATH +PROJECT1+"Dataset.arff";
	static String uriArffBookkeeper = PATH+PROJECT+"Dataset.arff";

	private static int movingWindows;

	private static final String ENDFILE = ".java";
	private static final String DELETE = "DELETE";
	
	private static void update(BufferedWriter fileWriter, Release release, JavaFile file) throws IOException {
	    StringBuilder sb = new StringBuilder();
	    sb.append(release.getIndex()).append(",");
	    sb.append(file.getName()).append(",");
	    sb.append(file.getlinesOfCode()).append(",");
	    sb.append(file.getlinesOfCodeadded()).append(",");

	    // ciclo for per scrivere i valori massimi e medi di linesOfCodeAddedList
	    List<Integer> linesOfCodeAddedList = file.getlinesOfCodeAddedList();
	    int maxLOCAdded = 0;
	    double avgLOCAdded = 0;
	    for (int i = 0; i < linesOfCodeAddedList.size(); i++) {
	        int locAdded = linesOfCodeAddedList.get(i);
	        maxLOCAdded = Math.max(maxLOCAdded, locAdded);
	        avgLOCAdded += locAdded;
	    }
	    avgLOCAdded /= linesOfCodeAddedList.size();
	    sb.append(maxLOCAdded).append(",").append(avgLOCAdded).append(",");

	    sb.append(file.getChurn()).append(",");

	    // ciclo for per scrivere i valori massimi e medi di churnList
	    List<Integer> churnList = file.getChurnList();
	    int maxChurn = 0;
	    double avgChurn = 0;
	    for (int i = 0; i < churnList.size(); i++) {
	        int churn = churnList.get(i);
	        maxChurn = Math.max(maxChurn, churn);
	        avgChurn += churn;
	    }
	    avgChurn /= churnList.size();
	    sb.append(maxChurn).append(",").append(avgChurn).append(",");

	    sb.append(file.getNr()).append(",");
	    sb.append(file.getNAuth().size()).append(",");
	    sb.append(file.getBuggyness()).append("\n");

	    fileWriter.write(sb.toString());
	    fileWriter.flush();
	}

    private static void linkFunction() {
    	linkage();
        setResolutionDateAndFVBookkeeper();
        setResolutionDateAndFVOpenjpa();
        removeUnlinkedTickets();
    }

    private static LocalDateTime getCommitDate(RevCommit commit) {
        Instant commitInstant = Instant.ofEpochSecond(commit.getAuthorIdent().getWhen().getTime());
        return LocalDateTime.ofInstant(commitInstant, ZoneId.systemDefault());
    }

    private static void addCommitToTicket(Ticket ticket, RevCommit commit) {
        ticket.getCommitList().add(commit);
    }
    
    private static ArrayList<LocalDateTime> findCommitDatesForTicket(Ticket ticket, List<RevCommit> commitList) {
        ArrayList<LocalDateTime> commitDateList = new ArrayList<>();

        String ticketID = ticket.getTicketID();
        commitList.stream()
                  .filter(commit -> commit.getFullMessage().contains(ticketID))
                  .forEach(commit -> {
                      LocalDateTime commitDate = getCommitDate(commit);
                      commitDateList.add(commitDate);
                      addCommitToTicket(ticket, commit);
                  });

        return commitDateList;
    }



    private static void updateTicketCommitDatesBookkeeper() {
        for (Ticket ticket : ticketListBookkeeper) {
            ArrayList<LocalDateTime> commitDateList = findCommitDatesForTicket(ticket, commitListBookkeeper);
            ticket.setCommitDateList(commitDateList);
        }
    }
    private static void updateTicketCommitDatesOpenjpa() {
        for (Ticket ticket : ticketListOpenjpa) {
            ArrayList<LocalDateTime> commitDateList = findCommitDatesForTicket(ticket, commitListOpenjpa);
            ticket.setCommitDateList(commitDateList);
        }
    }
    
    private static void linkage() {
        updateTicketCommitDatesBookkeeper();
        updateTicketCommitDatesOpenjpa();
    }



    private static void setResolutionDateAndFVBookkeeper() {
        for (Ticket ticket : ticketListBookkeeper) {
            ArrayList<LocalDateTime> commitDateList = ticket.getCommitList().stream()
                    .map(commit -> commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));

            Optional<LocalDateTime> latestCommitDate = commitDateList.stream()
                    .max(LocalDateTime::compareTo);

            latestCommitDate.ifPresent(resolutionDate -> {
                ticket.setResolutionDate(resolutionDate);
                ticket.setFixedVersion(afterBeforeDate(resolutionDate, releasesListBookkeeper));
            }); }

    }
    private static void setResolutionDateAndFVOpenjpa() {
        for (Ticket ticket : ticketListOpenjpa) {
            ArrayList<LocalDateTime> commitDateList = ticket.getCommitList().stream()
                    .map(commit -> commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));

            Optional<LocalDateTime> latestCommitDate = commitDateList.stream()
                    .max(LocalDateTime::compareTo);

            latestCommitDate.ifPresent(resolutionDate -> {
                ticket.setResolutionDate(resolutionDate);
                ticket.setFixedVersion(afterBeforeDate(resolutionDate, releasesListOpenjpa));
            }); }
    }

    private static void removeUnlinkedTickets() {
        ticketListBookkeeper.removeIf(ticket -> ticket.getResolutionDate() == null);
        ticketListOpenjpa.removeIf(ticket -> ticket.getResolutionDate() == null);
    }


    


    public static void removeReleaseBeforeHalf(List<Release> releasesList, List<Ticket> ticketList) {
        float releaseNumber = releasesList.size();
        int halfRelease = (int) Math.floor(releaseNumber / 2);

        // remove releases after the half point
        removeReleasesAfterHalfPoint(releasesList, halfRelease);

        // remove unresolved tickets and set the AV resolution
        filterTickets(halfRelease, ticketList);
    }

    private static void removeReleasesAfterHalfPoint(List<Release> releases, int releaseNew) {
        for (Release release : new ArrayList<>(releases)) {
            if (release.getIndex() > releaseNew) {
                releases.remove(release);
            }
        }
    }


    private static void filterTickets(int release, List<Ticket> tickets) {
        tickets.removeIf(ticket -> ticket.getInjectedVersion() > release || 
                                   ticket.getResolutionDate() == null ||
                                   ticket.getFixedVersion() > release);
    }



    public static void affectedVersion(int release, List<Ticket> ticket) {
        removeTickets(release, ticket);
        setAffectedVersionTicketsBookkeeper(release);
        setAffectedVersionTicketsOpenjpa(release);
    }

    private static void removeTickets(int release, List<Ticket> tickets) {
        tickets.removeIf(ticket -> ticket.getInjectedVersion() > release);
    }



    private static void setAffectedVersionTicketsBookkeeper(int release) {
        ticketListBookkeeper.stream()
                            .filter(t -> t.getOpenVersion() > release || t.getFixedVersion() > release)
                            .forEach(t -> {
                                List<Integer> affectedVersion = IntStream.range(t.getInjectedVersion(), release)
                                                                         .boxed()
                                                                         .collect(Collectors.toList());
                                t.setAffectedVersion(affectedVersion);
                            });
    }

    
    private static void setAffectedVersionTicketsOpenjpa(int release) {
        ticketListOpenjpa.stream()
                            .filter(t -> t.getOpenVersion() > release || t.getFixedVersion() > release)
                            .forEach(t -> {
                                List<Integer> affectedVersion = IntStream.range(t.getInjectedVersion(), release)
                                                                         .boxed()
                                                                         .collect(Collectors.toList());
                                t.setAffectedVersion(affectedVersion);
                            });
    }


    public static void checkTicketBookkeeper() {
        ticketListBookkeeper.stream()
            .filter(ticket -> ticket.getInjectedVersion() != 0)
            .forEach(ticket -> {
                if (ordering(ticket)) {
                    List<Integer> affectedVersions = new ArrayList<>();
                    for (int i = ticket.getInjectedVersion(); i < ticket.getFixedVersion(); i++) {
                        affectedVersions.add(i);
                    }
                    ticket.setAffectedVersion(affectedVersions);
                } else {
                    setErrorTicket(ticket);
                }
                handleOV(ticket);
            });
    }


    
    public static void checkTicketOpenjpa() {
        ticketListOpenjpa.stream()
            .filter(ticket -> ticket.getInjectedVersion() != 0)
            .forEach(ticket -> {
                if (ordering(ticket)) {
                    List<Integer> affectedVersions = new ArrayList<>();
                    for (int i = ticket.getInjectedVersion(); i < ticket.getFixedVersion(); i++) {
                        affectedVersions.add(i);
                    }
                    ticket.setAffectedVersion(affectedVersions);
                } else {
                    setErrorTicket(ticket);
                }
                handleOV(ticket);
            });
    }



    private static void handleOV(Ticket ticket) {
        if (ticket.getOpenVersion() == 1) {
            handleOVEquals1(ticket);
        } else {
            handleOVNotEquals1(ticket);
        }
    }



    private static boolean ordering(Ticket ticket) {
        return ticket.getFixedVersion() > ticket.getInjectedVersion() && ticket.getOpenVersion() >= ticket.getInjectedVersion();
    }


    private static void setErrorTicket(Ticket ticket) {
    	List<Integer> av = new ArrayList<>();
    	av.add(0);
    	ticket.setAffectedVersion(av);
    	ticket.setInjectedVersion(0);
    	if (ticket.getFixedVersion() == ticket.getInjectedVersion()) {
    		ticket.getAffectedVersion().clear();
    	}
    }



    private static void handleOVEquals1(Ticket ticket) {
    	ticket.setInjectedVersion(1);
    	if (ticket.getFixedVersion() == 1) {
    		ticket.setAffectedVersion(Collections.emptyList());
    	} else {
    		List<Integer> avList = IntStream.range(ticket.getInjectedVersion(), ticket.getFixedVersion())
		    	.boxed()
		    	.collect(Collectors.toList());
		    	ticket.setAffectedVersion(avList);
    	}
    	}

    private static void handleOVLessThanFV(Ticket ticket) {
        ticket.setInjectedVersion(ticket.getOpenVersion()); 
        int numAV = ticket.getFixedVersion() - ticket.getOpenVersion(); 
        ticket.setAffectedVersion(IntStream.range(ticket.getInjectedVersion(), ticket.getFixedVersion()).boxed().collect(Collectors.toList()));
        if (numAV <= 0) {
            ticket.getAffectedVersion().clear(); 
        }
    }


    private static void handleOVMoreThanFV(Ticket ticket) {
        int targetInjectedVersion = ticket.getInjectedVersion();
        OptionalInt validIV = IntStream.rangeClosed(targetInjectedVersion, ticket.getFixedVersion() - 1)
                                       .filter(v -> isIVValid(ticket, v))
                                       .findFirst();
        if (validIV.isPresent()) {
            ticket.setInjectedVersion(validIV.getAsInt());
            ticket.setAffectedVersion(IntStream.rangeClosed(ticket.getInjectedVersion(), ticket.getOpenVersion() - 1)
                                               .boxed()
                                               .collect(Collectors.toList()));
        } else {
            ticket.setAffectedVersion(Collections.singletonList(0));
            ticket.setInjectedVersion(0);
        }
    }


    private static void handleOVNotEquals1(Ticket ticket) {        

        if (ticket.getFixedVersion() <= ticket.getOpenVersion()) { 
            handleOVLessThanFV(ticket);
        } else { 
            handleOVMoreThanFV(ticket);
        }
    }

    private static boolean isIVValid(Ticket ticket, int injectedVersion) {
        return injectedVersion >= 1 && injectedVersion <= ticket.getOpenVersion() && injectedVersion <= ticket.getFixedVersion();
    }

    public static void createCSV(List<Release> releases, String projectName) {
    	String fileName = DATASET_FILENAME;
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(projectName.toLowerCase() + fileName))) {
            // Creazione del file CSV e scrittura dell'intestazione.
            writer.write("RELEASE,FILENAME,SIZE,LOC_added,MAX_LOC_Added,AVG_LOC_Added,CHURN,MAX_Churn,AVG_Churn,NR,NAUTH,BUGGYNESS\n");

            // Scrittura dei dati relativi a ciascun file per ogni release.
            for (Release release : releases) {
                writeReleaseMetrics(writer, release);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Errore nella creazione del dataset", e);
        }
    }

    private static void writeReleaseMetrics( BufferedWriter fileWriter, Release release) throws IOException {
    		for (JavaFile file : release.getFile()) {
    		update(fileWriter, release, file);
    		}
    	}

   
    private static final Logger logger = Logger.getLogger(MainClass.class.getName());


    private static List<Release> releasesListBookkeeper;
    private static List<Release> releasesListOpenjpa;
    private static List<Ticket> ticketListBookkeeper;
    private static List<Ticket> ticketListOpenjpa;
    private static List<RevCommit> commitListBookkeeper;
    private static List<RevCommit> commitListOpenjpa;
   
    

    public static void main(String[] args) throws IllegalStateException, GitAPIException, IOException, JSONException {
    	
    	
        releasesListBookkeeper = Release.getListRelease(PROJECT);
        releasesListOpenjpa = Release.getListRelease(PROJECT1);
        commitListBookkeeper = getAllCommits(releasesListBookkeeper, Paths.get(percorso + PROJECT.toLowerCase()));
        commitListOpenjpa = getAllCommits(releasesListOpenjpa, Paths.get(percorso + PROJECT1.toLowerCase()));
        ticketListBookkeeper = Ticket.getTickets(releasesListBookkeeper, PROJECT);
        ticketListOpenjpa = Ticket.getTickets(releasesListOpenjpa, PROJECT1);
        
        
        //Bookkeeper
        linkFunction();
        removeReleaseBeforeHalf(releasesListBookkeeper, ticketListBookkeeper);
        checkTicketBookkeeper(); 
        setBuilder(percorso + PROJECT.toLowerCase() + endPath);
        logger.log(Level.INFO, "Numero ticket = {0}.", ticketListBookkeeper.size());
        Collections.reverse(ticketListBookkeeper); 
        findProportion(ticketListBookkeeper);
        checkTicketBookkeeper();  
        getJavaFiles(Paths.get(percorso + PROJECT.toLowerCase()), releasesListBookkeeper);
        isBuggy(releasesListBookkeeper, ticketListBookkeeper); 
        getRepo(releasesListBookkeeper, percorso + PROJECT.toLowerCase() + endPath);
        createCSV(releasesListBookkeeper, PROJECT.toLowerCase());

        
        //openjpa
        linkFunction();
        removeReleaseBeforeHalf(releasesListOpenjpa, ticketListOpenjpa);
        checkTicketOpenjpa(); 
        setBuilder(percorso + PROJECT1.toLowerCase() + endPath);
        logger.log(Level.INFO, "Numero ticket = {0}.", ticketListOpenjpa.size());
        Collections.reverse(ticketListOpenjpa);
        findProportion(ticketListOpenjpa);
        checkTicketOpenjpa(); 
        getJavaFiles(Paths.get(percorso + PROJECT1.toLowerCase()), releasesListOpenjpa);
        isBuggy(releasesListOpenjpa, ticketListOpenjpa); 
        getRepo(releasesListOpenjpa, percorso + PROJECT1.toLowerCase() + endPath);
        createCSV(releasesListOpenjpa, PROJECT1.toLowerCase());
        logger.log(Level.INFO, "Creando il file .Arff");
        
        //csvLoader openjpa
        CSVLoader loaderOpenjpa = new CSVLoader();
        loaderOpenjpa.setSource(new File(URI_OPENJPA));
        Instances dataOpenjpa = loaderOpenjpa.getDataSet();

        // Salva il file ARFF Openjpa
        ArffSaver saverOpenjpa = new ArffSaver();
        saverOpenjpa.setInstances(dataOpenjpa);
        saverOpenjpa.setFile(new File(uriArffOpenjpa));
        saverOpenjpa.writeBatch();
        logger.log(Level.INFO, "File openjpaDataset.Arff creato");
        
        //csvLoader bookkeeper
        CSVLoader loaderBookkeeper = new CSVLoader();
        loaderBookkeeper.setSource(new File(URI_BOOKKEEPER));
        Instances dataBookkeeper = loaderBookkeeper.getDataSet();
        
        
        // Salva il file ARFF bookkeeper
        ArffSaver saverBookkeeper = new ArffSaver();
        saverBookkeeper.setInstances(dataBookkeeper);
        saverBookkeeper.setFile(new File(uriArffBookkeeper));
        saverBookkeeper.writeBatch();
        logger.log(Level.INFO, "File bookkeeperDataset.Arff creato");
        

    
    }
    
  

    public static void getRepo(List<Release> releasesList, String repo) throws IOException {
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
        Repository repository = repositoryBuilder.setGitDir(new File(repo))
            .readEnvironment()
            .findGitDir()
            .setMustExist(true)
            .build();

        analyzeMetrics(releasesList, repository);
    }

    public static void analyzeMetrics(List<Release> releasesList, Repository repository) {
        releasesList.forEach(release -> {
            List<JavaFile> javaFiles = analyzeRelease(release, repository);
            metricsOfFilesByRelease(javaFiles, release);
        });
    }


    private static List<JavaFile> analyzeRelease(Release release, Repository repository) {
        return release.getCommitList().stream()
            .map(commit -> {
                try (DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    diff.setRepository(repository);
                    diff.setDiffComparator(RawTextComparator.DEFAULT);
                    diff.setDetectRenames(true);
                    String authName = commit.getAuthorIdent().getName();
                    List<DiffEntry> diffs = getDiffs(commit);
                    if (diffs != null) {
                        List<JavaFile> javaFiles = new ArrayList<>();
                        diffs.forEach(diffEntry -> {
                            String type = diffEntry.getChangeType().toString();
                            DiffInfo diffInfo = new DiffInfo(diffEntry, type, authName, javaFiles, diff);
                            processDiffEntry(diffInfo);
                        });
                        return javaFiles;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Collections.<JavaFile>emptyList();
            })
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }



    public static long countTouchedClasses(List<DiffEntry> diffs) {
    	return diffs.stream()
    	.filter(diffEntry -> diffEntry.toString().contains(ENDFILE))
    	.count();
    	}
    public static void processDiffEntry(DiffInfo diffInfo) {
        String fileName = null;
        switch (diffInfo.getType()) {
            case "MODIFY":
                fileName = diffInfo.getDiffEntry().getNewPath();
                break;
            case "ADD":
                fileName = diffInfo.getDiffEntry().getNewPath();
                break;
            case DELETE:
                fileName = diffInfo.getDiffEntry().getOldPath();
                break;
            case "RENAME":
                fileName = diffInfo.getDiffEntry().getNewPath();
                break;
            default:
                fileName = null;
                break;
        }
        if (fileName != null && fileName.contains(ENDFILE)) {
            churn(diffInfo.getFileList(), fileName, diffInfo.getAuthName(), diffInfo.getDiffEntry(), diffInfo.getDiff());
        }
    }

//da rivedere
    public static void churn(List<JavaFile> fileList, String fileName, String authName, DiffEntry diffEntry, DiffFormatter diff) {
        try {
            FileHeader fileHeader = diff.toFileHeader(diffEntry);
            int addedLines = 0;
            int deletedLines = 0;
            for (Edit edit : fileHeader.toEditList()) {
                addedLines += edit.getLengthB();
                deletedLines += edit.getLengthA();
            }
            int churn = calculateChurn(addedLines, deletedLines);
            DiffInfo diffInfo = new DiffInfo(diffEntry, diffEntry.getChangeType().toString(), authName, fileList, diff);
            diffInfo.setFileName(fileName);
            fileList(fileList, diffInfo.getFileName(), authName, addedLines, churn);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Errore nel calcolo delle linee aggiunte ed eliminate", e);
        }
    }


    private static int calculateChurn(int addedLines, int deletedLines) {
        int churn = 0;
        if (addedLines > deletedLines) {
            churn = addedLines - deletedLines;
        } else if (deletedLines > addedLines) {
            churn = -(deletedLines - addedLines);
        }
        return churn;
    }


    private static void fileList(List<JavaFile> fileList, String fileName, String authName, int locAdded, int churn) {
        Optional<JavaFile> foundFile = fileList.stream().filter(file -> file.getName().equals(fileName)).findFirst();
        
        if (foundFile.isPresent()) {
            existingFile(foundFile.get(), authName, locAdded, churn);
        } else {
            addNewFile(fileList, fileName, authName, locAdded, churn);
        }
    }



    private static void existingFile(JavaFile file, String authName,  int locAdded, int churn) {
        file.setNr(file.getNr() + 1);
        if (!file.getNAuth().contains(authName)) {
            file.getNAuth().add(authName);
        }
        file.setlinesOfCodeadded(file.getlinesOfCodeadded() + locAdded);
        addToLocAddedList(file, locAdded);
        
        file.setChurn(file.getChurn() + churn);
        addToChurnList(file, churn);
    }

    private static void addNewFile(List<JavaFile> fileList, String fileName, String authName,  int locAdded, int churn) {
        JavaFile javaFile = new JavaFile(fileName);
        setMetrics(javaFile,  locAdded, churn, fileList, authName);
    }
    private static void addToLocAddedList(JavaFile file, int locAdded) {
    	file.getlinesOfCodeAddedList().add(locAdded);
    }

    private static void addToChurnList(JavaFile file, int churn) {
    file.getChurnList().add(churn);
    }


    private static void setNr(JavaFile javaFile) {
        javaFile.setNr(1);
    }
    private static void setNAuth(JavaFile javaFile, String authName) {
        List<String> listAuth = new ArrayList<>();
        listAuth.add(authName);
        javaFile.setNAuth(listAuth);
    }
    
    private static void setLocAdded(JavaFile javaFile, int locAdded) {
        javaFile.setlinesOfCodeadded(locAdded);
        List<Integer> locAddedList = new ArrayList<>();
        locAddedList.add(locAdded);
        javaFile.setlinesOfCodeAddedList(locAddedList);
    }
    private static void setChurn(JavaFile javaFile, int churn) {
        javaFile.setChurn(churn);
        List<Integer> churnList = new ArrayList<>();
        churnList.add(churn);
        javaFile.setChurnList(churnList);
    }
    private static void addToFileList(List<JavaFile> fileList, JavaFile javaFile) {
        fileList.add(javaFile);
    }
    public static void setMetrics(JavaFile javaFile, int locAdded, int churn, List<JavaFile> fileList, String authName) {
        setNr(javaFile);
        setNAuth(javaFile, authName);
        setLocAdded(javaFile, locAdded);
        setChurn(javaFile, churn);
        addToFileList(fileList, javaFile);
    }



    public static void metricsOfFilesByRelease(List<JavaFile> fileList, Release release) {
        fileList.stream()
                .filter(javaFile -> release.getFile().stream().anyMatch(file -> javaFile.getName().equals(file.getName())))
                .forEach(javaFile -> release.getFile().stream()
                        .filter(file -> javaFile.getName().equals(file.getName()))
                        .findFirst()
                        .ifPresent(file -> updateReleaseFileMetrics(javaFile, file)));
    }


    private static void updateReleaseFileMetrics(JavaFile javaFile, JavaFile file) {
        updateLOCadded(javaFile, file);
        updateChurn(javaFile, file);
        updateNAuth(javaFile, file);
        
    }

    private static void updateLOCadded(JavaFile javaFile, JavaFile file) {
        int locAdded = javaFile.getlinesOfCodeadded();
        file.setlinesOfCodeadded(file.getlinesOfCodeadded() + locAdded);
        file.getlinesOfCodeAddedList().addAll(javaFile.getlinesOfCodeAddedList());
    }


    private static void updateChurn(JavaFile javaFile, JavaFile file) {
        int newChurn = javaFile.getChurn();
        int currentChurn = file.getChurn();
        file.setChurn(currentChurn + newChurn);
        List<Integer> newChurnList = javaFile.getChurnList();
        List<Integer> currentChurnList = file.getChurnList();
        currentChurnList.addAll(newChurnList);
        file.setChurnList(currentChurnList);
    }


    private static void updateNAuth(JavaFile javaFile, JavaFile file) {
        int nr = javaFile.getNr();
        file.setNr(file.getNr() + nr);
        
        Set<String> nAuthSet = new HashSet<>(file.getNAuth());
        nAuthSet.addAll(javaFile.getNAuth());
        
        List<String> nAuthList = new ArrayList<>(nAuthSet);
        file.setNAuth(nAuthList);
    }


    

    


    public static int size(TreeWalk treewalk, Repository repository) throws IOException {
        try (ObjectReader reader = repository.newObjectReader()) {
            ObjectLoader loader = reader.open(treewalk.getObjectId(0));
            try (InputStream input = loader.openStream()) {
                BufferedReader readerNew = new BufferedReader(new InputStreamReader(input));
                return (int) readerNew.lines().count();
            }}
        }
        
        private static Repository repository;
        


        

        public static List<RevCommit> getAllCommits(List<Release> releases, Path repositoryPath) throws GitAPIException, IOException {
            try (Repository repository = new FileRepositoryBuilder().setGitDir(repositoryPath.resolve(".git").toFile()).build()) {
                List<RevCommit> commits = new ArrayList<>();
                try (Git git = new Git(repository)) {
                    Iterable<RevCommit> logEntries = git.log().all().call();
                    for (RevCommit commit : logEntries) {
                        commits.add(commit);
                        updateReleaseWithCommit(commit, releases);
                    }
                }
                return commits;
            }
        }


        private static void updateReleaseWithCommit(RevCommit commit, List<Release> releases) {
            Release release = getReleaseForCommit(commit, releases);
            if (release != null) {
                release.addCommit(commit);
            }
        }

        


        private static Release getReleaseForCommit(RevCommit commit, List<Release> releaseList) {
            LocalDateTime commitDate = commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            int releaseIndex = afterBeforeDate(commitDate, releaseList);
            return releaseList.stream()
                              .filter(release -> release.getIndex() == releaseIndex)
                              .findFirst()
                              .orElse(null);
        }



        private static Git initGitRepository(Path repoPath) throws IOException, IllegalStateException, GitAPIException {
            Git git = Git.open(repoPath.toFile());
            Repository repo = git.getRepository();
            if (!repo.getDirectory().exists()) {
                Git.init().setDirectory(repoPath.toFile()).call();
            }
            return git;
        }
//da rivedere
        private static void getJavaFilesForRelease(Git git, Release release) throws IOException {
            List<String> javaFileNames = new ArrayList<>();

            for (RevCommit commit : release.getCommitList()) {
                ObjectId treeId = commit.getTree();
                try (RevWalk revWalk = new RevWalk(git.getRepository())) {
                    RevTree tree = revWalk.parseTree(treeId);

                    try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                        treeWalk.addTree(tree);
                        treeWalk.setRecursive(true);

                        while (treeWalk.next()) {
                            String path = treeWalk.getPathString();

                            if (treeWalk.isSubtree() || !path.endsWith(ENDFILE)) {
                                continue;
                            }

                            addJavaFile(treeWalk, release, javaFileNames);
                        }
                    }
                }
            }
        }


        public static void getJavaFiles(Path repoPath, List<Release> releasesList) throws IOException, IllegalStateException, GitAPIException {
            Git git = initGitRepository(repoPath);

            for (Release release : releasesList) {
                getJavaFilesForRelease(git, release);
                setFileListIfNeeded(release, releasesList);
            }
        }


        private static void setFileListIfNeeded(Release release, List<Release> releasesList) {
            if (!release.getFile().isEmpty()) {
                return;
            }

            int previousReleaseIndex = releasesList.size() - 2;
            if (previousReleaseIndex < 0) {
                return;
            }

            Release previousRelease = releasesList.get(previousReleaseIndex);
            release.setFileList(previousRelease.getFile());
        }


       


//da rivedere
        private static void addJavaFile(TreeWalk treeWalk, Release release, List<String> fileNameList) throws IOException {
            while (treeWalk.next()) {
                if (treeWalk.isSubtree() || !treeWalk.getPathString().endsWith(ENDFILE)) {
                    continue;
                }
                String filename = treeWalk.getPathString();
                if (!fileNameList.contains(filename)) {
                    JavaFile file = new JavaFile(filename);
                    setDefaultJavaFileAttributes(treeWalk, file);
                    release.getFile().add(file);
                    fileNameList.add(filename);
                }
            }
        }




        private static void setDefaultJavaFileAttributes(TreeWalk treeWalk, JavaFile file) throws IOException {
            file.setBuggyness("false");
            file.setNr(0);
            file.setNAuth(new ArrayList<>());
            file.setlinesOfCodeadded(0);
            file.setlinesOfCodeAddedList(new ArrayList<>());
            file.setChurn(0);
            file.setChurnList(new ArrayList<>());
            // Calcola la dimensione del file in linee di codice
            int loc = calculateLinesOfCode(treeWalk);
            file.setlinesOfCode(loc);
        }

        
		private static int calculateLinesOfCode(TreeWalk treeWalk) throws IOException {
        	try (InputStream stream = treeWalk.getObjectReader().open(treeWalk.getObjectId(0)).openStream()) {
        	    return (int) new BufferedReader(new InputStreamReader(stream)).lines().count();
        	}
        }


        public static void isBuggy(List<Release> releases, List<Ticket> tickets)  {
            tickets.forEach(ticket -> {
				try {
					verify(ticket, releases);
				} catch (IOException e) {
					
					e.printStackTrace();
				}
			});
        }


        private static void verify(Ticket ticket, List<Release> releaseList) throws IOException {
            
            ticket.getCommitList().stream()
                .map(commit -> {
                    try {
                        return getDiffs(commit);
                    } catch (IOException e) {
                        
                        e.printStackTrace();
                    }
                    return null;
                })
                .filter(Objects::nonNull)

                .forEach(diffs -> diff(diffs, releaseList));
        }



        private static DiffFormatter initializeDiffFormatter() {
            DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE);
            diff.setRepository(repository);
            diff.setContext(0);
            diff.setDetectRenames(true);
            return diff;
        }
        private static List<DiffEntry> getDiffsBetweenCommits(RevCommit parent, RevCommit commit, DiffFormatter diff) throws IOException {
            return diff.scan(parent.getTree(), commit.getTree());
        }
        private static List<DiffEntry> getDiffsWithEmptyTree(RevCommit commit, DiffFormatter diff) throws IOException {
            try (RevWalk rw = new RevWalk(repository)) {
    			return diff.scan(new EmptyTreeIterator(), new CanonicalTreeParser(null, rw.getObjectReader(), commit.getTree()));
    		}
        }
        public static List<DiffEntry> getDiffs(RevCommit commit) throws IOException {
            DiffFormatter diff = initializeDiffFormatter();
            List<DiffEntry> diffs;
            if (commit.getParentCount() != 0) {
                RevCommit parent = (RevCommit) commit.getParent(0).getId();
                diffs = getDiffsBetweenCommits(parent, commit, diff);
            } else {
                diffs = getDiffsWithEmptyTree(commit, diff);
            }
            return diffs;
        }



        public static void diff(List<DiffEntry> diffs, List<Release> releasesList) {
        	  diffs.stream()
        	       .filter(diff -> isBuggyDiffEntry(diff))
        	       .map(diff -> getFilePathFromDiffEntry(diff))
        	       .forEach(file -> setBuggyness(file, releasesList));
        	}


        private static boolean isBuggyDiffEntry(DiffEntry diff) {
        	  ChangeType changeType = diff.getChangeType();
        	  String path = diff.getNewPath();
        	  return (changeType == ChangeType.MODIFY || changeType == ChangeType.DELETE) && path.endsWith(ENDFILE);
        	}


        private static String getFilePathFromDiffEntry(DiffEntry diff) {
            if (diff.getChangeType() == DiffEntry.ChangeType.DELETE || diff.getChangeType() == DiffEntry.ChangeType.RENAME) {
                return diff.getOldPath();
            } else {
                return diff.getNewPath();
            }
        }


        public static void setBuggyness(String file, List<Release> releasesList) {
            releasesList.forEach(release -> setBuggynessForRelease(file, release));
        }


        private static void setBuggynessForRelease(String file, Release release) {
            release.getFile().stream()
                .filter(javaFile -> javaFile.getName().equals(file))
                .findFirst()
                .ifPresent(javaFile -> javaFile.setBuggyness("true"));
        }


      
        public static void setBuilder(String repo) throws IOException {
            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
            repository = repositoryBuilder.setGitDir(new File(repo)).readEnvironment() // scan environment GIT_* variables
                    .findGitDir() // scan up the file system tree
                    .setMustExist(true).build();
        
    }
        
        public static Integer afterBeforeDate(LocalDateTime date, List<Release> releases) {
            int lastReleaseIndex = releases.size() - 1;
            
            for (int i = 0; i < releases.size(); i++) {
                Release release = releases.get(i);
                LocalDateTime releaseDate = release.getDate();
                            
                if (date.isBefore(releaseDate) || date.isEqual(releaseDate) || i == lastReleaseIndex) {
                    return release.getIndex();
                }
            }

            
            return null;
        }
     //----proportion----
        public static void findProportion(List<Ticket> ticketList) {
            List<Ticket> injectedVersion = new ArrayList<>();
            findInjectedVersion(ticketList, injectedVersion);

            int total = ticketList.size();
            movingWindows = calculatemovingWindows(total);

            List<Ticket> newProportionTicket = new ArrayList<>();
            processTicketList(ticketList, injectedVersion, newProportionTicket);
        }

        private static void findInjectedVersion(List<Ticket> ticketList, List<Ticket> injectedVersion) {
            ticketList.stream()
                .filter(ticket -> ticket.getOpenVersion().equals(ticket.getFixedVersion()) && ticket.getInjectedVersion() == 0)
                .forEach(ticket -> {
                    ticket.setInjectedVersion(ticket.getFixedVersion());
                    injectedVersion.add(ticket);
                });
    }


        private static int calculatemovingWindows(int total) {
            return total / 100;
        }

        private static void processTicketList(List<Ticket> ticketList, List<Ticket> injectedVersion, List<Ticket> newProportionTicket) {
            ticketList.stream()
                     .filter(ticket -> !injectedVersion.contains(ticket))
                     .forEach(ticket -> {
                         if (ticket.getInjectedVersion() != 0) {
                             movingWindows(newProportionTicket, ticket);
                         } else {
                             injectedProportion(newProportionTicket, ticket);
                         }
                     });
        }


        public static void movingWindows(List<Ticket> movingWindow, Ticket ticket) {
            movingWindow.add(ticket);
            movingWindow.removeIf(t -> movingWindow.size() > movingWindows);
        }


        public static void injectedProportion(List<Ticket> newProportionTicket, Ticket ticket) {
            float p = calculateP(newProportionTicket);
            int avgPFloor = calculateAvgPFloor(p);
            int predictedIv = calculatePredictedIv(ticket, avgPFloor);
            ticket.setInjectedVersion(Math.min(predictedIv, ticket.getOpenVersion()));
        }

        private static float calculateP(List<Ticket> newProportionTicket) {
            return newProportionTicket.stream()
                                      .map(ticket -> obtainingP(ticket))
                                      .reduce(0f, (a, b) -> a + b);
        }



        private static int calculateAvgPFloor(float p) {
            return (int) Math.floor(p / movingWindows);
        }

        private static int calculatePredictedIv(Ticket ticket, int avgPFloor) {
            int fv = ticket.getFixedVersion();
            int ov = ticket.getOpenVersion();
            return fv == ov ? ov : (int) (fv - (fv - ov) * avgPFloor);
        }



        public static void injectedProportion1(List<Ticket> newProportionTicket,Ticket ticket){
            float p = calculateP(newProportionTicket);
            int avgPFloor = calculateAverageIV(p);
            int fv = ticket.getFixedVersion();
            int ov = ticket.getOpenVersion();
            int predictedIv = fv-(fv-ov)*avgPFloor;
            ticket.setInjectedVersion(Math.min(predictedIv, ov));
        }


        private static int calculateAverageIV(float p) {
            return (int)Math.floor(p/movingWindows);
        }

        /**
         * Calcola la proporzione P per un Ticket.
         * @param ticket il Ticket per cui calcolare la proporzione
         * @return la proporzione P calcolata
         */
        private static float obtainingP(Ticket ticket) {
            final float fv = ticket.getFixedVersion();
            final float ov = ticket.getOpenVersion();
            final float iv = ticket.getInjectedVersion();
            
            return Float.compare(fv, ov) == 0 ? 0f : (fv - iv) / (fv - ov);
        }









}