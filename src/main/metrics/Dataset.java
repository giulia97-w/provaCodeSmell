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
		
		
		
		
		
		
		release = GetMetrics.getReleaseInfo(PROJECT);
		
		//retrieve info about tickets
		tickets = GetMetrics.getTickets(PROJECT, release);
				
		//associating release to tickets
		GetMetrics.associatingReleaseToTickets(release, tickets);
		
		//retrieve info about commits
		commits = GetMetrics.getCommits(PROJECT, TOKEN, release);
		
		//associating commit to tickets
		GetMetrics.associatingCommitsToTickets(tickets, commits);
		
		//retrieve info about files
		commitedFile = GetMetrics.getCommittedFiles(commits, PROJECT,TOKEN);
		
		//evaluation of the files' size
		GetMetrics.countSize(commitedFile);
			
		//Create list of result
		writeOnCsv(release, commits, PROJECT);
		
		
	
	} 	

		
		static int bug;
		static float p;	
		static int totalBug;		
		static List<Ticket> tickets = null;		
		static List<Commit> commits = null;	
		static List<Release> release = null;
		static List<FileCommitted> commitedFile = null;
		static int numAffected;
		private Dataset() {}
		
		
		//controlla che il tiket sia valido ovvero che ha una data di fv e un committ associato. Se non presente il tiket non viene considerato
		private static boolean isTicketValid(Ticket ticket) {
		    return !ticket.getFixVersions().isEmpty() && !ticket.getCommitsTicket().isEmpty();
		}
		//rimuovi le incosistenze una volta verificata la validità per tutti i ticket della lista
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


		public static int releaseNumber(List<Release> releases, String version) {
		    for (Release release : releases) {
		        if (release.getVersion().equals(version)) {
		            return releases.indexOf(release);
		        }
		    }
		    return -1;
		}
		

		public static List<Ticket> verifyAffectedVersions(List<Ticket> tickets, List<Release> releases) {
		    for (Ticket ticket : tickets) {
		        List<String> fixVersions = ticket.getFixVersions();
		        if (!fixVersions.isEmpty()) { // se il ticket ha versioni di correzione
		            List<String> affectedVersions = ticket.getAffectedVersions();
		            for (String affectedVersion : affectedVersions) { // per ogni versione affetta
		                int fixVersionNumber = releaseNumber(releases, fixVersions.get(0));
		                int affectedVersionNumber = releaseNumber(releases, affectedVersion);
		                if (affectedVersionNumber > fixVersionNumber) { // se la versione affetta è maggiore della versione corretta
		                    ticket.setAffectedVersions(new ArrayList<>()); // rimuovi le versioni affette dal ticket
		                    break;
		                }
		            }
		        }
		    }
		    return tickets;
		}

		public static void setNewAV(Ticket t) {
			
			String fv = t.getFixVersions().get(0);
			String ov = t.getOpeningVersion();
			int fvId = releaseNumber(release, fv) + 1;
			int ovId = releaseNumber(release, ov) + 1;
			
			
			//se il numero di versione fixata è minore della versione open allora uguaglio fv e ov
			if(fvId<ovId) {
	    		int versionId = ovId;
	    		ovId = fvId;
	    		fvId = versionId;	
	    	}
			//nuovo iv calcolato come fv - (fv - ov) * p (calcolato)
			int iv = (int) Math.floor(fvId-(fvId-ovId)*p);
			
	    	if(iv < 0) {	
	    		iv = 1;
	    	}
	    	//aggiungi av
			for(int i=iv-1; i<fvId-1; i++) {
				t.addAffectedVersion(release.get(i).getVersion());
			}
			
		}

		public static float calculateAffectedVersionProportion(List<Ticket> tickets) {
			int totalAffectedVersions = tickets.stream()
			.mapToInt(ticket -> ticket.getAffectedVersions().size())
			.sum();
			int totalTickets = tickets.size();
			
			return totalTickets > 0 ? (float) totalAffectedVersions / totalTickets : 0.0f;
		}

		//confronto tra la data del committ e la data della prima release se la data del commit è antecedente si passa all'oggetto commit successivo
		//altrimenti viene creato un indice i che viene decrementato finché la data dell'oggetto release corrente nella lista è antecedente alla data 
		//del commit. l'indice i indica l'indice dell'ultima release rilasciata prima della data dell'oggetto commit (altrimenti non avrebbe senso).
		
		public static void writeOnCsv(List<Release> lists, List<Commit> c, String project) throws FileNotFoundException {
			List<Result> list = new ArrayList<>();
			List<HashMap<String, Result>> listRes = new ArrayList<>();

			for (int i = 0; i < list.size() / 2; i++) {
			    listRes.add(new HashMap<String, Result>());
			}
			for (Commit com : c) {
			    LocalDate date = com.getDate();

			    if (date.compareTo(lists.get(0).getReleaseDate()) < 0) {
			        continue;
			    }
			    int i = 0;
			    for (Release release : lists) {
			        if (date.compareTo(release.getReleaseDate()) < 0) {
			            break;
			        }
			        i++;
			    }
			    i--; 
			    List<FileCommitted> fileList = com.getCommitFile();
			    if (!fileList.isEmpty()) {
			        addResult(listRes, com, fileList, i, list);
			    }
			}
			isBuggy(listRes);
			getDs(project, list);
		}

			
		
		private static void isBuggy(List<HashMap<String, Result>> maps) {
		    
		    
		    List<Ticket> list = removeInconsistence(tickets);
		    
		    verifyAffectedVersions(list, release);
		    //controllo dei commit, av e fv, se av è vuota la si setta. altrimenti per ogni versione affetta si setta come buggy.
		    for (Ticket ticket : tickets) { {
		        Commit com = ticket.getCommitFix();
		        List<String> av = ticket.getAffectedVersions();
		        List<String> fv = ticket.getFixVersions();
		        List<FileCommitted> fileList = com.getCommitFile();
		        
		        if (av.isEmpty()) {
		            setNewAV(ticket);
		            calculateAffectedVersionProportion(list);
		        }
		        
		        for (String version : av) {
		            int id = releaseNumber(release, version);
		            if (id < release.size() / 2) { //metà delle release
		                markFilesInReleaseAsBuggy(fileList, maps, false, id);
		            }
		        }
		        
		        if (!fv.isEmpty()) {
		            String fixVersion = fv.get(0);
		            int id = releaseNumber(release, fixVersion);
		            if (id < release.size() / 2) {
		                markFilesInReleaseAsBuggy(fileList, maps, true, id);
		            }
		        }}}
		    
		}

		
		//setta i file come buggy 
		private static void markFilesInReleaseAsBuggy(List<FileCommitted> fileList, List<HashMap<String, Result>> releaseMaps, boolean updateFixes, int releaseId) {
		    HashMap<String, Result> releaseResults = releaseMaps.get(releaseId);

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

		

		public static void getDs(String projectName, List<Result> resultList) throws FileNotFoundException {
		    String filename = projectName + ".csv";
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

		

		
		
	
