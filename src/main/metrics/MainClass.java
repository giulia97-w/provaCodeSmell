
package main;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import org.json.JSONException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;



public class MainClass {

    private static final Logger logger = Logger.getLogger(MainClass.class.getName());


    private static List<Release> releasesList;
    private static List<Ticket> ticketList;
    private static List<RevCommit> commitList;
    public static final String NAMEPROJECT = "BOOKKEEPER"; 


    public static void main(String[] args) throws IllegalStateException, GitAPIException, IOException, JSONException {

        String repo = "/Users/giuliamenichini/" + NAMEPROJECT.toLowerCase() + "/.git";
        Path repoPath = Paths.get("/Users/giuliamenichini/" + NAMEPROJECT.toLowerCase());

        // in releases List metto tutte le release del progetto
        releasesList = RetrieveJira.getListRelease(NAMEPROJECT);

        // in commit List metto tutti i commit del progetto
        commitList = RetrieveGit.getAllCommit(releasesList, repoPath);

        //prendo tutti i ticket da Jira in accordo alle specifiche
        ticketList = RetrieveJira.getTickets(releasesList, NAMEPROJECT);


        logger.log(Level.INFO, "Eseguo il linkage Tickets - Commits");
        linkTicketCommits();
        removeHalfRelease(releasesList, ticketList);


        cleanTicketInconsistencies();
        RetrieveGit.setBuilder(repo);
        logger.log(Level.INFO, "Numero ticket = {0}.", ticketList.size());

        Collections.reverse(ticketList); //reverse perchè è moving window
        Proportion.proportion(ticketList);

        cleanTicketInconsistencies();   //devo rifarlo perchè, avendo settato nuovi IV, voglio togliere possibili incongruenze!

        RetrieveGit.getJavaFiles(repoPath, releasesList);

        RetrieveGit.checkBuggyness(releasesList, ticketList); //inizialmente buggyness = NO per ogni release

        Metrics.getMetrics(releasesList, repo);
        CSVCreator.writeCSVBuggyness(releasesList, NAMEPROJECT.toLowerCase());

    }


    private static void linkTicketCommits() {
        for (Ticket ticket : ticketList) {
            ArrayList<LocalDateTime> commitDateList = new ArrayList<>();

            String ticketID = ticket.getID();

            for (RevCommit commit : commitList) {   //LINKAGE ticketList - commitList, se c'è prendo la data di creazione del commit
                String message = commit.getFullMessage();

                if (existsLinkMessageCommit(message,ticketID)) {
                    LocalDateTime commitDate = commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    commitDateList.add(commitDate);
                    ticket.getCommitList().add(commit);
                }


            }
            if (!commitDateList.isEmpty()) { //Esistono ticket chiusi senza commit (es: BOOKKEEPER-884: 4.3.2 link points to 4.3.1 documentation). Questi ticket non mi sono di aiuto.

                Collections.sort(commitDateList);
                LocalDateTime resolutionDate = commitDateList.get(commitDateList.size() - 1); //la resolution date di un ticket è l'ultimo commit fatto associato a quel ticket.
                ticket.setResolutionDate(resolutionDate);
                ticket.setFV(RetrieveJira.compareDateVersion(resolutionDate, releasesList));


            }

        }
        //Rimuovo ticket che non hanno alcun commit associato, li riconosco perchè non hanno resolutationDate (data ultimo commit).
        Iterator<Ticket> ticket = ticketList.iterator();

        while (ticket.hasNext()) {
            Ticket t = ticket.next();
            if (t.getResolutionDate() == null) {
                ticket.remove();
            }
        }
    }

    private static boolean existsLinkMessageCommit(String message, String ticketID) {
        // Utilizzo una regex per cercare una parola intera contenente il ticketID. In questo modo evito di linkare ticketID errati.
        // La regex \b indica il bordo della parola, mentre \d+ indica una o più cifre.
        // La regex nega il link se trova un carattere diverso da spazio, virgola, punto, parentesi quadra, trattino, slash o underscore subito prima o subito dopo il ticketID.
        String regex = "(?<![\\w,.\\[\\]/_-])" + ticketID + "(?![\\w,.\\[\\]/_-])";
        return message.matches(".*" + regex + ".*");
    }


    public static void removeHalfRelease(List<Release> releasesList, List<Ticket> ticketList) {

        float releaseNumber = releasesList.size();

        int halfRelease = (int) Math.floor(releaseNumber / 2); // arrotondo in difetto, ora il numero di release che voglio e' la meta'

        Iterator<Release> i = releasesList.iterator();
        while (i.hasNext()) {
            Release s = i.next();
            if (s.getIndex() > halfRelease) {
                i.remove();
            }
        }

        Iterator<Ticket> iterator = ticketList.iterator();
        while (iterator.hasNext()) {
            Ticket t = iterator.next();
            if (t.getIV() > halfRelease) {
                iterator.remove();
            }
            if (t.getOV() > halfRelease || t.getFV() > halfRelease) {
                List<Integer> affectedVersionsListByTicket = new ArrayList<>();
                for (int k = t.getIV(); k < halfRelease; k++) {
                    affectedVersionsListByTicket.add(k);
                }
                t.setAV(affectedVersionsListByTicket);
            }
        }
    }



    public static void cleanTicketInconsistencies() {
        for (Ticket ticket : ticketList) {
            if (ticket.getIV() == 0) {
                continue;
            }
            
            int iv = ticket.getIV();
            int fv = ticket.getFV();
            int ov = ticket.getOV();
            
            List<Integer> av = ticket.getAV();
            av.clear();
            
            if (fv > iv && ov >= iv) {
                for (int i = iv; i < fv; i++) {
                    av.add(i);
                }
            } else {
                ticket.setIV(0);
                av.add(0);
            }
            
            if (fv == iv || ov == 1) {
                av.clear();
                av.add(0);
                
                if (ov == 1) {
                    ticket.setIV(1);
                    if (fv > 1) {
                        for (int i = 1; i < fv; i++) {
                            av.add(i);
                        }
                    }
                }
            }
        }
    }


}