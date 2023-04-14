
package main;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class MainClass {
	
	private static final String ENDFILE = ".java";
	private static final String DELETE = "DELETE";
	
	private static void update(FileWriter fileWriter, Release release, JavaFile file) throws IOException {
        fileWriter.append(release.getIndex().toString());
        fileWriter.append(",");
        fileWriter.append(file.getName()); 
        fileWriter.append(",");
        fileWriter.append(file.getLOC().toString()); 
        fileWriter.append(",");
        fileWriter.append(file.getLOCadded().toString()); 
        fileWriter.append(",");
        writeMaxAndAvgLOCAdded(fileWriter, file); 
        fileWriter.append(",");
        fileWriter.append(file.getChurn().toString());
        fileWriter.append(",");
        writeMaxAndAvgChurn(fileWriter, file); 
        fileWriter.append(",");
        fileWriter.append(file.getNr().toString());
        fileWriter.append(",");
        fileWriter.append(String.valueOf(file.getNAuth().size()));
        fileWriter.append(",");
        fileWriter.append(file.getBugg());
        fileWriter.append("\n");
        fileWriter.flush();
    }

    private static void writeMaxAndAvgLOCAdded(FileWriter fileWriter, JavaFile file) throws IOException {
        if (file.getLOCadded().equals(0)) { //se non ho aggiunto nulla niente max e avg
            fileWriter.append("0");
            fileWriter.append(",");
            fileWriter.append("0");
        } else {
            int maxLocAdded = Collections.max((file.getLocAddedList())); //prendo il max dalla lista
            fileWriter.append(String.valueOf(maxLocAdded)); //scrivo tale massimo
            fileWriter.append(",");
            int avgLocAdded = (int)file.getLocAddedList().stream().mapToInt(Integer::intValue).average().orElse(0.0); //easy way to avg
            fileWriter.append(String.valueOf(avgLocAdded));
        }
    }

    private static void writeMaxAndAvgChurn(FileWriter fileWriter, JavaFile file) throws IOException {
        if (file.getChurn().equals(0)) {
            fileWriter.append("0");
            fileWriter.append(",");
            fileWriter.append("0");
        } else {
            int maxChurn = Collections.max((file.getChurnList()));
            fileWriter.append(String.valueOf(maxChurn));
            fileWriter.append(",");
            int avgChurn = (int) file.getChurnList().stream().mapToInt(Integer::intValue).average().orElse(0.0); //easy way
            fileWriter.append(String.valueOf(avgChurn));
        }
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
    private static ArrayList<LocalDateTime> findCommitDatesForTicket(Ticket ticket, List<RevCommit> commitList) {
        ArrayList<LocalDateTime> commitDateList = new ArrayList<>();
        String ticketID = ticket.getID();

        for (RevCommit commit : commitList) {
            String message = commit.getFullMessage();

            if (foundCom(message, ticketID)) {
                LocalDateTime commitDate = getCommitDate(commit);
                commitDateList.add(commitDate);
                addCommitToTicket(ticket, commit);
            }
        }

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
                ticket.setFV(RetrieveJira.compareDateVersion(resolutionDate, releasesListBookkeeper));
                
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
                
                ticket.setFV(RetrieveJira.compareDateVersion(resolutionDate, releasesListOpenjpa));
            }
        }
    }

    private static void removeUnlinkedTickets() {
        ticketListBookkeeper.removeIf(ticket -> ticket.getResolutionDate() == null);
        ticketListOpenjpa.removeIf(ticket -> ticket.getResolutionDate() == null);
    }


    private static boolean foundCom(String message, String ticketID) {
        // Verifica se il ticketID è presente nella stringa del messaggio di commit.
        String regex = "(^|[^A-Za-z0-9])" + ticketID + "([^A-Za-z0-9]|$)";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message);

        return matcher.find();
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

        if (ticket.getIV() == ticket.getOV()) { // Se IV = OV allora non ci sono versioni AV
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

    	

    	try (FileWriter fileWriter = new FileWriter(projectName.toLowerCase() + "Dataset.csv")) {
    	    // Creazione del file CSV e scrittura dell'intestazione.
    	    fileWriter.append("RELEASE,FILENAME,SIZE,LOC_added,MAX_LOC_Added,AVG_LOC_Added,CHURN,MAX_Churn,AVG_Churn,NR,NAUTH,BUGGYNESS\n");

    	    // Scrittura dei dati relativi a ciascun file per ogni release.
    	    for (Release release : releases) {
    	        writeReleaseMetrics(fileWriter, release);
    	    }
    	} catch (IOException e) {
    	    logger.log(Level.SEVERE, "Errore nella creazione del dataset", e);
    	}
    }
    private static void writeReleaseMetrics(FileWriter fileWriter, Release release) throws IOException {
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
        Proportion.proportion(ticketListBookkeeper);
        checkTicketBookkeeper();  
        getJavaFiles(Paths.get(percorso + PROJECT.toLowerCase()), releasesListBookkeeper);
        checkBuggyness(releasesListBookkeeper, ticketListBookkeeper); 
        getRepo(releasesListBookkeeper, percorso + PROJECT.toLowerCase() + endPath);
        createCSV(releasesListBookkeeper, PROJECT.toLowerCase());

        
        //openjpa
        linkFunction();
        removeReleaseBeforeHalf(releasesListOpenjpa, ticketListOpenjpa);
        checkTicketOpenjpa(); 
        setBuilder(percorso + PROJECT1.toLowerCase() + endPath);
        logger.log(Level.INFO, "Numero ticket = {0}.", ticketListOpenjpa.size());
        Collections.reverse(ticketListOpenjpa);
        Proportion.proportion(ticketListOpenjpa);
        checkTicketOpenjpa(); 
        getJavaFiles(Paths.get(percorso + PROJECT1.toLowerCase()), releasesListOpenjpa);
        checkBuggyness(releasesListOpenjpa, ticketListOpenjpa); 
        getRepo(releasesListOpenjpa, percorso + PROJECT1.toLowerCase() + endPath);
        createCSV(releasesListOpenjpa, PROJECT1.toLowerCase());

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

    public static void analyzeMetrics(List<Release> releasesList, Repository repository) throws IOException {
        releasesList.forEach(release -> {
            List<JavaFile> filesJavaListPerRelease = new ArrayList<>();
            release.getCommitList().forEach(commit -> {
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
            });
            metricsOfFilesByRelease(filesJavaListPerRelease, release);
        });
    }




    public static int countTouchedClasses(List<DiffEntry> diffs) {
        int numTouchedClass = 0;
        for (DiffEntry diffEntry : diffs) {
            if (diffEntry.toString().contains(ENDFILE)) {
                numTouchedClass++;
            }
        }
        return numTouchedClass;
    }
    public static void processDiffEntry(DiffEntry diffEntry, String type, String authName, List<JavaFile> fileList, int numTouchedClass, DiffFormatter diff) {
        if (diffEntry.toString().contains(ENDFILE) && type.equals("MODIFY") || type.equals(DELETE) || type.equals("ADD") || type.equals("RENAME")) {
            String fileName;
            if (type.equals(DELETE) || type.equals("RENAME")) fileName = diffEntry.getOldPath();
            else fileName = diffEntry.getNewPath();
            calculateMetrics(fileList, fileName, authName,diffEntry, diff);
        }
    }
    public static void analyzeDiffEntryMetrics(List<DiffEntry> diffs, List<JavaFile> fileList, String authName, DiffFormatter diff) {
        int numTouchedClass = countTouchedClasses(diffs);
        for (DiffEntry diffEntry : diffs) {
            String type = diffEntry.getChangeType().toString();
            processDiffEntry(diffEntry, type, authName, fileList, numTouchedClass, diff);
        }
    }


    public static void calculateMetrics(List<JavaFile> fileList, String fileName, String authName, DiffEntry diffEntry, DiffFormatter diff) {
        int locAdded = 0;
        int locDeleted = 0;
        try {
            for (Edit edit : diff.toFileHeader(diffEntry).toEditList()) {
                locAdded += edit.getEndB() - edit.getBeginB();
                locDeleted += edit.getEndA() - edit.getBeginA();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error in CalculateMetrics");
        }
        int churn = calculateChurn(locAdded, locDeleted);
        fileList(fileList, fileName, authName,  locAdded, churn);
    }

    private static int calculateChurn(int locAdded, int locDeleted) {
        return locAdded - locDeleted;
    }

    private static void fileList(List<JavaFile> fileList, String fileName, String authName, int locAdded, int churn) {
        JavaFile file = findFile(fileList, fileName);
        if (file != null) {
            existingFile(file, authName, locAdded, churn);
        } else {
            addNewFile(fileList, fileName, authName, locAdded, churn);
        }
    }

    private static JavaFile findFile(List<JavaFile> fileList, String fileName) {
        for (JavaFile file : fileList) {
            if (file.getName().equals(fileName)) {
                return file;
            }
        }
        return null;
    }

    private static void existingFile(JavaFile file, String authName,  int locAdded, int churn) {
        file.setNr(file.getNr() + 1);
        if (!file.getNAuth().contains(authName)) {
            file.getNAuth().add(authName);
        }
        file.setLOCadded(file.getLOCadded() + locAdded);
        addToLocAddedList(file, locAdded);
        
        file.setChurn(file.getChurn() + churn);
        addToChurnList(file, churn);
    }

    private static void addNewFile(List<JavaFile> fileList, String fileName, String authName,  int locAdded, int churn) {
        JavaFile javaFile = new JavaFile(fileName);
        applyMetricsNewFile(javaFile,  locAdded, churn, fileList, authName);
    }
    private static void addToLocAddedList(JavaFile file, int locAdded) {
    	file.getLocAddedList().add(locAdded);
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
        javaFile.setLOCadded(locAdded);
        List<Integer> locAddedList = new ArrayList<>();
        locAddedList.add(locAdded);
        javaFile.setLocAddedList(locAddedList);
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
        for (JavaFile javaFile : fileList) {
            for (JavaFile fileInRelease : release.getFile()) {
                if (javaFile.getName().equals(fileInRelease.getName())) {
                    // aggiorna le metriche del file associato alla release con quelle del file corrente
                    updateReleaseFileMetrics(javaFile, fileInRelease);
                }
            }
        }
    }

    private static void updateReleaseFileMetrics(JavaFile javaFile, JavaFile fileInRelease) {
        updateLOCadded(javaFile, fileInRelease);
        updateChurn(javaFile, fileInRelease);
        updateNAuth(javaFile, fileInRelease);
        
    }

    private static void updateLOCadded(JavaFile javaFile, JavaFile fileInRelease) {
        int locAdded = javaFile.getLOCadded();
        fileInRelease.setLOCadded(fileInRelease.getLOCadded() + locAdded);
        List<Integer> locAddedList = fileInRelease.getLocAddedList();
        locAddedList.addAll(javaFile.getLocAddedList());
        fileInRelease.setLocAddedList(locAddedList);
    }

    private static void updateChurn(JavaFile javaFile, JavaFile fileInRelease) {
        int churn = javaFile.getChurn();
        fileInRelease.setChurn(fileInRelease.getChurn() + churn);
        List<Integer> churnList = fileInRelease.getChurnList();
        churnList.addAll(javaFile.getChurnList());
        fileInRelease.setChurnList(churnList);
    }

    private static void updateNAuth(JavaFile javaFile, JavaFile fileInRelease) {
        int nr = javaFile.getNr();
        fileInRelease.setNr(fileInRelease.getNr() + nr);
        List<String> nAuth = javaFile.getNAuth();
        List<String> nAuthList = fileInRelease.getNAuth();
        nAuthList.addAll(nAuth);
        nAuthList = nAuthList.stream().distinct().collect(Collectors.toList());
        fileInRelease.setNAuth(nAuthList);
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
            List<RevCommit> commits = new ArrayList<>();
            try (Git git = Git.open(repositoryPath.toFile())) {
                Iterable<RevCommit> logEntries = git.log().all().call();
                for (RevCommit commit : logEntries) {
                    commits.add(commit);
                    updateReleaseWithCommit(commit, releases);
                }
            }
            return commits;
        }

        private static void updateReleaseWithCommit(RevCommit commit, List<Release> releases) {
            Release release = getReleaseForCommit(commit, releases);
            if (release != null) {
                release.addCommit(commit);
            }
        }

        


        private static Release getReleaseForCommit(RevCommit commit, List<Release> releaseList) {
            LocalDateTime commitDate = commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            int releaseIndex = RetrieveJira.compareDateVersion(commitDate, releaseList);
            return releaseList.stream()
                              .filter(release -> release.getIndex() == releaseIndex)
                              .findFirst()
                              .orElse(null);
        }



        private static Git initGitRepository(Path repoPath) throws IOException, IllegalStateException, GitAPIException {
            return Git.init().setDirectory(repoPath.toFile()).call();
        }
        private static void getJavaFilesForRelease(Git git, Release release) throws IOException {
            List<String> javaFileNames = new ArrayList<>();
            for (RevCommit commit : release.getCommitList()) {
                ObjectId treeId = commit.getTree();
                try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                    treeWalk.reset(treeId);
                    treeWalk.setRecursive(true);

                    while (treeWalk.next()) {
                        addJavaFile(treeWalk, release, javaFileNames);
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
            if (release.getFile().isEmpty() && !releasesList.isEmpty()) {
                Release previousRelease = releasesList.get(releasesList.size() - 1);
                release.setFileList(previousRelease.getFile());
            }
        }

       



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
            file.setBugg("false");
            file.setNr(0);
            file.setNAuth(new ArrayList<>());
            file.setLOCadded(0);
            file.setLocAddedList(new ArrayList<>());
            file.setChurn(0);
            file.setChurnList(new ArrayList<>());
            // Imposta la dimensione del file in linee di codice
            int loc = MainClass.size(treeWalk, repository);
            file.setLOC(loc);
        }



        public static void checkBuggyness(List<Release> releaseList, List<Ticket> ticketList) throws IOException {
            for (Ticket ticket : ticketList) {
                verify(ticket, releaseList);
            }
        }

        private static void verify(Ticket ticket, List<Release> releaseList) throws IOException {
            List<Integer> av = ticket.getAV();
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

                .forEach(diffs -> diff(diffs, releaseList, av));
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



        public static void diff(List<DiffEntry> diffs, List<Release> releasesList, List<Integer> av) {
            for (DiffEntry diff : diffs) {
                if (isBuggyDiffEntry(diff)) {
                    String file = getFilePathFromDiffEntry(diff);
                    setBuggyness(file, releasesList, av);
                }
            }
        }

        private static boolean isBuggyDiffEntry(DiffEntry diff) {
            String type = diff.getChangeType().toString();
            return diff.toString().contains(ENDFILE) && (type.equals("MODIFY") || type.equals(DELETE));
        }

        private static String getFilePathFromDiffEntry(DiffEntry diff) {
            if (diff.getChangeType() == DiffEntry.ChangeType.DELETE || diff.getChangeType() == DiffEntry.ChangeType.RENAME) {
                return diff.getOldPath();
            } else {
                return diff.getNewPath();
            }
        }


        public static void setBuggyness(String file, List<Release> releasesList, List<Integer> av) {
            for (Release release : releasesList) {
                setBuggynessForRelease(file, release, av);
            }
        }

        private static void setBuggynessForRelease(String file, Release release, List<Integer> av) {
            if (av.contains(release.getIndex())) {
                setBuggynessForJavaFile(file, release.getFile());
            }
        }

        private static void setBuggynessForJavaFile(String file, List<JavaFile> javaFiles) {
            for (JavaFile javaFile : javaFiles) {
                if (javaFile.getName().equals(file)) {
                    javaFile.setBugg("true");
                }
            }
        }


        /*Ticket mi dice che c'è un bug, questo bug ha toccato le release x,y,z. Ricordo che prendo tutti ticket risolti, ovvero so la release in qui li ho fixati.
        Il ticket include dei commit, i quali vanno a modificare dei file.java. Li modifico perchè quei file=classi hanno dei problemi, e li hanno dalla release x.
        Allora il file.java nelle release x,y,z avevano problemi, ovvero erano buggy.*/

        //utility di setup
        public static void setBuilder(String repo) throws IOException {
            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
            repository = repositoryBuilder.setGitDir(new File(repo)).readEnvironment() // scan environment GIT_* variables
                    .findGitDir() // scan up the file system tree
                    .setMustExist(true).build();
        
    }
}