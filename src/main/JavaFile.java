package main;


import java.util.List;
import java.util.stream.Collectors;



//Questo codice definisce una classe chiamata JavaFile in Java, che ha variabili di istanza (o campi) che rappresentano diverse informazioni su un file Java, come il nome del file, la sua dimensione in linee di codice (linesOfCode), il numero di volte che è stato modificato, il numero di volte in cui è stato introdotto un bug e il numero di volte in cui è stato corretto.

//La classe ha anche vari metodi getter e setter per accedere e impostare i valori di queste variabili di istanza. Inoltre, ci sono liste per tenere traccia di informazioni come i percorsi precedenti del file, le dimensioni dei set di modifiche e le dimensioni di churn (cioè il numero di linee di codice modificate in un dato periodo di tempo).

public class JavaFile {
	private String nome;
    private List<String> oldPaths;
    private String buggy;
    private Integer linesOfCode; 
    private Integer linesOfCodeTouched;
    private Integer nr;
    private Integer nFix;
    private List<String> nAuth;
    private Integer linesOfCodeAdded;
    private List<Integer> linesOfCodeAddedList;
    private Integer maxlinesOfCodeAdded;
    private Integer avglinesOfCodeAdded;
    private Integer churn;
    private Integer maxChurn;
    private Integer avgChurn;
    private Integer chgSetSize;
    private Integer maxChgSetSize;
    private Integer avgChgSetSize;
    private List<Integer> chgSetSizeList;
    private List<Integer> churnList;
    
    
    public void updateMetrics(JavaFile javaFile) {
    	this.setlinesOfCodeadded(this.getlinesOfCodeadded() + javaFile.getlinesOfCodeadded());
    	this.getlinesOfCodeAddedList().addAll(javaFile.getlinesOfCodeAddedList());
    	this.setChurn(this.getChurn() + javaFile.getChurn());
    	this.getChurnList().addAll(javaFile.getChurnList());
    	this.setNr(this.getNr() + javaFile.getNr());
    	List<String> nAuthList = this.getNAuth();
    	nAuthList.addAll(javaFile.getNAuth());
    	nAuthList = nAuthList.stream().distinct().collect(Collectors.toList());
    	this.setNAuth(nAuthList);
    	this.setChgSetSize(this.getChgSetSize() + javaFile.getChgSetSize());
    	this.getChgSetSizeList().addAll(javaFile.getChgSetSizeList());
    	}

    
    public JavaFile(String nome) {
        this.nome = nome;
        
    }

    // get
    public String getName() {
        return nome;
    }

    public String getBuggyness() {
        return buggy;
    }

    public List<String> getoldPaths() {
        return oldPaths;
    }

    public Integer getlinesOfCode() {
        return linesOfCode;
    }

    public Integer getlinesOfCodetouched() {
        return linesOfCodeTouched;
    }

    public int getlinesOfCodeadded() {
        if (linesOfCodeAdded == null) {
            return 0; // o un altro valore di default
        } else {
            return linesOfCodeAdded.intValue();
        }
    }

    public Integer getChurn() {
        return churn;
    }

    public Integer getChgSetSize() {
        return chgSetSize;
    }

    public Integer getMaxlinesOfCodeAdded() {
        return maxlinesOfCodeAdded;
    }

    public Integer getAvglinesOfCodeAdded() {
        return avglinesOfCodeAdded;
    }

    public Integer getNr() {
        return nr;
    }

    public Integer getMaxChgSetSize() {
        return maxChgSetSize;
    }

    public Integer getAvgChgSetSize() {
        return avgChgSetSize;
    }

    public List<Integer> getChgSetSizeList() {
        return chgSetSizeList;
    }
    public List<Integer> getChurnList() {
        return churnList;
    }

    public List<Integer> getlinesOfCodeAddedList() {
        return linesOfCodeAddedList;
    }
    public List<String> getNAuth() {
        return nAuth;
    }

    // set
    public void setName(String nome) {
        this.nome = nome;
    }

    public void setBuggyness(String bug) {
        this.buggy = bug;
    }

    public void setlinesOfCode(Integer linesOfCode) {
        this.linesOfCode = linesOfCode;
    }

    public void setlinesOfCodeadded(Integer linesOfCodeAdded) {
        this.linesOfCodeAdded = linesOfCodeAdded;
    }

    public void setlinesOfCodetouched(Integer linesOfCodeTouched) {
        this.linesOfCodeTouched = linesOfCodeTouched;
    }

    public void setChurn(Integer churn) {
        this.churn = churn;
    }

    public void setChgSetSize(Integer chgSetSize) {
        this.chgSetSize = chgSetSize;
    }

    public void setNr(Integer nr) {
        this.nr = nr;
    }

    public void setMaxlinesOfCodeAdded(Integer maxlinesOfCodeAdded) {
        this.maxlinesOfCodeAdded = maxlinesOfCodeAdded;
    }

    public void setAvglinesOfCodeAdded(Integer avglinesOfCodeAdded) {
        this.avglinesOfCodeAdded = avglinesOfCodeAdded;
    }

    public void setOldPaths(List<String> oldPaths) {
        this.oldPaths = oldPaths;
    }

    public void setMaxChgSetSize(Integer maxChgSetSize) {
        this.maxChgSetSize = maxChgSetSize;
    }

    public void setAvgChgSetSize(Integer avgChgSetSize) {
        this.avgChgSetSize = avgChgSetSize;
    }

    public Integer getnFix() {
        return nFix;
    }

    public void setnFix(Integer nFix) {
        this.nFix = nFix;
    }

    public void setNAuth(List<String> nAuth) {
        this.nAuth = nAuth;
    }

    public void setChgSetSizeList(List<Integer> chgSetSizeList) {
        this.chgSetSizeList = chgSetSizeList;
    }
    public void setlinesOfCodeAddedList(List<Integer> linesOfCodeAddedList) {
        this.linesOfCodeAddedList = linesOfCodeAddedList;
    }
    public void setChurnList(List<Integer> churnList) {
        this.churnList = churnList;
    }

    public Integer getMAXChurn() {
        return maxChurn;
    }

    public void setMAXChurn(Integer maxChurn) {
        this.maxChurn = maxChurn;
    }

    public Integer getAVGChurn() {
        return avgChurn;
    }

    public void setAVGChurn(Integer aVGChurn) {
        this.avgChurn = aVGChurn;
    }
    
    }
    







