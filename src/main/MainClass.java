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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class MainClass {
	
	
	
	
	private static final String ENDFILE = ".java";
	private static final String DELETE = "DELETE";
	
	private static void update(BufferedWriter fileWriter, Release release, JavaFile file) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(release.getIndex()).append(",");
		sb.append(file.getName()).append(",");
		sb.append(file.getlinesOfCode()).append(",");
		sb.append(file.getlinesOfCodeadded()).append(",");
		writeMaxAndAvg(sb, file.getlinesOfCodeAddedList());
		sb.append(",");
		sb.append(file.getChurn()).append(",");
		writeMaxAndAvg(sb, file.getChurnList());
		sb.append(",");
		sb.append(file.getNr()).append(",");
		sb.append(file.getNAuth().size()).append(",");
		sb.append(file.getBuggyness()).append("\n");
		fileWriter.write(sb.toString());
		fileWriter.flush();
		}

	private static void writeMaxAndAvg(StringBuilder sb, List<Integer> list) {
		if (list.isEmpty()) {
			sb.append("0,0");
			return;
		}
		int max = Collections.max(list);
			double avg = list.stream().mapToInt(Integer::intValue).average().orElse(0.0);
			sb.append(max).append(",").append((int)avg);
		}

    

    private static void linkFunction() {
    	linkage();
        setResolutionDateAndFVBookkeeper();
        setResolutionDateAndFVOpenjpa();
        removeUnlinkedTickets();
    }

    private static LocalDateTime getCommitDate(RevCommit commit) {
        return commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
    private static void addCommitToTicket(Ticket ticket, RevCommit commit) {
        ticket.getCommitList().add(commit);
    }
    /**
     * Returns a list of LocalDateTime objects representing the dates of all commits related to the specified ticket.
     * If no commits are found, an empty list is returned.
     */
    private static ArrayList<LocalDateTime> findCommitDatesForTicket(Ticket ticket, List<RevCommit> commitList) {
        ArrayList<LocalDateTime> commitDateList = new ArrayList<>();

        String ticketID = ticket.getID();
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

            if (!commitDateList.isEmpty()) {
                LocalDateTime resolutionDate = commitDateList.get(commitDateList.size() - 1);
                ticket.setResolutionDate(resolutionDate);
                ticket.setFV(afterBeforeDate(resolutionDate, releasesListBookkeeper));
                
            }
        }
    }
    private static void setResolutionDateAndFVOpenjpa() {
        for (Ticket ticket : ticketListOpenjpa) {
            ArrayList<LocalDateTime> commitDateList = ticket.getCommitList().stream()
                    .map(commit -> commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));

            if (!commitDateList.isEmpty()) {
                LocalDateTime resolutionDate = commitDateList.get(commitDateList.size() - 1);
                ticket.setResolutionDate(resolutionDate);
                
                ticket.setFV(afterBeforeDate(resolutionDate, releasesListOpenjpa));
            }
        }
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
        removeUnresolvedTicketsAndSetAVResolution(halfRelease, ticketList);
    }

    private static void removeReleasesAfterHalfPoint(List<Release> release, int releaseNew) {
        Iterator<Release> i = release.iterator();
        while (i.hasNext()) {
            Release s = i.next();
            if (s.getIndex() > releaseNew) {
                i.remove();
            }
        }
    }

    private static void removeUnresolvedTicketsAndSetAVResolution(int releaseNew, List<Ticket> ticketNew) {
        Iterator<Ticket> ticket = ticketNew.iterator();
        while (ticket.hasNext()) {
            Ticket t = ticket.next();
            if (t.getResolutionDate() == null || t.getFV() > releaseNew) {
                ticket.remove();
            }
        }
    }

    public static void affectedVersion(int release, List<Ticket> ticket) {
        removeTickets(release, ticket);
        setAVTicketsBookkeeper(release);
        setAVTicketsOpenjpa(release);
    }

    private static void removeTickets(int release, List<Ticket> ticket) {
        Iterator<Ticket> iterator = ticket.iterator();
        while (iterator.hasNext()) {
            Ticket t = iterator.next();
            if (t.getIV() > release) {
                iterator.remove();
            }
        }
    }

    private static void setAVTicketsBookkeeper(int release) {
        for (Ticket t : ticketListBookkeeper) {
            if (t.getOV() > release || t.getFV() > release) {
                List<Integer> affectedVersion = new ArrayList<>();
                for (int k = t.getIV(); k < release; k++) {
                    affectedVersion.add(k);
                }
                t.setAV(affectedVersion);
            }
        }
    }
    
    private static void setAVTicketsOpenjpa(int release) {
        for (Ticket t : ticketListOpenjpa) {
            if (t.getOV() > release || t.getFV() > release) {
                List<Integer> affectedVersion = new ArrayList<>();
                for (int k = t.getIV(); k < release; k++) {
                    affectedVersion.add(k);
                }
                t.setAV(affectedVersion);
            }
        }
    }

    public static void checkTicketBookkeeper() {
        for (Ticket ticket : ticketListBookkeeper) {
            if (ticket.getIV() != 0) {
                if (isTimeOrderCorrect(ticket)) {
                    ticket.getAV().clear();
                    for (int i = ticket.getIV(); i < ticket.getFV(); i++) {
                        ticket.getAV().add(i);
                    }
                } else {
                    setErrorTicket(ticket);
                }
                handleOV(ticket);
            }
        }
    }
    
    public static void checkTicketOpenjpa() {
        for (Ticket ticket : ticketListOpenjpa) {
            if (ticket.getIV() != 0) {
                if (isTimeOrderCorrect(ticket)) {
                    ticket.getAV().clear();
                    for (int i = ticket.getIV(); i < ticket.getFV(); i++) {
                        ticket.getAV().add(i);
                    }
                } else {
                    setErrorTicket(ticket);
                }
                handleOV(ticket);
            }
        }
    }

    private static void handleOV(Ticket ticket) {
        if (ticket.getOV() == 1) {
            handleOVEquals1(ticket);
        } else {
            handleOVNotEquals1(ticket);
        }
    }



    private static boolean isTimeOrderCorrect(Ticket ticket) {
        return ticket.getFV() > ticket.getIV() && ticket.getOV() >= ticket.getIV();
    }


    private static void setErrorTicket(Ticket ticket) {
        ticket.setIV(0); //setto come errore
        ticket.getAV().clear();
        ticket.getAV().add(0);

        if (ticket.getFV().equals(ticket.getIV())) { //se FV = IV -> AV vuota. (caso 'base')
            ticket.getAV().clear();
            ticket.getAV().add(0);
        }
    }



    private static void handleOVEquals1(Ticket ticket) {
        ticket.getAV().clear();
        ticket.setIV(1);

        if (ticket.getFV() == 1) {
            // if OV and FV are both 1, don't add any AVs
        } else {
            // add all releases from IV to FV-1 to AV
            for (int i = ticket.getIV(); i < ticket.getFV(); i++) {
                ticket.getAV().add(i);
            }
        }
    }

    private static void handleOVLessThanFV(Ticket ticket) {
    	ticket.getAV().clear(); // Svuoto la lista di AV per poi aggiornarla con i valori corretti
    	ticket.setIV(ticket.getOV()); // IV = OV
        if (ticket.getFV() > ticket.getOV()) { // Se FV > OV allora ci sono delle versioni AV
            for (int i = ticket.getIV(); i < ticket.getFV(); i++) { // Assegno ad AV tutte le release da IV ad FV
                ticket.getAV().add(i);
            }
        }
    }

    private static void handleOVMoreThanFV(Ticket ticket) {
        int targetIV = ticket.getIV();
        while (targetIV <= ticket.getOV() && targetIV < ticket.getFV()) { // Scorro tutte le versioni fino a OV o FV, la prima che incontro diventa IV
            if (isIVValid(ticket, targetIV)) {
                ticket.setIV(targetIV);
                break;
            }
            targetIV++;
        }

        if (ticket.getIV().equals(ticket.getOV())) { 
            
            ticket.getAV().clear();
        } else { // Altrimenti assegno ad AV tutte le release da IV ad OV
            ticket.getAV().clear();
            for (int i = ticket.getIV() - 1; i < ticket.getOV(); i++) {
                ticket.getAV().add(i);
            }
        }
    }

    private static void handleOVNotEquals1(Ticket ticket) {
        // handle OV > 1 case
        

        if (ticket.getFV() <= ticket.getOV()) { // Condizione caso base, in cui FV <= OV
            handleOVLessThanFV(ticket);
        } else { // Caso in cui OV < FV
            handleOVMoreThanFV(ticket);
        }
    }

    private static boolean isIVValid(Ticket ticket, int injectedVersion) {
        return injectedVersion >= 1 && injectedVersion <= ticket.getOV() && injectedVersion <= ticket.getFV();
    }

    public static void createCSV(List<Release> releases, String projectName) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(projectName.toLowerCase() + "Dataset.csv"))) {
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
    public static final String PROJECT = "BOOKKEEPER";
    public static final String PROJECT1 = "OPENJPA";


    public static void main(String[] args) throws IllegalStateException, GitAPIException, IOException, JSONException {
    	
    	String nameGit = "git";
    	String endPath = "/." + nameGit ;
    	
    	String name = "giuliamenichini";
    	String percorso = "/Users/" + name + "/";
    	String uri = "/Users/giuliamenichini/eclipse-workspace/ISW2/openjpaDataset.csv";
    	String uriArff = "/Users/giuliamenichini/eclipse-workspace/ISW2/openjpaDataset.arff";

        releasesListBookkeeper = RetrieveJira.getListRelease(PROJECT);
        releasesListOpenjpa = RetrieveJira.getListRelease(PROJECT1);
        commitListBookkeeper = getAllCommits(releasesListBookkeeper, Paths.get(percorso + PROJECT.toLowerCase()));
        commitListOpenjpa = getAllCommits(releasesListOpenjpa, Paths.get(percorso + PROJECT1.toLowerCase()));
        ticketListBookkeeper = RetrieveJira.getTickets(releasesListBookkeeper, PROJECT);
        ticketListOpenjpa = RetrieveJira.getTickets(releasesListOpenjpa, PROJECT1);
        
        
        //Bookkeeper
        linkFunction();
        removeReleaseBeforeHalf(releasesListBookkeeper, ticketListBookkeeper);
        checkTicketBookkeeper(); 
        setBuilder(percorso + PROJECT.toLowerCase() + endPath);
        logger.log(Level.INFO, "Numero ticket = {0}.", ticketListBookkeeper.size());
        Collections.reverse(ticketListBookkeeper); 
        Proportion.findProportion(ticketListBookkeeper);
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
        Proportion.findProportion(ticketListOpenjpa);
        checkTicketOpenjpa(); 
        getJavaFiles(Paths.get(percorso + PROJECT1.toLowerCase()), releasesListOpenjpa);
        isBuggy(releasesListOpenjpa, ticketListOpenjpa); 
        getRepo(releasesListOpenjpa, percorso + PROJECT1.toLowerCase() + endPath);
        createCSV(releasesListOpenjpa, PROJECT1.toLowerCase());
        logger.log(Level.INFO, "Creando il file .Arff");
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(uri));
        Instances data = loader.getDataSet();

        // Salva il file ARFF
        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(new File(uriArff));
        saver.writeBatch();
        logger.log(Level.INFO, "File .Arff creato");

    
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
    	for (Release release : releasesList) {
    	List<JavaFile> filesJavaListPerRelease = analyzeRelease(release, repository);
    	metricsOfFilesByRelease(filesJavaListPerRelease, release);
    	}
    }

    private static List<JavaFile> analyzeRelease(Release release, Repository repository) {
    	List<JavaFile> filesJavaListPerRelease = new ArrayList<>();
    	for (RevCommit commit : release.getCommitList()) {
    	try (DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
	    	diff.setRepository(repository);
	    	diff.setDiffComparator(RawTextComparator.DEFAULT);
	    	diff.setDetectRenames(true);
	    	String authName = commit.getAuthorIdent().getName();
	    	List<DiffEntry> diffs = getDiffs(commit);
    	if (diffs != null) {
	    	analyzeDiffEntryMetrics(diffs, filesJavaListPerRelease, authName, diff);
	    	}
	    } catch (IOException e) {
	    	e.printStackTrace();
	    }
	   }
    		return filesJavaListPerRelease;
    }




    public static long countTouchedClasses(List<DiffEntry> diffs) {
    	return diffs.stream()
    	.filter(diffEntry -> diffEntry.toString().contains(ENDFILE))
    	.count();
    	}
    public static void processDiffEntry(DiffEntry diffEntry, String type, String authName, List<JavaFile> fileList, DiffFormatter diff) {
    	String fileName = null;
    	switch (type) {
    		case "MODIFY":
    			fileName = diffEntry.getNewPath();
    			break;
    		case "ADD":
    			fileName = diffEntry.getNewPath();
    			break;
    		case DELETE:
    			fileName = diffEntry.getOldPath();
    			break;
    		case "RENAME":
    			fileName = diffEntry.getNewPath();
    			break;
    		
		    default:
		        fileName = null;
		        break; }
    		if (fileName != null && fileName.contains(ENDFILE)) {
    			churn(fileList, fileName, authName, diffEntry, diff);
    		}
    	}


    public static void analyzeDiffEntryMetrics(List<DiffEntry> diffs, List<JavaFile> fileList, String authName, DiffFormatter diff) {
    	diffs.forEach(diffEntry -> {
    	String type = diffEntry.getChangeType().toString();
    	processDiffEntry(diffEntry, type, authName, fileList, diff);
    	});
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
            fileList(fileList, fileName, authName, addedLines, churn);
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
        applyMetricsNewFile(javaFile,  locAdded, churn, fileList, authName);
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
    public static void applyMetricsNewFile(JavaFile javaFile, int locAdded, int churn, List<JavaFile> fileList, String authName) {
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
            // Ottieni il nome del file
            String filename = treeWalk.getPathString();
            // Se il file ha estensione .java e non è già stato aggiunto alla release
            if (filename.endsWith(ENDFILE) && !fileNameList.contains(filename)) {
                    // Crea una nuova istanza di JavaFile con il nome del file
                    JavaFile file = new JavaFile(filename);
                    // Imposta gli attributi di default per il nuovo file
                    setDefaultJavaFileAttributes(treeWalk, file);
                    // Aggiungi il file alla release
                    release.getFile().add(file);
                    // Aggiungi il nome del file alla lista dei file
                    fileNameList.add(filename);
            }
    }


        private static void setDefaultJavaFileAttributes(TreeWalk treeWalk, JavaFile file) throws IOException {
            // Imposta gli attributi di default per un nuovo file Java
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
            int loc = 0;
            try (InputStream stream = treeWalk.getObjectReader().open(treeWalk.getObjectId(0)).openStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    loc++;
                }
            }
            return loc;
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


        //utility di setup
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

}