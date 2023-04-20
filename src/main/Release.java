package main;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
public class Release {

	private static final String RELEASEDATE = "releaseDate";
	private static final Logger logger = Logger.getLogger(Release.class.getName());
    private static Integer numVersions;
    private Integer id;
    private String name;
    private LocalDateTime productReleaseDate;
    private String rel;
    private List<RevCommit> commitList;
    private List <JavaFile> file;
	private Repository repository;
	private static Map<LocalDateTime, String> releasesNames;
	private static Map<LocalDateTime, String> releasesID;
	private static List<LocalDateTime> releases;
	
	
	private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }


    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        try (InputStream is = new URL(url).openStream();
        	     BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        	    String jsonText = readAll(rd);
        	    return new JSONObject(jsonText);
        	} catch (IOException e) {
        	    logger.log(Level.SEVERE, "Errore durante la lettura del file JSON", e);
        	}
		return null;}
	
    public Release(Integer versionID, LocalDateTime date, String rel)
    {
        this.id = versionID;
        this.productReleaseDate = date;
        this.rel = rel;
        this.commitList = new ArrayList<>();
        this.file = new ArrayList<>();


    }

 

    public String getRelease() {
        return rel;
    }
    
    public JavaFile getFileByName(String fileName) {
        return file.stream()
                   .filter(javaFile -> javaFile.getName().equals(fileName))
                   .findFirst()
                   .orElse(null);
    }

    public String getName() {
        return this.name;
    }
    public static boolean containsCommit(RevCommit commit, List<RevCommit> commits) {
        return commits.stream()
                      .anyMatch(c -> c.getName().equals(commit.getName()));
    }



    public LocalDateTime getDate() {
        return productReleaseDate;
    }
    public void setDate(LocalDateTime date) {
        this.productReleaseDate = date;
    }

    public Integer getIndex() {
        return id;
    }
    public void setIndex(Integer index) {
        this.id = index;
    }


    public List<RevCommit> getCommitList() {
        return commitList;
    }
    public void setCommitList(List<RevCommit> commitList) {
        this.commitList = commitList;
    }
    public List<JavaFile> getFile() {
        return file;
    }
    public void setFileList(List<JavaFile> fileList) {
        this.file = fileList;
    }
    public void addCommit(RevCommit commit) {
        commitList.add(commit);}

   
    public void setRelease(String release) {
        this.rel = release;
    }

    public Repository getRepository() {
        return this.repository;
    }

    
    static List<Integer> getAV(JSONArray versions, List<Release> releases) {
        List<Integer> listaAV = new ArrayList<>();

        if (versions.isEmpty()) {
            listaAV.add(null); 
        } else {
            for (int j = 0; j < versions.length(); j++) {
                String av = versions.getJSONObject(j).getString("name"); 
                Optional<Release> releaseOptional = releases.stream()
                        .filter(release -> av.equals(release.getRelease()))
                        .findFirst();
                if (releaseOptional.isPresent()) {
                    listaAV.add(releaseOptional.get().getIndex()); 
                }
            }
        }
        return listaAV;
    }
    
    public static List<Release> getListRelease(String projName) throws IOException, JSONException {

        

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
        Collections.sort(releases, Comparable::compareTo);
        
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


        return newList(releases, releasesNames);

    }
    
    private static List<Release> newList(List<LocalDateTime> releases, Map<LocalDateTime, String> releasesNames) {
        return IntStream.range(0, releases.size())
                .mapToObj(i -> {
                    LocalDateTime time = releases.get(i);
                    String name = releasesNames.get(time);
                    return new Release(i + 1, time, name);
                })
                .collect(Collectors.toList());
    }

    public Integer getNumVersions() {
        return numVersions;
    }


    private static void addRelease(String releaseDate, String name, String id) {
        LocalDate date = LocalDate.parse(releaseDate);                                          
        LocalDateTime dateTime = date.atStartOfDay();                                          
        if (!releases.contains(dateTime))                                               
            releases.add(dateTime);                                                  

        releasesNames.put(dateTime, name);                                                
        releasesID.put(dateTime, id);                                                           
    }



}
