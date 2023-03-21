package metrics;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import org.json.JSONException;
import entities.Commit;
import entities.FileCommitted;
import entities.Release;
import entities.Result;
import entities.Ticket;

public class Dataset {
	
	
	
	public static final String TOKEN = "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";
	public static final String PROJECT = "BOOKKEEPER";
	
	public static void main(String[] args) throws JSONException, IOException, ParseException {
		
		//Get token and project name from conf.properties
		
		String token = TOKEN;
		
		String project = PROJECT;
		
		/* Start to get information about project that will be used for calculating metrics */
		
		retrieveInfo(project, token);
		 	
	}
	
	private static final Logger LOGGER = Logger.getLogger(Dataset.class.getName());
	
	static int numBugs;

	static float p;
	
	static int numAnalyseBugs;
	
	//List of tickets from the project	
	static List<Ticket> tickets = null;	
			
	//List of all the commits of the project
	static List<Commit> commits = null;	
			
	//List of all the release of the project
	static List<Release> release = null;
			
	//List of all file committed
	static List<FileCommitted> commitedFile = null;
	
	static int numAffected;
	
	private Dataset() {}
	
	/* remove all tickets that don't have fixed version or commit associating and
	 *
	 * comparing fix version of a tickets and take the fix versison that have the latest date*/
	
	public static List<Ticket> checkFixVersionTickets(List<Ticket> tickets, List<Release> releases) {
	    List<Ticket> validTickets = new ArrayList<>();
	    
	    for (Ticket ticket : tickets) {
	        List<String> fixVersions = ticket.getFixVersions();
	        List<Commit> commits = ticket.getCommitsTicket();
	        List<String> affectedVersions = ticket.getAffectedVersions();
	        
	        if (!fixVersions.isEmpty() && !commits.isEmpty()) {
	            while (fixVersions.size() > 1) {
	                String fix1 = fixVersions.get(0);
	                String fix2 = fixVersions.get(1);
	                int idFix1 = getReleaseId(releases, fix1);
	                int idFix2 = getReleaseId(releases, fix2);
	                
	                // keep the most recent fix version
	                if (idFix1 > idFix2) {
	                    fixVersions.remove(fix2);
	                    affectedVersions.add(fix2);
	                } else if (idFix1 < idFix2) {
	                    fixVersions.remove(fix1);
	                    affectedVersions.add(fix1);
	                }
	            }
	            validTickets.add(ticket);
	        }
	    }
	    
	    return validTickets;
	}
	
	// return the number of a release
	
	public static int getReleaseId(List<Release> releases, String version) {
	    for (Release release : releases) {
	        if (release.getVersion().equals(version)) {
	            return releases.indexOf(release);
	        }
	    }
	    return -1;
	}
	
	//compare affected version to fix version
	
	public static List<Ticket> compareAffecteVersionToFixVersion(List<Ticket> tickets, List<Release> releases) {

	    for (Ticket ticket : tickets) {
	        List<String> affectedVersions = ticket.getAffectedVersions();
	        List<String> fixVersions = ticket.getFixVersions();

	        if (!fixVersions.isEmpty()) {
	            String mostRecentFixVersion = fixVersions.get(fixVersions.size() - 1);
	            int idMostRecentFixVersion = getReleaseId(releases, mostRecentFixVersion);

	            for (String affectedVersion : affectedVersions) {
	                int idAffectedVersion = getReleaseId(releases, affectedVersion);

	                if (idAffectedVersion > idMostRecentFixVersion) {
	                    ticket.setAffectedVersions(new ArrayList<>());
	                    break;
	                }
	            }
	        }
	    }

	    return tickets;
	}
	
	//function that compute the iv's value
	
public static void findProportion(Ticket t) {
		
		String fixVersion = t.getFixVersions().get(0);
		String openingVersion = t.getOpeningVersion();
		
		int fv = getReleaseId(release, fixVersion) + 1;
		int ov = getReleaseId(release, openingVersion) + 1;
		
		//swap values if fv is less than ov
		if(fv < ov) {
			int temp = fv;
			fv = ov;
			ov = temp;
		}
		
		//compute the injected version
		int iv = Math.max((int) Math.floor(fv - (fv - ov) * p), 1);
		
		//add new affected versions to the ticket
		List<String> affectedVersions = new ArrayList<>();
		for(int i = iv-1; i < fv-1; i++) {
			affectedVersions.add(release.get(i).getVersion());
		}
		t.setAffectedVersions(affectedVersions);
	} 
	
	//compute the proportional number (calculate the moving window)
	
public static float calculateProportion(Ticket t) {
    int numAffected = t.getAffectedVersions().size();
    int numAnalyzedBugs = 1;
    float proportion = numAffected / (float) numAnalyzedBugs;
    
    
    
    return proportion;
}
	
	//creating the value that will be print in the file csv
	
public static void createDataset(List<Release> releases, List<Commit> commits, String projectName) throws FileNotFoundException {
	
	List<Result> results = new ArrayList<>();
	List<HashMap<String,Result>> releaseMaps = new ArrayList<>();
	List<FileCommitted> committedFiles = null;
	Commit currentCommit = null;
	
	LocalDate currentReleaseDate = null;
		
	LOGGER.info("Creating dataset...");
	
	for(int i = 0; i < releases.size()/2; i++) {
		
		currentReleaseDate = releases.get(i).getReleaseDate();
		
		HashMap<String, Result> releaseMap = new HashMap<>();
		releaseMaps.add(releaseMap);
		
		for(int j = 0; j < commits.size(); j++) {
			currentCommit = commits.get(j);
			if(currentCommit.getDate().compareTo(currentReleaseDate) < 0) {
				committedFiles = currentCommit.getCommitFile();
				if(!committedFiles.isEmpty()) {
					addResult(releaseMaps, currentCommit, committedFiles, i, results);
				}
			}
			else {
				break;
			}
		}
		
		commits.removeAll(results.get(results.size()-1).getCommitList());
		results.get(results.size()-1).getCommitList().clear();
		
	}
	
	computeBugginess(releaseMaps);
	
	writeDataset(projectName, results);
	
}

		
	//take file and analyse it or using proportion or using the affected version take from jira
	
private static void computeBugginess(List<HashMap<String, Result>> maps) {
	
	//Affected version
	
	int id;
	Commit commit;		
	List<FileCommitted> fileList = null;
	List<Ticket> ticketList;
	List<String> affectedVersions = null;
			
	numAffected = 0;
	numBugs = Math.round(tickets.size()/100);
	numAnalyseBugs = 0;

	ticketList = checkFixVersionTickets(tickets, release);

	compareAffecteVersionToFixVersion(ticketList, release);
	
	for (Ticket ticket : ticketList) {
		
		commit = ticket.getCommitFix();
		affectedVersions = ticket.getAffectedVersions();
		
		if (commit == null || ticket.getFixVersions().isEmpty()) {
			
			continue;
			
		} else {
			
			fileList = commit.getCommitFile();
					
			if (affectedVersions.isEmpty()) {
				
				// if there are no affected versions, use the proportion
				
				findProportion(ticket);
				calculateProportion(ticket);
										
			} else {
				
				calculateProportion(ticket);
				
				for (String version : affectedVersions) {

					id = getReleaseId(release, version);
					checkVersion(fileList, maps, false, id);	
					
				}			
				
			}						
			
		}					
		
		//fixed Version

		id = getReleaseId(release, ticket.getFixVersions().get(0));

		if (id < release.size()/2) {

			checkVersion(fileList, maps, true, id);

		}

	}
	
} 
	
	//check if a file is buggy or not

private static void checkVersion(List<FileCommitted> fileList, List<HashMap<String, Result>> maps, boolean updateFix, int releaseId) {
    Result result = null;
    // Check the specified release
    if (releaseId < release.size() / 2) {
        for (FileCommitted committedFile : fileList) {
            result = maps.get(releaseId).get(committedFile.getFilename());
            if (result != null) {
                result.setBuggy("Si");
                if (updateFix) {
                    result.addFix();
                }
            }
        }
    }
}
	
	//check the file and, if it's not new update info, else create the new result
		
private static void addResult(List<HashMap<String, Result>> maps, Commit commit, List<FileCommitted> fileList, int releaseIndex, List<Result> results) {
    for (FileCommitted file : fileList) {
        String filename = file.getFilename();
        Result result = maps.get(releaseIndex).get(filename);
        
        if (result == null) {
            // Create a new result object for this file in this release
            result = new Result(releaseIndex + 1, filename);
            maps.get(releaseIndex).put(filename, result);
            results.add(result);
        }
        
        // Update the metrics for this file in this release
        result.setSize(file.getSize());
        result.addLocTouched(file.getLineChange());
        result.addLocAdded(file.getLineAdded());
        result.addAuth(commit.getAuth());
        result.addRevision();
        result.addChgSetSize(fileList.size());
    }
}
	
	//Write the dataset

public static void writeDataset(String project, List<Result> resultList) throws FileNotFoundException {
    String outname = project + "DatasetInfo.csv";

    try (PrintWriter writer = new PrintWriter(new File(outname))) {
        writer.println("Release; File; Size; LocTouched; LocAdded; MaxLocAdded; AvgLocAdded; Nauth; Nfix; Nr; ChgSetSize; Buggy");

        for (Result result : resultList) {
            writer.printf("%d; %s; %d; %d; %d; %d; %.2f; %d; %d; %d; %d; %s%n",
                result.getRelease(), result.getFile(), result.getSize(), result.getLocTouched(), result.getLocAdded(),
                result.getMaxLocAdded(), result.getAvgLocAdded(), result.getAuth(), result.getnFix(), result.getnR(),
                result.getChgSetSize(), result.getBuggy());
        }
    } 
}
	
	/*function that permitte to start retrieve information about the project*/
	
public static void retrieveInfo(String projName, String token) throws JSONException, IOException, ParseException {
	
	//retrieve info about releases
	release = GetMetrics.getReleaseInfo(projName);
	
	//retrieve info about tickets
	tickets = GetMetrics.getTickets(projName, release);
			
	//associating release to tickets
	GetMetrics.associatingReleaseToTickets(release, tickets);
	
	//retrieve info about commits
	commits = GetMetrics.getCommits(projName, token, release);
	
	//associating commit to tickets
	GetMetrics.associatingCommitToTickets(tickets, commits);
	
	//retrieve info about files
	commitedFile = GetMetrics.getFile(commits, projName, token);
	
	//evaluation of the files' size
	GetMetrics.calculateSizes(commitedFile);
		
	//Create list of result
	createDataset(release, commits, projName);
	
	LOGGER.info("Dataset done!");

}


}