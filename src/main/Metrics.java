package main;


import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
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


    public static void getRepo(List<Release> releasesList, String repo) throws IOException {
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
        Repository repository = repositoryBuilder.setGitDir(new File(repo))
            .readEnvironment()
            .findGitDir()
            .setMustExist(true)
            .build();

        analyzeMetrics(releasesList, repository);
    }

    public static void analyzeMetrics(List<Release> releasesList, Repository repository) throws IOException {
        releasesList.forEach(release -> {
            List<JavaFile> filesJavaListPerRelease = new ArrayList<>();
            release.getCommitList().forEach(commit -> {
                try (DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    diff.setRepository(repository);
                    diff.setDiffComparator(RawTextComparator.DEFAULT);
                    diff.setDetectRenames(true);
                    String authName = commit.getAuthorIdent().getName();
                    List<DiffEntry> diffs = RetrieveGit.getDiffs(commit);
                    if (diffs != null) {
                        analyzeDiffEntryMetrics(diffs, filesJavaListPerRelease, authName, diff);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            metricsOfFilesByRelease(filesJavaListPerRelease, release);
        });
    }




    public static int countTouchedClasses(List<DiffEntry> diffs) {
        int numTouchedClass = 0;
        for (DiffEntry diffEntry : diffs) {
            if (diffEntry.toString().contains(FILE_EXTENSION)) {
                numTouchedClass++;
            }
        }
        return numTouchedClass;
    }
    public static void processDiffEntry(DiffEntry diffEntry, String type, String authName, List<JavaFile> fileList, int numTouchedClass, DiffFormatter diff) {
        if (diffEntry.toString().contains(FILE_EXTENSION) && type.equals(MODIFY) || type.equals(DELETE) || type.equals(ADD) || type.equals(RENAME)) {
            String fileName;
            if (type.equals(DELETE) || type.equals(RENAME)) fileName = diffEntry.getOldPath();
            else fileName = diffEntry.getNewPath();
            calculateMetrics(fileList, fileName, authName, numTouchedClass, diffEntry, diff);
        }
    }
    public static void analyzeDiffEntryMetrics(List<DiffEntry> diffs, List<JavaFile> fileList, String authName, DiffFormatter diff) {
        int numTouchedClass = countTouchedClasses(diffs);
        for (DiffEntry diffEntry : diffs) {
            String type = diffEntry.getChangeType().toString();
            processDiffEntry(diffEntry, type, authName, fileList, numTouchedClass, diff);
        }
    }


    public static void calculateMetrics(List<JavaFile> fileList, String fileName, String authName, int numTouchedClass, DiffEntry diffEntry, DiffFormatter diff) {
        int locAdded = 0;
        int locDeleted = 0;
        try {
            for (Edit edit : diff.toFileHeader(diffEntry).toEditList()) {
                locAdded += edit.getEndB() - edit.getBeginB();
                locDeleted += edit.getEndA() - edit.getBeginA();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error in CalculateMetrics");
        }
        int churn = calculateChurn(locAdded, locDeleted);
        fileList(fileList, fileName, authName, numTouchedClass, locAdded, churn);
    }

    private static int calculateChurn(int locAdded, int locDeleted) {
        return locAdded - locDeleted;
    }

    private static void fileList(List<JavaFile> fileList, String fileName, String authName, int numTouchedClass, int locAdded, int churn) {
        JavaFile file = findFile(fileList, fileName);
        if (file != null) {
            existingFile(file, authName, numTouchedClass, locAdded, churn);
        } else {
            addNewFile(fileList, fileName, authName, numTouchedClass, locAdded, churn);
        }
    }

    private static JavaFile findFile(List<JavaFile> fileList, String fileName) {
        for (JavaFile file : fileList) {
            if (file.getName().equals(fileName)) {
                return file;
            }
        }
        return null;
    }

    private static void existingFile(JavaFile file, String authName, int numTouchedClass, int locAdded, int churn) {
        file.setNr(file.getNr() + 1);
        if (!file.getNAuth().contains(authName)) {
            file.getNAuth().add(authName);
        }
        file.setLOCadded(file.getLOCadded() + locAdded);
        addToLocAddedList(file, locAdded);
        file.setChgSetSize(file.getChgSetSize() + numTouchedClass);
        file.setChurn(file.getChurn() + churn);
        addToChurnList(file, churn);
    }

    private static void addNewFile(List<JavaFile> fileList, String fileName, String authName, int numTouchedClass, int locAdded, int churn) {
        JavaFile javaFile = new JavaFile(fileName);
        applyMetricsNewFile(javaFile, numTouchedClass, locAdded, churn, fileList, authName);
    }
    private static void addToLocAddedList(JavaFile file, int locAdded) {
    	file.getLocAddedList().add(locAdded);
    }

    private static void addToChurnList(JavaFile file, int churn) {
    file.getChurnList().add(churn);
    }


    private static void setNr(JavaFile javaFile) {
        javaFile.setNr(1);
    }
    private static void setNAuth(JavaFile javaFile, String authName) {
        List<String> listAuth = new ArrayList<>();
        listAuth.add(authName);
        javaFile.setNAuth(listAuth);
    }
    private static void setChgSetSize(JavaFile javaFile, int numTouchedClass) {
        javaFile.setChgSetSize(numTouchedClass);
        List<Integer> chgSetSizeList = new ArrayList<>();
        chgSetSizeList.add(numTouchedClass);
        javaFile.setChgSetSizeList(chgSetSizeList);
    }
    private static void setLocAdded(JavaFile javaFile, int locAdded) {
        javaFile.setLOCadded(locAdded);
        List<Integer> locAddedList = new ArrayList<>();
        locAddedList.add(locAdded);
        javaFile.setLocAddedList(locAddedList);
    }
    private static void setChurn(JavaFile javaFile, int churn) {
        javaFile.setChurn(churn);
        List<Integer> churnList = new ArrayList<>();
        churnList.add(churn);
        javaFile.setChurnList(churnList);
    }
    private static void addToFileList(List<JavaFile> fileList, JavaFile javaFile) {
        fileList.add(javaFile);
    }
    public static void applyMetricsNewFile(JavaFile javaFile, int numTouchedClass, int locAdded, int churn, List<JavaFile> fileList, String authName) {
        setNr(javaFile);
        setNAuth(javaFile, authName);
        setChgSetSize(javaFile, numTouchedClass);
        setLocAdded(javaFile, locAdded);
        setChurn(javaFile, churn);
        addToFileList(fileList, javaFile);
    }



    public static void metricsOfFilesByRelease(List<JavaFile> fileList, Release release) {
        for (JavaFile javaFile : fileList) {
            for (JavaFile fileInRelease : release.getFile()) {
                if (javaFile.getName().equals(fileInRelease.getName())) {
                    // aggiorna le metriche del file associato alla release con quelle del file corrente
                    updateReleaseFileMetrics(javaFile, fileInRelease);
                }
            }
        }
    }

    private static void updateReleaseFileMetrics(JavaFile javaFile, JavaFile fileInRelease) {
        updateLOCadded(javaFile, fileInRelease);
        updateChurn(javaFile, fileInRelease);
        updateNAuth(javaFile, fileInRelease);
        updateChgSetSize(javaFile, fileInRelease);
    }

    private static void updateLOCadded(JavaFile javaFile, JavaFile fileInRelease) {
        int locAdded = javaFile.getLOCadded();
        fileInRelease.setLOCadded(fileInRelease.getLOCadded() + locAdded);
        List<Integer> locAddedList = fileInRelease.getLocAddedList();
        locAddedList.addAll(javaFile.getLocAddedList());
        fileInRelease.setLocAddedList(locAddedList);
    }

    private static void updateChurn(JavaFile javaFile, JavaFile fileInRelease) {
        int churn = javaFile.getChurn();
        fileInRelease.setChurn(fileInRelease.getChurn() + churn);
        List<Integer> churnList = fileInRelease.getChurnList();
        churnList.addAll(javaFile.getChurnList());
        fileInRelease.setChurnList(churnList);
    }

    private static void updateNAuth(JavaFile javaFile, JavaFile fileInRelease) {
        int nr = javaFile.getNr();
        fileInRelease.setNr(fileInRelease.getNr() + nr);
        List<String> nAuth = javaFile.getNAuth();
        List<String> nAuthList = fileInRelease.getNAuth();
        nAuthList.addAll(nAuth);
        nAuthList = nAuthList.stream().distinct().collect(Collectors.toList());
        fileInRelease.setNAuth(nAuthList);
    }

    private static void updateChgSetSize(JavaFile javaFile, JavaFile fileInRelease) {
        int chgSetSize = javaFile.getChgSetSize();
        fileInRelease.setChgSetSize(fileInRelease.getChgSetSize() + chgSetSize);
        List<Integer> chgSetSizeList = fileInRelease.getChgSetSizeList();
        chgSetSizeList.addAll(javaFile.getChgSetSizeList());
        fileInRelease.setChgSetSizeList(chgSetSizeList);
    }

    


    public static int linesOfCode(TreeWalk treewalk, Repository repository) throws IOException {
        try (ObjectReader reader = repository.newObjectReader()) {
            ObjectLoader loader = reader.open(treewalk.getObjectId(0));
            try (InputStream input = loader.openStream()) {
                BufferedReader readerNew = new BufferedReader(new InputStreamReader(input));
                return (int) readerNew.lines().count();
            }
        }
    }


}