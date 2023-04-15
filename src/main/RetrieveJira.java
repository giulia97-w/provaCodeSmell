package main;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RetrieveJira {

    private RetrieveJira() {
    }
    private static final Logger logger = Logger.getLogger(RetrieveJira.class.getName());

    private static Map<LocalDateTime, String> releasesNames;
    private static Map<LocalDateTime, String> releasesID;
    private static List<LocalDateTime> releases;
    private static final String RELEASEDATE = "releaseDate";    //added for resolve code smells
	public static Integer numVersions = null;

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }


    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
           BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
           String jsonText = readAll(rd);
           return new JSONObject(jsonText);
         } finally {
           is.close();
         }
    }

    

    public static List<Release> getListRelease(String projName) throws IOException, JSONException {

       

        // Fills the arraylist with releases dates and orders them
        // Ignores releases with missing dates
        releases = new ArrayList<>();
        Integer i;
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projName;
        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");
        releasesNames = new HashMap<>();
        releasesID = new HashMap<>();
        for (i = 0; i < versions.length(); i++) {
            String name = "";
            String id = "";
            String releaseDate="";
            if (versions.getJSONObject(i).has(RELEASEDATE)) {                 
                if (versions.getJSONObject(i).has("name"))
                    name = versions.getJSONObject(i).get("name").toString();
                if (versions.getJSONObject(i).has("id"))
                    id = versions.getJSONObject(i).get("id").toString();
                if (versions.getJSONObject(i).has(RELEASEDATE))
                    releaseDate = versions.getJSONObject(i).get(RELEASEDATE).toString();
                addRelease(releaseDate, name, id);              

            }
        }
        Collections.sort(releases, (dateTime1, dateTime2) -> dateTime1.compareTo(dateTime2));
        
        String outname = projName + "VersionInfo.csv";
      //Name of CSV for output
        try (FileWriter fileWriter = new FileWriter(outname)) {
            fileWriter.append("Index,Version ID,Version Name,Date");
            fileWriter.append("\n");
            numVersions = releases.size();
            for (i = 0; i < releases.size(); i++) {
                Integer index = i + 1;
                fileWriter.append(index.toString());
                fileWriter.append(",");
                fileWriter.append(releasesID.get(releases.get(i)));
                fileWriter.append(",");
                fileWriter.append(releasesNames.get(releases.get(i)));
                fileWriter.append(",");
                fileWriter.append(releases.get(i).toString());
                fileWriter.append("\n");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error in csv writer", e);
        }

         
   


        List<Release> releaseListNew = createReleaseList(releases, releasesNames);
        return releaseListNew;

    }
    
    private static List<Release> createReleaseList(List<LocalDateTime> releases, Map<LocalDateTime, String> releasesNames) {
        List<Release> releaseList = new ArrayList<>();
        for (int j = 0; j < releases.size(); j++) {
            LocalDateTime releaseDatetime = releases.get(j);
            String releaseNameVersion = releasesNames.get(releaseDatetime);
            Release release = new Release(j+1, releaseDatetime, releaseNameVersion);
            releaseList.add(release);
        }
        return releaseList;
    }



    private static void addRelease(String releaseDate, String name, String id) {
        LocalDate date = LocalDate.parse(releaseDate);                                          
        LocalDateTime dateTime = date.atStartOfDay();                                          
        if (!releases.contains(dateTime))                                               
            releases.add(dateTime);                                                  

        releasesNames.put(dateTime, name);                                                
        releasesID.put(dateTime, id);                                                           
    }



    


    //adesso che ho info sulle release, voglio i ticket associati a queste release.
    public static List<Ticket> getTickets(List<Release> releases, String projName) throws IOException {
		List<Ticket> tickets = new ArrayList<>();
        Integer i = 0;
        Integer total = 1;
        while (i < total) {
            Integer j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22" + projName +
                    "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR%22status%22=%22resolved%22)" +
                    "AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,affectedVersion,versions,created&startAt="
                    + i + "&maxResults=" + j.toString();
            JSONObject json = readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                JSONObject jsonIssues = issues.getJSONObject(i % 1000);
                Ticket ticket = createTicket(jsonIssues, releases);
                tickets.add(ticket);
            }
        }
        return tickets;
    }

    private static Ticket createTicket(JSONObject jsonIssues, List<Release> releases) {
        JSONObject jsonFields = jsonIssues.getJSONObject("fields");
        String key = jsonIssues.get("key").toString();
        LocalDateTime creationDate = LocalDateTime.parse(jsonFields.getString("created").substring(0, 16));
        JSONArray affectedVersions = jsonFields.getJSONArray("versions");
        List<Integer> affectedVersionsIndexList = Release.getAV(affectedVersions, releases);
        Ticket ticket = new Ticket(key, creationDate, affectedVersionsIndexList);
        if (!(affectedVersionsIndexList.isEmpty() || affectedVersionsIndexList.get(0) == null)) {
            ticket.setIV(affectedVersionsIndexList.get(0));
        } else {
            ticket.setIV(0);
        }
        ticket.setOV(MainClass.beforeAfterDate(creationDate, releases));
        return ticket;
    }



    



}