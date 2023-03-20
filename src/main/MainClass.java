
package main;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import main.Release;
import main.Ticket;
import org.json.JSONException;

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
import java.util.stream.IntStream;


public class MainClass {

    private static final Logger logger = Logger.getLogger(MainClass.class.getName());


    private static List<Release> rel;
    private static List<Ticket> tickets;
    private static List<RevCommit> com;
    public static final String NAMEPROJECT = "BOOKKEEPER"; // OR 'AVRO'


    public static void main(String[] args) throws IllegalStateException, GitAPIException, IOException, JSONException {

        String repo = "/Users/giuliamenichini/" + NAMEPROJECT.toLowerCase() + "/.git";
        Path repoPath = Paths.get("/Users/giuliamenichini/" + NAMEPROJECT.toLowerCase());

        // in releases List metto tutte le release del progetto
        rel = RetrieveJira.getListRelease(NAMEPROJECT);

        // in commit List metto tutti i commit del progetto
        com = RetrieveGit.getAllCommit(rel, repoPath);

        //prendo tutti i ticket da Jira in accordo alle specifiche
        tickets = RetrieveJira.getTickets(rel, NAMEPROJECT);


        logger.log(Level.INFO, "Eseguo il linkage Tickets - Commits");
        linkage();
        av(rel, tickets);


        control();
        RetrieveGit.setBuilder(repo);
        logger.log(Level.INFO, "Numero ticket = {0}.", tickets.size());

        Collections.reverse(tickets); //reverse perchè è moving window
        Proportion.proportion(tickets);

        control();   //devo rifarlo perchè, avendo settato nuovi IV, voglio togliere possibili incongruenze!

        RetrieveGit.getJavaFiles(repoPath, rel);

        RetrieveGit.checkBuggyness(rel, tickets); //inizialmente buggyness = NO per ogni release

        Metrics.calculateMetricsForReleases(rel, repo);
        CSVCreator.writeCSVBuggyness(rel, NAMEPROJECT.toLowerCase());

    }


    private static void linkage() {
        Iterator<Ticket> ticket = tickets.iterator();

        while (ticket.hasNext()) {
            Ticket t = ticket.next();
            String ticketID = t.getID();
            List<LocalDateTime> commitDateList = com.stream()
                    .filter(commit -> {
                        // Verifica che il ticket ID sia presente nella stringa del commit, preceduto e seguito solo da caratteri non numerici o lettere.
                        String regex = "(?<![\\d\\w])" + ticketID + "(?![\\d\\w])";
                        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                        Matcher matcher = pattern.matcher(commit.getFullMessage());
                        return matcher.find();
                    })
                    .map(commit -> commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .collect(Collectors.toList());

            if (commitDateList.isEmpty()) {
                ticket.remove();
                continue;
            }

            LocalDateTime resolutionDate = Collections.max(commitDateList);
            t.setResolutionDate(resolutionDate);
            t.setFV(RetrieveJira.compareDateVersion(resolutionDate, rel));
            t.getCommitList().addAll(com.stream()
                    .filter(commit -> {
                        String regex = "(?<![\\d\\w])" + ticketID + "(?![\\d\\w])";
                        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                        Matcher matcher = pattern.matcher(commit.getFullMessage());
                        return matcher.find();
                    })
                    .collect(Collectors.toList()));
        }
    }

   


    public static void av(List<Release> rel, List<Ticket> tickets) {

        int releaseNumber = rel.size();

        int halfRelease = releaseNumber / 2; // arrotondo in difetto, ora il numero di release che voglio e' la meta'

        logger.log(Level.INFO, "NUMERO RELEASE == = {0}.", releaseNumber);
        logger.log(Level.INFO, "HALF RELEASE == = {0}.", halfRelease);

        Iterator<Ticket> iterator = tickets.iterator();
        while (iterator.hasNext()) {
            Ticket ticket = iterator.next();
            if (ticket.getIV() > halfRelease) {
                iterator.remove();
            } else {
                int endIV = Math.min(halfRelease, ticket.getFV());
                if (ticket.getOV() > halfRelease) {
                    ticket.setAV(IntStream.rangeClosed(ticket.getIV(), endIV).boxed().collect(Collectors.toList()));
                } else {
                    ticket.setAV(IntStream.rangeClosed(ticket.getIV(), Math.min(halfRelease, ticket.getFV())).boxed().collect(Collectors.toList()));
                }
            }
        }

        rel.removeIf(release -> release.getIndex() > halfRelease);
    }




    public static void control() {
        for (Ticket ticket : tickets) {
            if (ticket.getIV() == 0) {
                resetTicket(ticket);
            } else if (ticket.getFV().equals(ticket.getIV())) {
                ticket.getAV().clear();
                ticket.getAV().add(0);
            } else if (ticket.getOV() == 1) {
                resetTicket(ticket);
                ticket.getAV().add(0);
            } else if (ticket.getFV() > ticket.getIV() && ticket.getOV() >= ticket.getIV()) {
                ticket.getAV().clear();
                for (int i = ticket.getIV(); i < ticket.getFV(); i++) {
                    ticket.getAV().add(i);
                }
            } else {
                resetTicket(ticket);
            }
        }
    }
    //svuoto AV per tutti gli IV della release (dimezzata)
    private static void resetTicket(Ticket ticket) {
        ticket.setIV(1);
        ticket.getAV().clear();
        for (int i = ticket.getIV(); i <= rel.size(); i++) {
            ticket.getAV().add(i);
        }
    }}