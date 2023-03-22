package metrics;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
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
		
		release = GetMetrics.getReleaseInfo(project);
		
		//retrieve info about tickets
		tickets = GetMetrics.getTickets(project, release);
				
		//associating release to tickets
		GetMetrics.associatingReleaseToTickets(release, tickets);
		
		//retrieve info about commits
		commits = GetMetrics.getCommits(project, token, release);
		
		//associating commit to tickets
		GetMetrics.associatingCommitToTickets(tickets, commits);
		
		//retrieve info about files
		commitedFile = GetMetrics.getFile(commits, project, token);
		
		//evaluation of the files' size
		GetMetrics.countSize(commitedFile);
			
		//Create list of result
		createDataset(release, commits, project);
		
		
	
	} 	
	
	
		
		
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
		
		private static boolean isTicketValid(Ticket ticket) {
		    return !ticket.getFixVersions().isEmpty() && !ticket.getCommitsTicket().isEmpty();
		}

		public static List<Ticket> checkFixVersionTickets(List<Ticket> tickets, List<Release> release) {
		    List<String> fixVersion;
		    List<String> affectedVersion;

		    int idFix1;
		    int idFix2;
		    String fix1;
		    String fix2;

		    for (int i = 0; i < tickets.size(); i++) {
		        Ticket ticket = tickets.get(i);

		        if (!isTicketValid(ticket)) {
		            tickets.remove(ticket);
		            continue;
		        }

		        fixVersion = ticket.getFixVersions();
		        affectedVersion = ticket.getAffectedVersions();

		        while (fixVersion.size() > 1) {
		            fix1 = fixVersion.get(0);
		            fix2 = fixVersion.get(1);

		            idFix1 = getReleaseId(release, fix1);
		            idFix2 = getReleaseId(release, fix2);

		            if (idFix1 > idFix2) {
		                fixVersion.remove(fix2);
		                affectedVersion.add(fix2);
		            } else if (idFix1 < idFix2) {
		                fixVersion.remove(fix1);
		                affectedVersion.add(fix1);
		            }
		        }
		    }

		    return tickets;
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
		
		public static List<Ticket> compareAffecteVersionToFixVersion(List<Ticket> tickets, List<Release> release) {
		    for (int i = 0; i < tickets.size(); i++) {
		        Ticket ticket = tickets.get(i);
		        List<String> fixVersion = ticket.getFixVersions();
		        if (!fixVersion.isEmpty()) {
		            for (int j = 0; j < ticket.getAffectedVersions().size(); j++) {
		                String affectedVersion = ticket.getAffectedVersions().get(j);
		                int idFix = getReleaseId(release, fixVersion.get(0));
		                int idAffected = getReleaseId(release, affectedVersion);
		                if (idAffected > idFix) {
		                    ticket.setAffectedVersions(new ArrayList<>());
		                    break;
		                }
		            }
		        }
		    }
		    return tickets;
		}

		

		
		//function that compute the iv's value
		
		public static void setNewAV(Ticket t) {
			
			String fv = t.getFixVersions().get(0);
			String ov = t.getOpeningVersion();
			int fvId = getReleaseId(release, fv) + 1;
			int ovId = getReleaseId(release, ov) + 1;
			
			//check value of fv and ov
			
			if(fvId<ovId) {
	    		int versionId = ovId;
	    		ovId = fvId;
	    		fvId = versionId;	
	    	}
			
			//compute injected version
			int iv = (int) Math.floor(fvId-(fvId-ovId)*p);
			//Checks if the value is negative
	    	if(iv < 0) {	
	    		iv = 1;
	    	}
			
	    	//Add new affected version to the ticket
	    	
			for(int i=iv-1; i<fvId-1; i++) {
				t.addAffectedVersion(release.get(i).getVersion());
			}
			
		}
		
		//compute the proportional number (calculate the moving window)
		
		public static float calculateProportion(List<Ticket> tickets) {
		    int numAffected = 0;
		    int numAnalyseBugs = 0;
		    int numBugs = tickets.size();
		    float p = 0.0f;

		    for (Ticket t : tickets) {
		        numAffected += t.getAffectedVersions().size();
		        numAnalyseBugs++;

		        if (numAnalyseBugs == numBugs) {
		            p = numAffected / (float) numAnalyseBugs;
		            numAnalyseBugs = 0;
		            numAffected = 0;
		        }
		    }

		    return p;
		}

		
		//creating the value that will be print in the file csv
		
		public static void createDataset(List<Release> releases, List<Commit> commits, String projectName) throws FileNotFoundException {
		    List<Result> results = new ArrayList<>();
		    List<HashMap<String, Result>> releaseResults = new ArrayList<>();

		    // create a hashmap for each release
		    for (int i = 0; i < releases.size() / 2; i++) {
		        releaseResults.add(new HashMap<String, Result>());
		    }

		    // add commit results to release hashmaps
		    for (Commit c : commits) {
		        LocalDate commitDate = c.getDate();

		        // check if commit is before the first release
		        if (commitDate.compareTo(releases.get(0).getReleaseDate()) < 0) {
		            continue;
		        }

		        // find the release index for the commit
		        int releaseIndex = 0;
		        for (Release r : releases) {
		            if (commitDate.compareTo(r.getReleaseDate()) < 0) {
		                break;
		            }
		            releaseIndex++;
		        }
		        releaseIndex--; // use the previous release index

		        List<FileCommitted> fileList = c.getCommitFile();
		        if (!fileList.isEmpty()) {
		            addResult(releaseResults, c, fileList, releaseIndex, results);
		        }
		    }

		    computeBugginess(releaseResults);

		    // write the dataset to a .csv file
		    writeDataset(projectName, results);
		}

			
		//take file and analyse it or using proportion or using the affected version take from jira
		
		private static void computeBugginess(List<HashMap<String, Result>> maps) {
		    
		    
		    // Lista di ticket filtrati per la versione di fix
		    List<Ticket> ticketList = checkFixVersionTickets(tickets, release);
		    
		    // Confronta la versione di fix con le versioni colpite e aggiorna il ticket di conseguenza
		    compareAffecteVersionToFixVersion(ticketList, release);
		    
		    for (int i = 0; i < ticketList.size(); i++) {
		        Ticket ticket = ticketList.get(i);
		        Commit commitFix = ticket.getCommitFix();
		        List<String> affectedVersions = ticket.getAffectedVersions();
		        List<String> fixVersions = ticket.getFixVersions();
		        
		        // Se il commit è null o la lista di versioni di fix è vuota, passa al prossimo ticket
		        
		        
		        List<FileCommitted> fileList = commitFix.getCommitFile();
		        
		        // Se non ci sono versioni colpite, calcola la proporzione e aggiorna il ticket
		        if (affectedVersions.isEmpty()) {
		            setNewAV(ticket);
		            calculateProportion(ticketList);
		        }
		        
		        // Controlla la versione colpita e aggiorna la lista dei risultati
		        for (String version : affectedVersions) {
		            int id = getReleaseId(release, version);
		            if (id < release.size() / 2) {
		                checkVersion(fileList, maps, false, id);
		            }
		        }
		        
		        // Controlla la versione di fix e aggiorna la lista dei risultati
		        if (!fixVersions.isEmpty()) {
		            String fixVersion = fixVersions.get(0);
		            int id = getReleaseId(release, fixVersion);
		            if (id < release.size() / 2) {
		                checkVersion(fileList, maps, true, id);
		            }
		        }
		    }
		}

		
		//check if a file is buggy or not

		private static void checkVersion(List<FileCommitted> fileList, List<HashMap<String, Result>> maps, boolean updateFix, int releaseId) {
		    Result r = null;

		    //Iterate over each committed file and check if it is in the given release
		    for(FileCommitted file : fileList) {
		        r = maps.get(releaseId).get(file.getFilename());

		        if(r != null) {
		            //The file is in the release, set it as buggy and update the number of fixes if necessary
		            r.setBuggy("Si");
		            if(updateFix) {
		                r.addFix();
		            }
		        }
		    }
		}

		
		//check the file and, if it's not new update info, else create the new result
			
		private static void addResult(List<HashMap<String, Result>> maps, Commit c, List<FileCommitted> fileList, int i, List<Result> result) {
		    for (int j = 0; j < fileList.size(); j++) {
		        FileCommitted f = fileList.get(j);
		        Result r = getResult(maps, f.getFilename(), i, result);
		        updateResult(r, f, c, fileList.size());
		    }
		}

		private static Result getResult(List<HashMap<String, Result>> maps, String filename, int releaseIndex, List<Result> resultList) {
		    Result result = maps.get(releaseIndex).get(filename);
		    if (result == null) {
		        result = new Result(releaseIndex + 1, filename);
		        maps.get(releaseIndex).put(filename, result);
		        resultList.add(result);
		    }
		    return result;
		}

		private static void updateResult(Result result, FileCommitted fileCommitted, Commit commit, int chgSetSize) {
		    result.setSize(fileCommitted.getSize());
		    result.addLocTouched(fileCommitted.getLineChange());
		    result.addLocAdded(fileCommitted.getLineAdded());
		    result.addAuth(commit.getAuth());
		    result.addRevision();
		    result.addChgSetSize(chgSetSize);
		}

		
		//Write the dataset

		public static void writeDataset(String projectName, List<Result> resultList) throws FileNotFoundException {
		    String filename = projectName + "DatasetInfo.csv";
		    try (PrintStream printer = new PrintStream(new File(filename))) {
		        printer.println("Release; File; Size; LocTouched; LocAdded; MaxLocAdded; AvgLocAdded; Nauth; Nfix; Nr; ChgSetSize; Buggy");

		        for (Result result : resultList) {
		            printer.println(result.getRelease() + ";" + result.getFile() + ";" + result.getSize() + ";" + result.getLocTouched() 
		                + ";" + result.getLocAdded()  + ";" + result.getMaxLocAdded() + ";" + String.format("%.2f", result.getAvgLocAdded()) + ";" 
		                + result.getAuth() + ";" + result.getnFix() + ";" + result.getnR() + ";" + result.getChgSetSize() 
		                + ";" + result.getBuggy());
		        }
		    } 
		    }
		}

		
		/*function that permitte to start retrieve information about the project*/
		
		
	
