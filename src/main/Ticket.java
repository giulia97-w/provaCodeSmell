package main;

import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ticket {

    private String id;
    private Integer fixedVersion;
    private Integer openVersion; 
    private Integer injectedVersion; //
    private List<Integer> affectedVersion; 
    private Integer index;
    
    private LocalDateTime resolutionDate;

    private LocalDateTime creationDate;
    private List<LocalDateTime> commitDateList;
    private List<RevCommit> commitList;
    private List<String> fileList;


    public Ticket (String id, LocalDateTime creationDate, List<Integer> av)
    {
        this.id = id;
        this.affectedVersion = av;
        this.commitList = new ArrayList<>();
        this.fileList = new ArrayList<>();
        this.creationDate = creationDate;
    }

  
    public String getTicketID() {
        return id;
    }
    

    public List<Integer> getAffectedVersion() {
        return affectedVersion;
    }
    public void setAffectedVersion(List<Integer> av) {
        this.affectedVersion = av;
    }

    public LocalDateTime getResolutionDate() {
        return resolutionDate;
    }
    public void setResolutionDate(LocalDateTime resolutionDate) {
        this.resolutionDate = resolutionDate;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }
    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }
    
    


    public Integer getFixedVersion() {
        return fixedVersion;
    }
    public void setFixedVersion(Integer fv) {
        this.fixedVersion = fv;
    }

    public Integer getOpenVersion() {
        return openVersion;
    }
    public void setOpenVersion(Integer ov) {
        this.openVersion = ov;
    }

    public Integer getInjectedVersion() {
        return injectedVersion;
    }
    public void setInjectedVersion(Integer iv) {
        this.injectedVersion = iv;
    }
    
    public List<LocalDateTime> getCommitDateList() {
        return commitDateList;
    }
    public void setCommitDateList(List<LocalDateTime> commitDateList) {
        this.commitDateList = commitDateList;
    }


    public Integer getIndex() {
        return index;
    }

    public List<String>  getFileList() { 
    	return fileList;
    	}
    
    public void setIndex(Integer index) {
        this.index = index;
    }

    public List<RevCommit> getCommitList() {
        return commitList;
    }

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
            JSONObject json = Release.readJsonFromUrl(url);
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
            ticket.setInjectedVersion(affectedVersionsIndexList.get(0));
        } else {
            ticket.setInjectedVersion(0);
        }
        ticket.setOpenVersion(MainClass.afterBeforeDate(creationDate, releases));
        return ticket;
    }


	

}