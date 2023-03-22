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
		GetMetrics.associatingCommitsToTickets(tickets, commits);
		
		//retrieve info about files
		commitedFile = GetMetrics.getFile(commits, project, token);
		
		//evaluation of the files' size
		GetMetrics.countSize(commitedFile);
			
		//Create list of result
		writeOnCsv(release, commits, project);
		
		
	
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

		public static List<Ticket> removeInconsistence(List<Ticket> tickets) {
		    List<String> fv;
		    List<String> av;

		    for (int i = 0; i < tickets.size(); i++) {
		        Ticket ticket = tickets.get(i);

		        if (!isTicketValid(ticket)) {
		            tickets.remove(ticket);
		            continue;
		        }

		        fv = ticket.getFixVersions();
		        av = ticket.getAffectedVersions();


		        ticket.setFixVersions(fv);
		        ticket.setAffectedVersions(av);
		    }

		    return tickets;
		}

			
			

		
		// return the number of a release
		
		public static int releaseNumber(List<Release> releases, String version) {
		    for (Release release : releases) {
		        if (release.getVersion().equals(version)) {
		            return releases.indexOf(release);
		        }
		    }
		    return -1;
		}
		
		
		
		
		//compare affected version to fix version
		
		public static List<Ticket> verifyAv(List<Ticket> tickets, List<Release> release) {
		    for (int i = 0; i < tickets.size(); i++) {
		        Ticket ticket = tickets.get(i);
		        List<String> fixVersion = ticket.getFixVersions();
		        if (!fixVersion.isEmpty()) {
		            for (int j = 0; j < ticket.getAffectedVersions().size(); j++) {
		                String av = ticket.getAffectedVersions().get(j);
		                int fvId = releaseNumber(release, fixVersion.get(0));
		                int avId = releaseNumber(release, av);
		                if (avId > fvId) {
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
			int fvId = releaseNumber(release, fv) + 1;
			int ovId = releaseNumber(release, ov) + 1;
			
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
		
		public static float proportion(List<Ticket> tickets) {
		    int totalAffected = 0;
		    int totalTickets = tickets.size();
		    
		    for (Ticket ticket : tickets) {
		        totalAffected += ticket.getAffectedVersions().size();
		    }
		    
		    if (totalTickets > 0) {
		        return (float) totalAffected / totalTickets;
		    } else {
		        return 0.0f;
		    }
		}


		
		//creating the value that will be print in the file csv
		
		public static void writeOnCsv(List<Release> lists, List<Commit> c, String project) throws FileNotFoundException {
			List<Result> list = new ArrayList<>();
			List<HashMap<String, Result>> listRes = new ArrayList<>();

		    // create a hashmap for each release
		
			for (int i = 0; i < list.size() / 2; i++) {
			    listRes.add(new HashMap<String, Result>());
			}

			// add commit results to release hashmaps
			for (Commit com : c) {
			    LocalDate date = com.getDate();

			    // check if commit is before the first release
			    if (date.compareTo(lists.get(0).getReleaseDate()) < 0) {
			        continue;
			    }

			    // find the release index for the commit
			    int i = 0;
			    for (Release release : lists) {
			        if (date.compareTo(release.getReleaseDate()) < 0) {
			            break;
			        }
			        i++;
			    }
			    i--; // use the previous release index

			    List<FileCommitted> fileList = com.getCommitFile();
			    if (!fileList.isEmpty()) {
			        addResult(listRes, com, fileList, i, list);
			    }
			}

			isBuggy(listRes);

			// write the dataset to a .csv file
			getDs(project, list);
		}

			
		//take file and analyse it or using proportion or using the affected version take from jira
		
		private static void isBuggy(List<HashMap<String, Result>> maps) {
		    
		    
		    // Lista di ticket filtrati per la versione di fix
		    List<Ticket> list = removeInconsistence(tickets);
		    
		    // Confronta la versione di fix con le versioni colpite e aggiorna il ticket di conseguenza
		    verifyAv(list, release);
		    
		    for (int i = 0; i < list.size(); i++) {
		        Ticket ticket = list.get(i);
		        Commit com = ticket.getCommitFix();
		        List<String> av = ticket.getAffectedVersions();
		        List<String> fv = ticket.getFixVersions();
		        
		        // Se il commit è null o la lista di versioni di fix è vuota, passa al prossimo ticket
		        
		        
		        List<FileCommitted> fileList = com.getCommitFile();
		        
		        // Se non ci sono versioni colpite, calcola la proporzione e aggiorna il ticket
		        if (av.isEmpty()) {
		            setNewAV(ticket);
		            proportion(list);
		        }
		        
		        // Controlla la versione colpita e aggiorna la lista dei risultati
		        for (String version : av) {
		            int id = releaseNumber(release, version);
		            if (id < release.size() / 2) {
		                markFilesInReleaseAsBuggy(fileList, maps, false, id);
		            }
		        }
		        
		        // Controlla la versione di fix e aggiorna la lista dei risultati
		        if (!fv.isEmpty()) {
		            String fixVersion = fv.get(0);
		            int id = releaseNumber(release, fixVersion);
		            if (id < release.size() / 2) {
		                markFilesInReleaseAsBuggy(fileList, maps, true, id);
		            }
		        }
		    }
		}

		
		//check if a file is buggy or not

		private static void markFilesInReleaseAsBuggy(List<FileCommitted> fileList, List<HashMap<String, Result>> releaseMaps, boolean updateFixes, int releaseId) {
		    // Get the map of results for the given release
		    HashMap<String, Result> releaseResults = releaseMaps.get(releaseId);

		    // Iterate over each committed file and mark it as buggy if it is in the given release
		    for (FileCommitted file : fileList) {
		        Result result = releaseResults.get(file.getFilename());
		        if (result != null) {
		            result.setBuggy("Si");
		            if (updateFixes) {
		                result.addFix();
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

		public static void getDs(String projectName, List<Result> resultList) throws FileNotFoundException {
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

		

		
		
	
