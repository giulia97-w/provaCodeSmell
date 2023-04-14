
package main;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import org.json.JSONException;

import java.io.FileWriter;
import java.io.IOException;
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
    	String percorso = "/Users/giuliamenichini/";
        releasesListBookkeeper = RetrieveJira.getListRelease(PROJECT);
        releasesListOpenjpa = RetrieveJira.getListRelease(PROJECT1);
        commitListBookkeeper = RetrieveGit.getAllCommit(releasesListBookkeeper, Paths.get(percorso + PROJECT.toLowerCase()));
        commitListOpenjpa = RetrieveGit.getAllCommit(releasesListOpenjpa, Paths.get(percorso + PROJECT1.toLowerCase()));
        ticketListBookkeeper = RetrieveJira.getTickets(releasesListBookkeeper, PROJECT);
        ticketListOpenjpa = RetrieveJira.getTickets(releasesListOpenjpa, PROJECT1);
        
        
        //Bookkeeper
        linkFunction();
        removeReleaseBeforeHalf(releasesListBookkeeper, ticketListBookkeeper);
        checkTicketBookkeeper(); 
        RetrieveGit.setBuilder(percorso + PROJECT.toLowerCase() + "/.git");
        logger.log(Level.INFO, "Numero ticket = {0}.", ticketListBookkeeper.size());
        Collections.reverse(ticketListBookkeeper); 
        Proportion.proportion(ticketListBookkeeper);
        checkTicketBookkeeper();  
        RetrieveGit.getJavaFiles(Paths.get(percorso + PROJECT.toLowerCase()), releasesListBookkeeper);
        RetrieveGit.checkBuggyness(releasesListBookkeeper, ticketListBookkeeper); 
        Metrics.getMetrics(releasesListBookkeeper, percorso + PROJECT.toLowerCase() + "/.git");
        createCSV(releasesListBookkeeper, PROJECT.toLowerCase());

        
        //openjpa
        linkFunction();
        removeReleaseBeforeHalf(releasesListOpenjpa, ticketListOpenjpa);
        checkTicketOpenjpa(); 
        RetrieveGit.setBuilder(percorso + PROJECT1.toLowerCase() + "/.git");
        logger.log(Level.INFO, "Numero ticket = {0}.", ticketListOpenjpa.size());
        Collections.reverse(ticketListOpenjpa);
        Proportion.proportion(ticketListOpenjpa);
        checkTicketOpenjpa(); 
        RetrieveGit.getJavaFiles(Paths.get(percorso + PROJECT1.toLowerCase()), releasesListOpenjpa);
        RetrieveGit.checkBuggyness(releasesListOpenjpa, ticketListOpenjpa); 
        Metrics.getMetrics(releasesListOpenjpa, percorso + PROJECT1.toLowerCase() + "/.git");
        createCSV(releasesListOpenjpa, PROJECT1.toLowerCase());

    }
}