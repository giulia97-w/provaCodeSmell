package main;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import Part1.RetrieveGit;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class Metrics {

    private Metrics() {
    }

    private static final String FILE_EXTENSION = ".java";
    private static final String RENAME = "RENAME";
    private static final String DELETE = "DELETE";
    private static final String MODIFY = "MODIFY";
    private static final String ADD = "ADD";
    static Logger logger = Logger.getLogger(Metrics.class.getName());

    public static void calculateMetricsForReleases(List<Release> releasesList, String repositoryPath) throws IOException {

        try (Repository repository = new FileRepositoryBuilder().setGitDir(new File(repositoryPath))
                .readEnvironment()
                .findGitDir()
                .setMustExist(true)
                .build()) {
            
            for (Release release : releasesList) {
                List<JavaFile> javaFilesListForRelease = new ArrayList<>();
                
                for (RevCommit commit : release.getCommitList()) {
                    analyzeCommitDiffMetrics(commit, javaFilesListForRelease, repository);
                }
                
                updateMetricsOfFilesByRelease(javaFilesListForRelease, release);
            }
            
        } catch (IOException e) {
            logger.severe("Error accessing the Git repository: " + e.getMessage());
            throw new IOException("Error accessing the Git repository: " + e.getMessage(), e);
        }
    }

    
    private static void analyzeCommitDiffMetrics(RevCommit commit, List<JavaFile> javaFilesListForRelease, Repository repository) {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {               
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(true);
            String authorName = commit.getAuthorIdent().getName();
            List<DiffEntry> diffs = RetrieveGit.getDiffs(commit);                           
            if (diffs != null) {
                analyzeDiffEntryMetrics(diffs, javaFilesListForRelease, authorName, diffFormatter);
            }
        } catch (Exception e) {
        	logger.severe("Error analyzing diff entry metrics: " + e.getMessage());
        }
    }




    public static void analyzeDiffEntryMetrics(List<DiffEntry> diffs, List<JavaFile> fileList, String authName, DiffFormatter diff) {
        int numTouchedClass = (int) diffs.stream()
                .filter(diffEntry -> diffEntry.toString().contains(FILE_EXTENSION))
                .count();

        for (DiffEntry diffEntry : diffs) {
            String type = diffEntry.getChangeType().toString();
            if (diffEntry.toString().contains(FILE_EXTENSION)
                    && (type.equals(MODIFY) || type.equals(DELETE) || type.equals(ADD) || type.equals(RENAME))) {
                String fileName = type.equals(DELETE) || type.equals(RENAME) ? diffEntry.getOldPath() : diffEntry.getNewPath();
                calculateMetrics(fileList, fileName, authName, numTouchedClass, diffEntry, diff);
            }
        }
    }


    public static void calculateMetrics(List<JavaFile> fileList, String fileName, String authName, int numTouchedClass, DiffEntry diffEntry, DiffFormatter diff) {
        int locAdded = 0;
        int locDeleted = 0;
        try {
            for (Edit edit : diff.toFileHeader(diffEntry).toEditList()) {   //metodo per calcolare locAdded & locDeleted
                locAdded += edit.getEndB() - edit.getBeginB();
                locDeleted += edit.getEndA() - edit.getBeginA();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error in calculateMetrics", e); // specificare l'eccezione e il metodo in cui si verifica l'errore
            return; // gestione dell'errore
        }

        int churn = locAdded - locDeleted;      

        boolean isFind = false;

        // Utilizzo di una mappa per velocizzare la ricerca del file
        Map<String, JavaFile> fileMap = fileList.stream().collect(Collectors.toMap(JavaFile::getName, Function.identity()));

        if (fileMap.containsKey(fileName)) { 
            JavaFile file = fileMap.get(fileName);
            isFind = true; 

            file.setNr(file.getNr() + 1);
            if (!file.getNAuth().contains(authName)) {
                file.getNAuth().add(authName);
            }

            file.setLOCadded(file.getLOCadded() + locAdded);
            file.getLocAddedList().add(locAdded);
            file.setChgSetSize(file.getChgSetSize() + numTouchedClass);
            file.setChurn(file.getChurn() + churn);
            file.getChurnList().add(churn);
        }

        if (!isFind) { 
            JavaFile javaFile = new JavaFile(fileName);
            applyMetricsNewFile(javaFile, numTouchedClass, locAdded, churn, fileList, authName);
        }
    }



    public static void applyMetricsNewFile(JavaFile javaFile, int numTouchedClass, Integer locAdded, Integer churn, List<JavaFile> fileList, String authName) {
        javaFile.setNr(1); //Perché appena creato

        List<String> listAuth = new ArrayList<>();
        listAuth.add(authName); // poiché appena creato
        javaFile.setNAuth(listAuth);

        javaFile.setChgSetSize(numTouchedClass);
        List<Integer> chgSetSizeList = new ArrayList<>();
        chgSetSizeList.add(numTouchedClass);
        javaFile.setChgSetSizeList(chgSetSizeList);

        if (locAdded != null) {
            javaFile.setLOCadded(locAdded);
            List<Integer> locAddedList = new ArrayList<>();
            locAddedList.add(locAdded);
            javaFile.setLocAddedList(locAddedList);
        }

        if (churn != null) {
            javaFile.setChurn(churn);
            List<Integer> churnList = new ArrayList<>();
            churnList.add(churn);
            javaFile.setChurnList(churnList);
        }

        fileList.add(javaFile);
    }




    public static void updateMetricsOfFilesByRelease(List<JavaFile> fileList, Release release) {
        for (JavaFile javaFile : fileList) {
            JavaFile fileInRelease = release.getFileByName(javaFile.getName());
            if (fileInRelease == null) {
                continue; // Il file non è presente nella release, passo al prossimo
            }

            // Update delle metriche
            fileInRelease.setLOCadded(fileInRelease.getLOCadded() + javaFile.getLOCadded());
            fileInRelease.getLocAddedList().addAll(javaFile.getLocAddedList());
            fileInRelease.setChurn(fileInRelease.getChurn() + javaFile.getChurn());
            fileInRelease.getChurnList().addAll(javaFile.getChurnList());
            fileInRelease.setNr(fileInRelease.getNr() + javaFile.getNr());
            List<String> nAuthList = fileInRelease.getNAuth();
            nAuthList.addAll(javaFile.getNAuth());
            nAuthList = nAuthList.stream().distinct().collect(Collectors.toList());
            fileInRelease.setNAuth(nAuthList);
            fileInRelease.setChgSetSize(fileInRelease.getChgSetSize() + javaFile.getChgSetSize());
            fileInRelease.getChgSetSizeList().addAll(javaFile.getChgSetSizeList());
        }
    }



    public static int countLinesOfFile(TreeWalk treeWalk, Repository repository) throws IOException {
        ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        loader.copyTo(output);
        String fileContent = output.toString();
            
        BufferedReader reader = new BufferedReader(new StringReader(fileContent));
        int lines = 0;
        
        while ((reader.readLine()) != null) {
            lines++;
        }
        return lines;
    }}

