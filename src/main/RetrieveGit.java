package main;

import org.eclipse.jgit.api.Git;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;



import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;




public class RetrieveGit {

    private RetrieveGit() {
    }


    private static Repository repository;
    private static final Logger logger = Logger.getLogger(RetrieveGit.class.getName());
  


   

    public static List<RevCommit> getAllCommits(List<Release> releases, Path repositoryPath) throws GitAPIException, IOException {
        List<RevCommit> commits = new ArrayList<>();
        try (Git git = Git.open(repositoryPath.toFile())) {
            Iterable<RevCommit> logEntries = git.log().all().call();
            for (RevCommit commit : logEntries) {
                commits.add(commit);
                updateReleaseWithCommit(commit, releases);
            }
        }
        return commits;
    }

    private static void updateReleaseWithCommit(RevCommit commit, List<Release> releases) {
        Release release = getReleaseForCommit(commit, releases);
        if (release != null) {
            release.addCommit(commit);
        }
    }

    


    private static Release getReleaseForCommit(RevCommit commit, List<Release> releaseList) {
        LocalDateTime commitDate = commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        int releaseIndex = RetrieveJira.compareDateVersion(commitDate, releaseList);
        return releaseList.stream()
                          .filter(release -> release.getIndex() == releaseIndex)
                          .findFirst()
                          .orElse(null);
    }



    public static void getJavaFiles(Path repoPath, List<Release> releasesList) throws IOException, IllegalStateException, GitAPIException {
        Git git = Git.init().setDirectory(repoPath.toFile()).call();

        for (Release release : releasesList) {
            List<String> javaFileNames = new ArrayList<>();
            for (RevCommit commit : release.getCommitList()) {
                ObjectId treeId = commit.getTree();
                try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                    treeWalk.reset(treeId);
                    treeWalk.setRecursive(true);

                    while (treeWalk.next()) {
                        addJavaFile(treeWalk, release, javaFileNames);
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error during the getJavaFiles operation.");
                    System.exit(1);
                }
            }
            setFileListIfNeeded(release, releasesList);
        }
    }

    private static void setFileListIfNeeded(Release release, List<Release> releasesList) {
        if (release.getFile().isEmpty() && !releasesList.isEmpty()) {
            Release previousRelease = releasesList.get(releasesList.size() - 1);
            release.setFileList(previousRelease.getFile());
        }
    }

   



    private static void addJavaFile(TreeWalk treeWalk, Release release, List<String> fileNameList) throws IOException {
        // Ottieni il nome del file
        String filename = treeWalk.getPathString();
        // Se il file ha estensione .java
        if (filename.endsWith(".java")) {
            // Se il file non è già stato aggiunto alla release
            if (!fileNameList.contains(filename)) {
                // Crea una nuova istanza di JavaFile con il nome del file
                JavaFile file = new JavaFile(filename);
                // Imposta gli attributi di default per il nuovo file
                setDefaultJavaFileAttributes(treeWalk, file);
                // Aggiungi il file alla release
                release.getFile().add(file);
                // Aggiungi il nome del file alla lista dei file
                fileNameList.add(filename);
            }
        }
    }

    private static void setDefaultJavaFileAttributes(TreeWalk treeWalk, JavaFile file) throws IOException {
        // Imposta gli attributi di default per un nuovo file Java
        file.setBugg("false");
        file.setNr(0);
        file.setNAuth(new ArrayList<>());
        file.setChgSetSize(0);
        file.setChgSetSizeList(new ArrayList<>());
        file.setLOCadded(0);
        file.setLocAddedList(new ArrayList<>());
        file.setChurn(0);
        file.setChurnList(new ArrayList<>());
        // Imposta la dimensione del file in linee di codice
        int loc = MainClass.size(treeWalk, repository);
        file.setLOC(loc);
    }



    public static void checkBuggyness(List<Release> releaseList, List<Ticket> ticketList) throws IOException {
        for (Ticket ticket : ticketList) {
            verify(ticket, releaseList);
        }
    }

    private static void verify(Ticket ticket, List<Release> releaseList) throws IOException {
        List<Integer> av = ticket.getAV();
        ticket.getCommitList().stream()
            .map(commit -> {
				try {
					return getDiffs(commit);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;
			})
            .filter(diffs -> diffs != null)
            .forEach(diffs -> diff(diffs, releaseList, av));
    }



    private static DiffFormatter initializeDiffFormatter() {
        DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE);
        diff.setRepository(repository);
        diff.setContext(0);
        diff.setDetectRenames(true);
        return diff;
    }
    private static List<DiffEntry> getDiffsBetweenCommits(RevCommit parent, RevCommit commit, DiffFormatter diff) throws IOException {
        return diff.scan(parent.getTree(), commit.getTree());
    }
    private static List<DiffEntry> getDiffsWithEmptyTree(RevCommit commit, DiffFormatter diff) throws IOException {
        RevWalk rw = new RevWalk(repository);
        return diff.scan(new EmptyTreeIterator(), new CanonicalTreeParser(null, rw.getObjectReader(), commit.getTree()));
    }
    public static List<DiffEntry> getDiffs(RevCommit commit) throws IOException {
        DiffFormatter diff = initializeDiffFormatter();
        List<DiffEntry> diffs;
        if (commit.getParentCount() != 0) {
            RevCommit parent = (RevCommit) commit.getParent(0).getId();
            diffs = getDiffsBetweenCommits(parent, commit, diff);
        } else {
            diffs = getDiffsWithEmptyTree(commit, diff);
        }
        return diffs;
    }



    public static void diff(List<DiffEntry> diffs, List<Release> releasesList, List<Integer> av) {
        for (DiffEntry diff : diffs) {
            if (isBuggyDiffEntry(diff)) {
                String file = getFilePathFromDiffEntry(diff);
                setBuggyness(file, releasesList, av);
            }
        }
    }

    private static boolean isBuggyDiffEntry(DiffEntry diff) {
        String type = diff.getChangeType().toString();
        return diff.toString().contains(".java") && (type.equals("MODIFY") || type.equals("DELETE"));
    }

    private static String getFilePathFromDiffEntry(DiffEntry diff) {
        if (diff.getChangeType() == DiffEntry.ChangeType.DELETE || diff.getChangeType() == DiffEntry.ChangeType.RENAME) {
            return diff.getOldPath();
        } else {
            return diff.getNewPath();
        }
    }


    public static void setBuggyness(String file, List<Release> releasesList, List<Integer> av) {
        for (Release release : releasesList) {
            setBuggynessForRelease(file, release, av);
        }
    }

    private static void setBuggynessForRelease(String file, Release release, List<Integer> av) {
        if (av.contains(release.getIndex())) {
            setBuggynessForJavaFile(file, release.getFile());
        }
    }

    private static void setBuggynessForJavaFile(String file, List<JavaFile> javaFiles) {
        for (JavaFile javaFile : javaFiles) {
            if (javaFile.getName().equals(file)) {
                javaFile.setBugg("true");
            }
        }
    }


    /*Ticket mi dice che c'è un bug, questo bug ha toccato le release x,y,z. Ricordo che prendo tutti ticket risolti, ovvero so la release in qui li ho fixati.
    Il ticket include dei commit, i quali vanno a modificare dei file.java. Li modifico perchè quei file=classi hanno dei problemi, e li hanno dalla release x.
    Allora il file.java nelle release x,y,z avevano problemi, ovvero erano buggy.*/

    //utility di setup
    public static void setBuilder(String repo) throws IOException {
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
        repository = repositoryBuilder.setGitDir(new File(repo)).readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .setMustExist(true).build();
    }

}