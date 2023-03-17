package main;


import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.opencsv.CSVWriter;

import Part1.RetrieveGit;


import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainClass {
	
	//variabile di tipo logger utilizzata per registrare messaggi di log
    private static final Logger logger = Logger.getLogger(MainClass.class.getName());
    
    // lista oggetti release che rappresentano le varie versioni del progetto
    private static List<Release> releasesList;
    // Una lista di oggetti ticket che rappresentano i ticket relativi al bug fixing del progetto
    private static List<Ticket> ticketList;
    // lista di oggetti che rappresentano i commit fatti nel repository del progetto
    private static List<RevCommit> commitList;
    //nome progetto
    public static final String NAMEPROJECT = "BOOKKEEPER";
    

    public static void main(String[] args) throws IllegalStateException, GitAPIException, IOException, JSONException {
    	
    	//repo del progetto
        String repo = "/Users/giuliamenichini/" + NAMEPROJECT.toLowerCase() + "/.git";
        Path repoPath = Paths.get("/Users/giuliamenichini/" + NAMEPROJECT.toLowerCase());

        // Ottengo lista di tutte le release del progetto
        releasesList = getListRelease(NAMEPROJECT);

        // Ottengo lista di tutti i commit del repository (in Github)
        commitList = RetrieveGit.getAllCommit(releasesList, repoPath);

        //Ottengo una lista di tutti i ticket relativi al progetto (in jira)
        ticketList = getTickets(releasesList, NAMEPROJECT);


        logger.log(Level.INFO, "Eseguo il linkage Tickets - Commits");
        //linkage collegamento stabilito per tenere traccia delle modifiche apportate a un progetto sw e delle relative correzioni  di errori in questo modo si può tenere traccia di quale commit ha corretto un determinato problema
        linkTicketCommits();
        //rimuovi metà delle release
        removeHalfRelease(releasesList, ticketList);

        //pulizia inconsistenze
        cleanTicketInconsistencies();
        //ottenimento repository per informazioni
        RetrieveGit.setBuilder(repo);
        //messaggio di log
        logger.log(Level.INFO, "Numero ticket = {0}.", ticketList.size());
        //inverte l'ordine degli elementi nella lista Tickelist. perché il metodo moving window
        //richiede di scorrere la lista in ordine inverso per calcolare gli overlap tra le finestre temporali
        //dei ticket. In questo modo il primo elemento della finestra inizia dal ticket più recente e non dal più vecchio
        //per overlap si intende la finestra temporale comune tra due finestre temporali adiacenti
        //questo permette di minimizzare la perdita di informazioni
       
        Collections.reverse(ticketList); //reverse perchè è moving window
        
        //metodo proportion
        Proportion.proportion(ticketList);
        
        cleanTicketInconsistencies();   //devo rifarlo perchè, avendo settato nuovi IV, voglio togliere possibili incongruenze!
        //ottieni file con estensione .java
        RetrieveGit.getJavaFiles(repoPath, releasesList);
        //verifica che siano buggati all'inizio sono tutti non buggy
        RetrieveGit.checkBuggyness(releasesList, ticketList); 
        
        Metrics.getMetrics(releasesList, repo);
        writeCSVBuggyness(releasesList, NAMEPROJECT.toLowerCase());

    }

    //linkage tra commit e ticket per ottenere il commit relativo a quel ticket.
    private static void linkTicketCommits() {
        for (Ticket ticket : ticketList) {
            String ticketID = ticket.getID();
            List<LocalDateTime> commitDateList = commitList.stream()
                    .filter(commit -> existsLinkMessageCommit(commit.getFullMessage(), ticketID))
                    .map(commit -> commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .collect(Collectors.toList());

            if (commitDateList.isEmpty()) {
                continue;
            }

            LocalDateTime resolutionDate = Collections.max(commitDateList);
            ticket.setResolutionDate(resolutionDate);
            ticket.setFV(compareDateVersion(resolutionDate, releasesList));
            ticket.getCommitList().addAll(commitList.stream()
                    .filter(commit -> existsLinkMessageCommit(commit.getFullMessage(), ticketID))
                    .collect(Collectors.toList()));
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
        // Verifica che il ticket ID sia presente nella stringa del commit, preceduto e seguito solo da caratteri non numerici o lettere.
        String regex = "(?<![\\d\\w])" + ticketID + "(?![\\d\\w])";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message);
        return matcher.find();
    }

    //rimuovo la metà delle release per evitare data missing
    public static void removeHalfRelease(List<Release> releasesList, List<Ticket> ticketList) {

        int releaseNumber = releasesList.size();

        int halfRelease = releaseNumber / 2; // arrotondo in difetto, ora il numero di release che voglio e' la meta'

        logger.log(Level.INFO, "NUMERO RELEASE == = {0}.", releaseNumber);
        logger.log(Level.INFO, "HALF RELEASE == = {0}.", halfRelease);

        releasesList.removeIf(release -> release.getIndex() > halfRelease);

        removeAndSetAVTickets(halfRelease, ticketList);
    }



    public static void removeAndSetAVTickets(int halfRelease, List<Ticket> ticketList) {

        Iterator<Ticket> iterator = ticketList.iterator();
        while (iterator.hasNext()) {
            Ticket t = iterator.next();
            if (t.getIV() > halfRelease) {
                iterator.remove();
            } else if (t.getOV() > halfRelease || t.getFV() > halfRelease) {
                int startIV = t.getIV();
                int endIV = Math.min(halfRelease, t.getFV()); // l'intervallo di AV va da IV fino al minimo tra FV e halfRelease
                List<Integer> affectedVersionsListByTicket = IntStream.rangeClosed(startIV, endIV)
                    .boxed()
                    .collect(Collectors.toList());
                t.setAV(affectedVersionsListByTicket);
            }
        }
    }




    public static void cleanTicketInconsistencies() {
        for (Ticket ticket : ticketList) {
            if (ticket.getIV() == 0) {
                handleUndefinedIV(ticket);
            } else {
                handleDefinedIV(ticket);
            }
        }
    }

    private static void handleUndefinedIV(Ticket ticket) {
        ticket.setIV(1);
        ticket.getAV().clear();
        for (int i = ticket.getIV(); i <= releasesList.size(); i++) {
            ticket.getAV().add(i);
        }
    }

    private static void handleDefinedIV(Ticket ticket) {
        if (isCorrectIV(ticket)) {
            updateCorrectIV(ticket);
        } else {
            handleInvalidIV(ticket);
        }

        if (isBaseCase(ticket)) {
            handleBaseCase(ticket);
        } else if (isFirstRelease(ticket)) {
            handleFirstRelease(ticket);
        }
    }

    private static boolean isCorrectIV(Ticket ticket) {
        return ticket.getFV() > ticket.getIV() && ticket.getOV() >= ticket.getIV();
    }

    private static void updateCorrectIV(Ticket ticket) {
        ticket.getAV().clear();
        for (int i = ticket.getIV(); i < ticket.getFV(); i++) {
            ticket.getAV().add(i);
        }
    }

    private static void handleInvalidIV(Ticket ticket) {
        ticket.setIV(0);
        ticket.getAV().clear();
        ticket.getAV().add(0);
    }

    private static boolean isBaseCase(Ticket ticket) {
        return ticket.getFV().equals(ticket.getIV());
    }

    private static void handleBaseCase(Ticket ticket) {
        ticket.getAV().clear();
        ticket.getAV().add(0);
    }

    private static boolean isFirstRelease(Ticket ticket) {
        return ticket.getOV() == 1;
    }

    private static void handleFirstRelease(Ticket ticket) {
        ticket.getAV().clear();
        if (ticket.getFV() == 1) {
            ticket.setIV(1);
        } else {
            ticket.setIV(1);
            for (int i = ticket.getIV(); i < ticket.getFV(); i++) {
                ticket.getAV().add(i);
            }
        }
        ticket.getAV().add(0);
    }


    
    private static Map<LocalDateTime, String> releasesNameVersion;
    private static Map<LocalDateTime, String> releasesID;
    private static List<LocalDateTime> releasesOnlyDate;
    private static final String RELEASEDATE = "releaseDate";    //added for resolve code smells

    private static String readAll(Reader reader) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        int character;
        while ((character = reader.read()) != -1) {
            stringBuilder.append((char) character);
        }
        return stringBuilder.toString();
    }

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        URL urlObject = new URL(url);

        HttpURLConnection connection = (HttpURLConnection) urlObject.openConnection();
        connection.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String jsonText = readAll(reader);
            return new JSONObject(jsonText);
        } finally {
            connection.disconnect();
        }
    }



    /** OPERAZIONI PER OTTENERE LE RELEASE **/

    public static List<Release> getListRelease(String projName) throws IOException, JSONException {

        ArrayList<Release> releaseList = new ArrayList<>();

        // Fills the arraylist with releases dates and orders them
        // Ignores releases with missing dates
        releasesOnlyDate = new ArrayList<>();
        int i;
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projName;
        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");
        releasesNameVersion = new HashMap<>();
        releasesID = new HashMap<>();
        for (i = 0; i < versions.length(); i++) {
            String name = "";
            String id = "";
            String releaseDate="";
            if (versions.getJSONObject(i).has(RELEASEDATE)) {                 //qui sfrutto le api per prelevare nome, id, release date dei ticket JIRA.
                if (versions.getJSONObject(i).has("name"))
                    name = versions.getJSONObject(i).get("name").toString();
                if (versions.getJSONObject(i).has("id"))
                    id = versions.getJSONObject(i).get("id").toString();
                if (versions.getJSONObject(i).has(RELEASEDATE))
                    releaseDate = versions.getJSONObject(i).get(RELEASEDATE).toString();
                addRelease(releaseDate, name, id);              // Questo metodo popola gli attributi della classe presente a inizio codice.

            }
        }
        releasesOnlyDate.sort(LocalDateTime::compareTo);                                //ordinamento in base alla data

        createCSVReleases(projName,releasesID,releasesNameVersion,releasesOnlyDate); //dopo aver popolato le tabelle hash delle release, le scrivo su un file csv.

        for (int j = 0; j <releasesOnlyDate.size(); j++)
        {
            LocalDateTime releaseDatetime = releasesOnlyDate.get(j);
            String releaseNameVersion = releasesNameVersion.get(releaseDatetime);
            Release release = new Release(j+1,releaseDatetime,releaseNameVersion); //primo parametro è index
            releaseList.add(release);
        }

        return releaseList;
    }


    private static void addRelease(String releaseDate, String name, String id) {
        try {
            LocalDate date = LocalDate.parse(releaseDate);
            LocalDateTime dateTime = date.atStartOfDay();
            if (!releasesOnlyDate.contains(dateTime)) {
                releasesOnlyDate.add(dateTime);
            }
            releasesNameVersion.put(dateTime, name);
            releasesID.put(dateTime, id);
        } catch (DateTimeParseException e) {
            String errorMessage = String.format("Error adding release with date %s, name %s, and id %s", releaseDate, name, id);
            logger.log(Level.SEVERE, errorMessage, e);
            // handle the exception appropriately, for example by returning a default value or presenting an error message to the user
        }
    }






    /**  OPERAZIONI PER OTTENERE LE I TICKET **/


    //adesso che ho info sulle release, voglio i ticket associati a queste release.
    public static List<Ticket> getTickets( List<Release> releases, String projName) throws IOException { //lavoro con 50% delle release!

        int j = 0;
        int i = 0;
        int total = 1;
        //creo nuova lista di ticket
        ArrayList<Ticket> ticketList = new ArrayList<>();
        //Get JSON API for closed bugs w/ AV in the project
        do {
            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + projName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,affectedVersion,versions,created&startAt="
                    + i + "&maxResults=" + j;

            JSONObject json = readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                //Iterate through each bug = ticket
                String key = issues.getJSONObject(i%1000).get("key").toString(); //prendo key, come 'AVRO-1105'
                LocalDateTime creationDate = LocalDateTime.parse(issues.getJSONObject(i%1000).getJSONObject("fields").getString("created").substring(0,16)); //consistente con Data della Release(faccio substring per avere formato di data come quello della release)
                JSONArray affectedVersion = issues.getJSONObject(i%1000).getJSONObject("fields").getJSONArray("versions"); //Affected versions, è un JSON array, posso avere da 0 ad n elementi di AV (contiene anche altre info che non ci interessano!)
                List<Integer> listAVForTicket = getAV(affectedVersion,releases);     //sostanzialmente prende SOLO i nomi delle AV dal JSONArray e le associa agli index delle release che ho.

                Ticket ticket = new Ticket(key,creationDate,listAVForTicket);


                if ( ! (listAVForTicket.isEmpty() || listAVForTicket.get(0) == null) ) {         // Controllo che la lista generate NON SIA VUOTA. Il primo di questi è l'Injected version.
                    ticket.setIV(listAVForTicket.get(0));                                        // Il primo elemento in un array NON VUOTO di Affected Versions è IV.
                } else {
                    ticket.setIV(0);                                                             //Altrimenti metto come IV index 0, che non corrisponde a nulla, ma serve per capire che IV non è noto.
                }

                ticket.setOV(compareDateVersion(creationDate,releases));                        //Adesso ho settato tutte info del ticket. (Al massimo può mancare IV)
                ticketList.add(ticket); //aggiungo a lista ticket
            }
        } while (i < total);

        return ticketList;                                                                      //lista di ticket con nome, iv (se presente), av, ov.
        }


    private static List<Integer> getAV(JSONArray versions, List<Release> releases) {
        List<Integer> listaAV = new ArrayList<>();

        if (versions.length() == 0) {
            listaAV.add(null); // non ci sono affected version
        } else {
            for (int j = 0; j < versions.length(); j++) {
                String av = versions.getJSONObject(j).getString("name"); // nome release affected (4.3.0)
                boolean found = false;
                for (Release release : releases) {
                    if (av.equals(release.getRelease())) { // confronto nome AV con nomi delle release (nome = nome versione)
                        listaAV.add(release.getIndex()); // mi dice indice release AV
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    listaAV.add(null);
                }
            }
        }
        return listaAV;
    }


    public static int compareDateVersion(LocalDateTime date, List<Release> releases) {

        for (int i = releases.size() - 1; i >= 0; i--) {
            Release release = releases.get(i);
            if (date.isEqual(release.getDate()) || date.isAfter(release.getDate())) {
                return release.getIndex();
            }
        }
        
        return -1;
    }


    
    public static void createCSVReleases(String projName, Map<LocalDateTime, String> releasesID, Map<LocalDateTime, String> releasesNameVersion, List<LocalDateTime> releasesOnlyDate) {
        String fileName = projName.toLowerCase() + ".ReleasesList.csv";

        try (CSVWriter csvWriter = new CSVWriter(new FileWriter(fileName))) {
            String[] header = {"Index", "VersionID", "VersionName", "Date"};
            csvWriter.writeNext(header);

            for (int i = 0; i < releasesOnlyDate.size(); i++) {
                int index = i + 1;
                LocalDateTime releaseDate = releasesOnlyDate.get(i);
                String releaseID = releasesID.get(releaseDate);
                String releaseNameVersion = releasesNameVersion.get(releaseDate);
                
                String[] data = {Integer.toString(index), releaseID, releaseNameVersion, releaseDate.toString()};
                csvWriter.writeNext(data);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error in CSV Releases Lists", e);
        }
    }



    public static void writeCSVBuggyness(List<Release> releasesList, String project) {

        try (FileWriter fileWriter = new FileWriter(project.toLowerCase()+".Buggyness.csv"))
        {
            //creo file csv.
            fileWriter.append("RELEASE,FILENAME,LOC,LOC_added,MAX_LOC_Added,AVG_LOC_Added,CHURN,MAX_Churn,AVG_Churn,NR,NAUTH,CHGSETSIZE,MAX_ChgSet,AVG_ChgSet,BUGGYNESS\n");
            for (Release release : releasesList) {

                for (JavaFile file : release.getFileList()) {
                    //per ogni file appartenente alla release 'x'

                    appendMetrics(fileWriter, release, file);
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error in writeCSVBuggyness");
        }

    }

    private static void appendMetrics(FileWriter fileWriter, Release release, JavaFile file) throws IOException {
        fileWriter.append(release.getIndex().toString());
        fileWriter.append(",");
        fileWriter.append(file.getName()); //nome del file
        fileWriter.append(",");
        fileWriter.append(file.getLOC().toString()); //LOC
        fileWriter.append(",");
        fileWriter.append(file.getLOCadded().toString()); //LOC_added
        fileWriter.append(",");

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
        fileWriter.append(",");
        fileWriter.append(file.getChurn().toString());
        fileWriter.append(",");
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
        fileWriter.append(",");

        fileWriter.append(file.getNr().toString());
        fileWriter.append(",");
        int loc = file.getNAuth().size();
        fileWriter.append(String.valueOf(loc));
        fileWriter.append(",");
        fileWriter.append(file.getChgSetSize().toString());
        fileWriter.append(",");
        if (file.getChgSetSize().equals(0)) {
            fileWriter.append("0");
            fileWriter.append(",");
            fileWriter.append("0");
        } else {
            int maxChgSet = Collections.max((file.getChgSetSizeList()));
            fileWriter.append(String.valueOf(maxChgSet));
            fileWriter.append(",");
            int avgChgSet = (int) file.getChgSetSizeList().stream().mapToInt(Integer::intValue).average().orElse(0.0); //da calcolare
            fileWriter.append(String.valueOf(avgChgSet));

        }
        fileWriter.append(",");
        fileWriter.append(file.getBugg());
        fileWriter.append("\n");
        fileWriter.flush();
    }

// per arrotondare alla seconda cifra decimale!

    public static String doubleTransform(Double value) {
        DecimalFormat df = new DecimalFormat("#.######", DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(value);
    }




public static void writeWekaCSV(List<WekaRecordi> wekaRecordList, String projName) {
    try (
            FileWriter fileWriter = new FileWriter(projName.toLowerCase()+".WekaResults.csv")) {

        fileWriter.append("Dataset,#TrainingRelease,%training/total,%Defective/training,%Defective/testing,Classifier,"
                + "Feature Selection,Balancing,Sensitivity,TP,FP,TN,FN,Precision,Recall,AUC,Kappa\n");


        for(WekaRecordi entry : wekaRecordList) {

            fileWriter.append(entry.getDatasetName());
            fileWriter.append(",");
            fileWriter.append(entry.getNumTrainingRelease().toString());
            fileWriter.append(",");
            fileWriter.append(doubleTransform(entry.getTrainingPerc()));
            fileWriter.append(",");
            fileWriter.append(doubleTransform(entry.getDefectPercTrain()));
            fileWriter.append(",");
            fileWriter.append(doubleTransform(entry.getDefectPercTest()));
            fileWriter.append(",");
            fileWriter.append(entry.getClassifierName());
            fileWriter.append(",");
            fileWriter.append(entry.getFeatureSelection());
            fileWriter.append(",");
            fileWriter.append(entry.getBalancing());
            fileWriter.append(",");
            fileWriter.append(entry.getSensitivity());
            fileWriter.append(",");
            fileWriter.append(entry.getTP().toString());
            fileWriter.append(",");
            fileWriter.append(entry.getFP().toString());
            fileWriter.append(",");
            fileWriter.append(entry.getTN().toString());
            fileWriter.append(",");
            fileWriter.append(entry.getFN().toString());
            fileWriter.append(",");
            fileWriter.append(doubleTransform(entry.getPrecision()));
            fileWriter.append(",");
            fileWriter.append(doubleTransform(entry.getRecall()));
            fileWriter.append(",");
            fileWriter.append(doubleTransform(entry.getAuc()));
            fileWriter.append(",");
            fileWriter.append(doubleTransform(entry.getKappa()));
            fileWriter.append("\n");
            fileWriter.flush();
        }

    } catch (Exception ex) {
        logger.log(Level.SEVERE, "Error in writeWekaCSV");
    }
}



}
