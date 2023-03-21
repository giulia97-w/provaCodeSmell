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
	public static final String NAMEPROJECT = "BOOKKEEPER";
	private GetMetrics() {}

	private static final Logger LOGGER = Logger.getLogger(GetMetrics.class.getName());

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
	/* function that retrieve information about the release of the project */
		
public static List<Release> getReleaseInfo(String projName) throws JSONException, IOException, ParseException {
		
		List<Release> release = new ArrayList<>();
		
		LOGGER.info("Searching release...");
		
		// Get release information using JIRA API
		
		String url = "https://issues.apache.org/jira/rest/api/2/project/" + NAMEPROJECT;
		
		JSONObject json = readJsonFromUrl(url);
		
		JSONArray versions = json.getJSONArray("versions");
		
		// Process only the releases that have a release date
		
		for (int i = 0; i < versions.length(); i++) {
			
			JSONObject version = versions.getJSONObject(i);
			
			if (version.has("releaseDate")) {
				
				String strdate = version.getString("releaseDate").substring(0, 10);
				LocalDate date = LocalDate.parse(strdate, formatter);
				
				String id = version.getString("id");
				String versionName = version.getString("name");
							
				Release r = new Release(id, date, versionName);
				release.add(r);
			
			}
		         
		}
		
		// Sort releases by date
		
		release.sort(Comparator.comparing(Release::getReleaseDate));
				   
		if (release.size() < 6) {
			LOGGER.info(release.size() + " release found!");
			return release;
		}
		
		LOGGER.info(release.size()/2 +" releases found!");
						
		return release;
		
	}

	
	/* function that retrieve information about the ticket that have the date previous than 
	 * 
	 * the date of the release date (the first half of the project) */
		
	public static List<Ticket> getTickets(String projName, List<Release> release) throws JSONException, IOException{
				  
		//Searchs for all the tickets of type 'Bug' which have been resolved/closed
		      
		List<Ticket> tickets = new ArrayList<>();
		
		Integer j = 0;
		Integer i = 0;
		int total = 1;
	
		do {
		
			//Only gets a max of 1000 at a time, so must do this multiple times if >1000
	    
			j = i + 1000;
		    
			//Url for found all Tickets related to bug
		       	    
			String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"		    
					+ NAMEPROJECT + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
		            + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key," 
		            + "resolutiondate,versions,fixVersions,created&startAt=" + i.toString() + "&maxResults=" + j.toString();        
		         
			JSONObject json = readJsonFromUrl(url);
		    
			JSONArray issues = json.getJSONArray("issues");
		    
			total = json.getInt("total");
			
			LOGGER.info("Searching tickets...");
		    
			for (; i < total && i < j; i++) {
				
				//retrieve information from tickets
				
				JSONObject ticket = issues.getJSONObject(i%1000);
	        	
				String key = issues.getJSONObject(i%1000).get("key").toString();
				
				List<String> affectedVersionList = new ArrayList<>();
				
				List<String> fixVersionList = new ArrayList<>();
				
				JSONObject field =ticket.getJSONObject("fields");
								
				String strCreatedDate = field.get("created").toString();
			
				String formattedCreatedDate = strCreatedDate.substring(0,10);
				
				LocalDate createdDate = LocalDate.parse(formattedCreatedDate, formatter);
				
				LocalDate releaseDate = release.get(release.size()/2).getReleaseDate();
				
				//compare the ticket's date to the release's date
				
				if(createdDate.compareTo(releaseDate)<0) {
					
					/*if the ticket's date is previous than the release's date (the first half of the project), 
					 * 
					 * the fuction get all information about the ticket and it is added to the list*/
						
					JSONArray affectedVersion = field.getJSONArray("versions");
	
					for(int k=0; k<affectedVersion.length(); k++) {
							
						String versionName = affectedVersion.getJSONObject(k).get("name").toString();
													
						affectedVersionList.add(versionName);
		
					}	
	
					JSONArray fixVersion = field.getJSONArray("fixVersions");
						
					for(int z=0; z<fixVersion.length(); z++) {

						String fixVersionName = fixVersion.getJSONObject(z).get("name").toString();
		
						fixVersionList.add(fixVersionName);
						
					}
										
					Ticket t = new Ticket(key, affectedVersionList, fixVersionList, createdDate); 
					
					//Adds the new ticket to the list

					tickets.add(t);	
						
					}
		               
			}    
		    
		} while (i < total);
		
		// order releases by date
		
		Collections.sort(tickets, (Ticket o1, Ticket o2) -> o1.getCreatedDate().compareTo(o2.getCreatedDate()));
		
		LOGGER.info(tickets.size()+" tickets found!");
		     
		return tickets;
		  
	}

	/* fuction that retrieve the information about commits that have the date previous than 
	 * 
	 * the release's date (the first half of the project)*/
	
	public static List<Commit> getCommits(String projName, String token, List<Release> release) throws IOException, ParseException {
		
		LOGGER.info("Searching for commits...");
		
		List<Commit> commits = new ArrayList<>();
	   
		Integer page = 0;
		JSONArray comm;
		   
		while(true) {
			   
			//Takes all commits
			String url = "https://api.github.com/repos/apache/" + projName + "/commits?per_page=100&page=" + page.toString();
			  
			try{
				comm = jsonArrayFromUrl(url, token);
		    	   	         
			}catch(Exception e) {
				LOGGER.severe(e.toString());
				return commits;
			}
			
			int total = comm.length();
		      
			if(total == 0) {
		    	  break;
		    }
			
			for(int i = 0; i < total; i++) {
		        	
				JSONObject commit = comm.getJSONObject(i).getJSONObject("commit");
				   
				String author = commit.getJSONObject("author").get("name").toString();
				String message = commit.get("message").toString();
				String strdate = commit.getJSONObject("committer").get("date").toString();
				String sha = comm.getJSONObject(i).get("sha").toString();
				   
				String formattedDate = strdate.substring(0,10);
				LocalDate date = LocalDate.parse(formattedDate, formatter);					
					
				LocalDate releaseDate = release.get(release.size() / 2).getReleaseDate();
				   
				//Take the commit from the first half of the project										
				if(date.compareTo(releaseDate) < 0) {
					Commit c = new Commit(message, formattedDate, author, sha);
					commits.add(c);
				}
			}
			 
			page++;
		
		}
		
		LOGGER.info(commits.size() + " commits found!");
		
		//order commit by descending date
		Collections.sort(commits, (Commit o1, Commit o2) -> o2.getDate().compareTo(o1.getDate()));
		
		return commits;
		
	} 

	
	/* if a ticket is found in the commit, the function add this commit in the list commitsTicket and search the fix commit*/
	
	public static void associatingCommitToTickets(List<Ticket> tickets, List<Commit> commits) {
		
		for (Ticket ticket : tickets) {
			String ticketId = ticket.getId();

			for (Commit commit : commits) {
				String message = commit.getMessage();
				
				// If the commit message contains the ticket ID in a specific format, associate the commit to the ticket
				if (message.matches(".*\\b" + ticketId + "[-:\\]\\s].*")) {
					
					// Add commit in the list of ticket's commits
					ticket.addCommit(commit);
					ticket.setCommitFix(commit);
				}
			}
		}
		
}

	
	//get info of the files for all commit
	
	public static List<FileCommitted> getFile(List<Commit> commits, String projName, String token) throws UnsupportedEncodingException {
		
		List<FileCommitted> commitedFiles = new ArrayList<>();
		LOGGER.info("Searching committed files...");

		for(Commit commit : commits) {
			String sha = commit.getSha();
			String url = "https://api.github.com/repos/apache/"+ projName +"/commits/"+ sha;
			try {
				JSONObject conn = jsonFromUrl(url, token);
				JSONArray files = conn.getJSONArray("files");
				for(int i=0; i<files.length(); i++) {
					JSONObject file = files.getJSONObject(i);
					String filename = file.getString("filename");
					if(filename.endsWith(".java")) {
						int changes = file.getInt("changes");
						int deletions = file.getInt("deletions");
						int additions = file.getInt("additions");	
						LocalDate date = commit.getDate();
						String contentUrl = file.getString("contents_url");
						JSONObject conn2 = jsonFromUrl(contentUrl, token);
						String content = conn2.getString("content");
						byte[] contentByteArray = Base64.getMimeDecoder().decode(content);
						String contentString = new String(contentByteArray);
						FileCommitted f = new FileCommitted(filename, changes, deletions, additions, date, contentUrl, contentString);
						commit.addCommitFile(f);
						commitedFiles.add(f);
					}
				}
				LOGGER.info(commit.getCommitFile().size() + " committed files (.java) found for commit " + sha);
			}catch(Exception e) {
				LOGGER.log(Level.SEVERE, "[ERROR]", e);
				break;
			}
		}
		LOGGER.info(commitedFiles.size() + " committed files found for all commits!");
		return commitedFiles;
	}

	
	
	//associating opened version to the ticket
	
	public static void associatingReleaseToTickets(List<Release> release, List<Ticket> tickets) {
		
		Collections.sort(tickets, Comparator.comparing(Ticket::getCreatedDate));
		
		for (int i = 0; i < release.size() - 1; i++) {
			Release currentRelease = release.get(i);
			Release nextRelease = release.get(i + 1);
			
			for(Ticket ticket : tickets) {
				LocalDate createdDate = ticket.getCreatedDate();
				if(createdDate.isBefore(currentRelease.getReleaseDate())) {
					ticket.setOpeningVersion(currentRelease.getVersion());
				} else if(createdDate.isAfter(currentRelease.getReleaseDate()) && createdDate.isBefore(nextRelease.getReleaseDate()) || createdDate.isEqual(currentRelease.getReleaseDate())) {
					ticket.setOpeningVersion(nextRelease.getVersion());
				}
			}
		}

	}

	
	//Returns the number of LOC in a file
	
	public static int getLoc(FileCommitted file) {
	    return file.getContent().split("\\r?\\n").length;
	}

	
	//Returns the number of comments in a file
	
	public static int countComment(FileCommitted file) {

		int count = 0;

		String content = file.getContent();

		StringBuilder currentLine = new StringBuilder();

		boolean inBlockComment = false;

		for(char c : content.toCharArray()) {
		    
		    if(c == '\n') {
		        
		        String line = currentLine.toString().trim();
		        
		        if(line.startsWith("//") || (line.startsWith("/*") && line.endsWith("*/"))) {
		            
		            count++;
		            
		        } else if(line.startsWith("/*") && !line.endsWith("*/")) {
		            
		            inBlockComment = true;
		            count++;
		            
		        } else if(line.endsWith("*/")) {
		            
		            inBlockComment = false;
		            count++;
		            
		        }
		        
		        currentLine = new StringBuilder();
		        
		    } else {
		        
		        currentLine.append(c);
		        
		        if(inBlockComment) {
		            count++;
		        }
		        
		    }
		    
		}

		return count;

	 
	}	
	
	// count the size of a file
	
	public static void calculateSizes(List<FileCommitted> fileList) {
	    fileList.forEach(file -> {
	        int loc = getLoc(file);
	        int comment = countComment(file);
	        int size = loc - comment;
	        file.setSize(size);
	    }); }
	
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
            throw new RuntimeException("HTTP error code: " + responseCode);
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
	        throw new RuntimeException("Failed to parse JSON array from URL: " + url, e);
	    } finally {
	        urlConnection.disconnect();
	    }
	}
}