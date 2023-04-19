package main;



import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
public class Release {


    private Integer index;
    private String name;
    private LocalDateTime date;
    private String rel;
    private List<RevCommit> commitList;
    private List <JavaFile> file;
	private Repository repository;

    public Release(Integer index, LocalDateTime date, String rel)
    {
        this.index = index;
        this.date = date;
        this.rel = rel;
        this.commitList = new ArrayList<>();
        this.file = new ArrayList<>();


    }

    //metodi get & set

    public String getRelease() {
        return rel;
    }
    
    public JavaFile getFileByName(String fileName) {
        for (JavaFile javaFile : file) {
            if (javaFile.getName().equals(fileName)) {
                return javaFile;
            }
        }
        return null;
    }
    public String getName() {
        return this.name;
    }
    public static boolean containsCommit(RevCommit commit, List<RevCommit> commits) {
        for (RevCommit c : commits) {
            if (c.getName().equals(commit.getName())) {
                return true;
            }
        }
        return false;
    }


    public LocalDateTime getDate() {
        return date;
    }

    public Integer getIndex() {
        return index;
    }

    public List<RevCommit> getCommitList() {
        return commitList;
    }
    public List<JavaFile> getFile() {
        return file;
    }
    public void addCommit(RevCommit commit) {
        commitList.add(commit);}

    //set
    public void setRelease(String release) {
        this.rel = release;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public void setCommitList(List<RevCommit> commitList) {
        this.commitList = commitList;
    }

    public void setFileList(List<JavaFile> fileList) {
        this.file = fileList;
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



}
