package metrics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import entities.Commit;
import entities.FileCommitted;
import entities.Release;
import entities.Ticket;


public final class GetMetrics {
	
	private GetMetrics() {}

	private static final Logger LOGGER = Logger.getLogger(GetMetrics.class.getName());
	public static final String DATE = "yyyy-MM-dd";
	 
	
	/* function that retrieve information about the release of the project */
		
	public static List<Release> getReleaseInfo(String projName) throws JSONException, IOException, ParseException{
		
		List<Release> release = new ArrayList<>();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE);
		
		//Jira
		
		String url = "https://issues.apache.org/jira/rest/api/2/project/" + projName;
		
		JSONObject json = readJsonFromUrl(url);
		
		JSONArray versions = json.getJSONArray("versions");
		
		String strdate = null;
		String formattedDate = null;
		LocalDate date = null;
		String id = null;
		String versionName = null;
		for (int i = 0; i < versions.length(); i++ ) {
			
			
			if(versions.getJSONObject(i).has("releaseDate")) {
				if(versions.getJSONObject(i).has("name"))
					versionName = versions.getJSONObject(i).get("name").toString();
				if(versions.getJSONObject(i).has("id"))
					id = versions.getJSONObject(i).get("id").toString();

				strdate = versions.getJSONObject(i).get("releaseDate").toString();
				
				formattedDate = strdate.substring(0,10);
				
				date = LocalDate.parse(formattedDate, formatter);
				
				
							
				Release r = new Release(id, date, versionName);
				
				release.add(r);
			
			}
		         
		}
		
		// order releases by date
		
		Collections.sort(release, (Release o1, Release o2) -> o1.getReleaseDate().compareTo(o2.getReleaseDate()));
				   
		
		
						
		return release;
		
	}
	
	/* function that retrieve information about the ticket that have the date previous than 
	 * 
	 * the date of the release date (the first half of the project) */
		
	public static List<Ticket> getTickets(String projName, List<Release> release) throws JSONException, IOException{
				  
		//Searchs for all the tickets of type 'Bug' which have been resolved/closed
		      
		List<Ticket> tickets = new ArrayList<>();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE);

		Integer j = 0;
		Integer i = 0;
		int total = 1;
		String key = null;
		List<String> avL = null;
		List<String> fvL = null;
		JSONObject field = null;
		String formatted = null;
		LocalDate createdDate = null;
		LocalDate releaseDate =null;
		JSONArray av  = null;
		String vn = null;
		JSONArray fv = null;
		String fvn = null;
		do {
		
			//Only gets a max of 1000 at a time, so must do this multiple times if >1000
	    
			j = i + 1000;
		    
			//Url for found all Tickets related to bug
		       	    
			String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"		    
					+ projName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
		            + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key," 
		            + "resolutiondate,versions,fixVersions,created&startAt=" + i.toString() + "&maxResults=" + j.toString();        
		         
			JSONObject json = readJsonFromUrl(url);
		    
			JSONArray issues = json.getJSONArray("issues");
		    
			total = json.getInt("total");
			
		    
			for (; i < total && i < j; i++) {
				
				//retrieve information from tickets
				
				
	        	field =issues.getJSONObject(i%1000).getJSONObject("fields");
				key = issues.getJSONObject(i%1000).get("key").toString();
				formatted = field.get("created").toString().substring(0,10);
				createdDate = LocalDate.parse(formatted, formatter);
				avL = new ArrayList<>();
				fvL = new ArrayList<>();
				releaseDate = release.get(release.size()/2).getReleaseDate();
				
				//compare the ticket's date to the release's date
				
				if(createdDate.compareTo(releaseDate)<0) {
					
					/*if the ticket's date is previous than the release's date (the first half of the project), 
					 * 
					 * the fuction get all information about the ticket and it is added to the list*/
						
					av = field.getJSONArray("versions");
	
					for(int k=0; k<av.length(); k++) {
							
						vn = av.getJSONObject(k).get("name").toString();
													
						avL.add(vn);
		
					}	
	
					fv = field.getJSONArray("fixVersions");
						
					for(int z=0; z<fv.length(); z++) {

						fvn = fv.getJSONObject(z).get("name").toString();
		
						fvL.add(fvn);
						
					}
										
					Ticket t = new Ticket(key, avL, fvL, createdDate); 
					
					//Adds the new ticket to the list

					tickets.add(t);	
						
					}
		               
			}    
		    
		} while (i < total);
		
		// order releases by date
		
		Collections.sort(tickets, (Ticket o1, Ticket o2) -> o1.getCreatedDate().compareTo(o2.getCreatedDate()));
		
		     
		return tickets;
		  
	}

	/* fuction that retrieve the information about commits that have the date previous than 
	 * 
	 * the release's date (the first half of the project)*/
	
	public static List<Commit> getCommits(String projName, String token, List<Release> release) throws IOException, ParseException {
		
		LOGGER.info("Searching commit...");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE);

		List<Commit> commits = new ArrayList<>();
	   
		int page = 1;
		JSONArray comm;
		   
		while(true) {
			   
			//Takes all commits
			     
			String url = "https://api.github.com/repos/apache/"+ projName +"/commits?&per_page=100&page="+ page;
			  
			try{
				
				comm = jsonArrayFromUrl(url, token);
		    	   	         
			}catch(Exception e) {
				
				LOGGER.severe(e.toString());
				  
				return commits;
				    
			}
			
			int total = comm.length();
		    	
			int i = 0;
		      
			if(total == 0) {
		    	  
				break;
		      }
			
			 for(i=0; i<total; i++) {
		        	
				   JSONObject commit = comm.getJSONObject(i).getJSONObject("commit");
				   String sha = comm.getJSONObject(i).get("sha").toString();
				   String author = commit.getJSONObject("author").get("name").toString();
				   String message = commit.get("message").toString();
				   
				   
				   String formattedDate = commit.getJSONObject("committer").get("date").toString().substring(0,10);
					
				   LocalDate date = LocalDate.parse(formattedDate, formatter);					
					
				   LocalDate releaseDate = release.get(release.size()/2).getReleaseDate();
				   
				   //Take the commit from the first half of the project
										
				   if(date.compareTo(releaseDate)<0) {
				   
					   Commit c = new Commit(message, formattedDate, author, sha);
					   
					   //Adds the new commit to the list
					   
					   commits.add(c);

				   }

				   i++;
			            
			   }
			 
			 page++;
		
		}
		
		
		
		
		Collections.sort(commits, (Commit o1, Commit o2) -> o1.getDate().compareTo(o2.getDate()));
		
		return commits;
		
	}
	
	/* if a ticket is found in the commit, the function add this commit in the list commitsTicket and search the fix commit*/
	
	public static void associatingCommitsToTickets(List<Ticket> tickets, List<Commit> commits) {

	    for (Commit commit : commits) {
	        String message = commit.getMessage();

	        for (Ticket ticket : tickets) {
	            String ticketId = ticket.getId();

	            // If a ticket ID is found in the commit message, the commit is associated with the ticket
	            if (message.contains(ticketId)) {
	                ticket.addCommit(commit);
	                ticket.setCommitFix(commit);
	            }
	        }
	    }
	}

	
	//get info of the files for all commit
	
	public static List<FileCommitted> getCommittedFiles(List<Commit> commits, String projectName, String token) throws UnsupportedEncodingException {
	    List<FileCommitted> committedFiles = new ArrayList<>();
	    LOGGER.info("Searching committed files...");
	    
	    for (Commit commit : commits) {
	        String sha = commit.getSha();
	        String url = "https://api.github.com/repos/apache/" + projectName + "/commits/" + sha;
	        JSONObject conn;
	        
	        try {
	            conn = jsonFromUrl(url, token);
	        } catch (Exception e) {
	            LOGGER.log(Level.SEVERE, "[ERROR]", e);
	            break;
	        }
	        
	        JSONArray files = conn.getJSONArray("files");
	        
	        for (int i = 0; i < files.length(); i++) {
	            JSONObject fileObj = files.getJSONObject(i);
	            String fileName = fileObj.getString("filename");
	            
	            // Consider only Java files
	            if (fileName.endsWith(".java")) {
	                int changes = fileObj.getInt("changes");
	                int deletions = fileObj.getInt("deletions");
	                int additions = fileObj.getInt("additions");
	                LocalDate commitDate = commit.getDate();
	                String contentsUrl = fileObj.getString("contents_url");
	                JSONObject contentsObj;
	                
	                try {
	                    contentsObj = jsonFromUrl(contentsUrl, token);
	                } catch (Exception e) {
	                    LOGGER.log(Level.SEVERE, "[ERROR]", e);
	                    break;
	                }
	                
	                String content = contentsObj.getString("content");
	                byte[] decodedBytes = Base64.getMimeDecoder().decode(content);
	                String decodedContent = new String(decodedBytes, StandardCharsets.UTF_8);
	                
	                FileCommitted fileCommitted = new FileCommitted(fileName, changes, deletions, additions, commitDate, contentsUrl, decodedContent);
	                commit.addCommitFile(fileCommitted);
	                committedFiles.add(fileCommitted);
	            }
	        }
	        
	        LOGGER.info(commit.getCommitFile().size() + " committed files (.java) found for commit " + sha);
	    }
		
		LOGGER.info(committedFiles.size() + " committed files found for all commits!");
		
		return committedFiles;
		
	}
	
	
	//associating opened version to the ticket
	
	public static void associatingReleaseToTickets(List<Release> release, List<Ticket> tickets) {
		
		LocalDate firstReleaseDate = release.get(0).getReleaseDate();
		LocalDate lastReleaseDate = release.get(release.size()-1).getReleaseDate();
		
		Collections.sort(tickets, Comparator.comparing(Ticket::getCreatedDate));
		
		for (int i = 0; i < release.size()-1; i++) {
			LocalDate currentReleaseDate = release.get(i).getReleaseDate();
			LocalDate nextReleaseDate = release.get(i+1).getReleaseDate();
			
			for(int j = 0; j<tickets.size(); j++) {
				LocalDate ticketCreatedDate = tickets.get(j).getCreatedDate();
				
				if(ticketCreatedDate.compareTo(firstReleaseDate) < 0) {
					tickets.get(j).setOpeningVersion(release.get(0).getVersion());
				} else if(ticketCreatedDate.compareTo(lastReleaseDate) > 0) {
					tickets.get(j).setOpeningVersion(release.get(release.size()-1).getVersion());
				} else if(ticketCreatedDate.compareTo(currentReleaseDate) >= 0 && ticketCreatedDate.compareTo(nextReleaseDate) < 0) {
					tickets.get(j).setOpeningVersion(release.get(i+1).getVersion());
				}
			}
		}
	}

	//Returns the number of LOC in a file
	
	public static void countSize(List<FileCommitted> fileList) {
		
		for(int i=0; i < fileList.size(); i++) {
			
			FileCommitted file = fileList.get(i);
			String content = file.getContent();
			
			String[] lines = content.split("\n");
			int loc = lines.length;
			
			int comment = 0;
			boolean inComment = false;
			for (String line : lines) {
				line = line.trim();
				if (line.startsWith("//") || (inComment && line.endsWith("*/"))) {
					comment++;
					inComment = false;
				} else if (line.startsWith("/*")) {
					comment++;
					inComment = true;
				} else if (inComment) {
					comment++;
				}
			}
			
			int size = loc - comment;
			file.setSize(size);			
			
		}
		
	}

	
	//Returns the number of comments in a file
	
	public static int countComment(FileCommitted file) {

		int count = 0;
		String[] lines = file.getContent().split("\n");

		boolean inSingleLineComment = false;
		boolean inMultiLineComment = false;

		for (String line : lines) {
		    line = line.trim();

		    if (line.startsWith("//")) {
		        inSingleLineComment = true;
		        count++;
		    } else if (line.startsWith("/*")) {
		        inMultiLineComment = true;
		        count++;
		    }

		    if (inSingleLineComment && !line.contains("/*")) {
		        inSingleLineComment = false;
		    }

		    if (inMultiLineComment) {
		        count++;
		        if (line.endsWith("*/")) {
		            inMultiLineComment = false;
		        }
		    }
		}

		return count;}


	
	
	// count the size of a file
	
	
	
public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
	    
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod("GET");
		    
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			return new JSONObject(response.toString());
		} finally {
			connection.disconnect();
		}
	}

	
	public static JSONObject jsonFromUrl(String url, String token) throws IOException {
	    URL url2 = new URL(url);
	    HttpURLConnection connection = (HttpURLConnection) url2.openConnection();

	    connection.setRequestMethod("GET");
	    connection.setRequestProperty("Accept", "application/vnd.github.cloak-preview");
	    connection.setRequestProperty("Authorization", "token "+ token);

	    int responseCode = connection.getResponseCode();

	    if (responseCode != 200) {
	        throw new HttpErrorException("HTTP error code: " + responseCode);
	    }

	    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
	    StringBuilder responseBuilder = new StringBuilder();

	    String line;
	    while ((line = reader.readLine()) != null) {
	        responseBuilder.append(line);
	    }

	    reader.close();
	    connection.disconnect();

	    return new JSONObject(responseBuilder.toString());
	}

	public static class HttpErrorException extends RuntimeException {
	    public HttpErrorException(String message) {
	        super(message);
	    }
	}

	   
	public static String readAll(Reader rd) throws IOException {
	    StringWriter writer = new StringWriter();
	    char[] buffer = new char[4096];
	    int n;
	    while ((n = rd.read(buffer)) != -1) {
	        writer.write(buffer, 0, n);
	    }
	    return writer.toString();
	}

	
	public static JSONArray jsonArrayFromUrl(String url, String token) throws IOException {
	    URL url2 = new URL(url);
	    HttpURLConnection urlConnection = (HttpURLConnection) url2.openConnection();

	    // Setting the requirements to access the Github API
	    urlConnection.setRequestProperty("Accept", "application/vnd.github.cloak-preview");
	    urlConnection.setRequestProperty("Authorization", "token " + token);

	    try (BufferedReader rd = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), StandardCharsets.UTF_8))) {
	        StringBuilder sb = new StringBuilder();
	        int cp;
	        while ((cp = rd.read()) != -1) {
	            sb.append((char) cp);
	        }
	        String jsonText = sb.toString();
	        return new JSONArray(jsonText);
	    } catch (JSONException e) {
	        throw new JsonParsingException("Failed to parse JSON array from URL: " + url, e);
	    } finally {
	        urlConnection.disconnect();
	    }
	}

	public static class JsonParsingException extends RuntimeException {
	    public JsonParsingException(String message, Throwable cause) {
	        super(message, cause);
	    }
	}

}