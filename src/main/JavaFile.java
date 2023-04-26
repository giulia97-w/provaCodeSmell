package main;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;



//Questo codice definisce una classe chiamata JavaFile in Java, che ha variabili di istanza (o campi) che rappresentano diverse informazioni su un file Java, come il nome del file, la sua dimensione in linee di codice (linesOfCode), il numero di volte che è stato modificato, il numero di volte in cui è stato introdotto un bug e il numero di volte in cui è stato corretto.

//La classe ha anche vari metodi getter e setter per accedere e impostare i valori di queste variabili di istanza. Inoltre, ci sono liste per tenere traccia di informazioni come i percorsi precedenti del file, le dimensioni dei set di modifiche e le dimensioni di churn (cioè il numero di linee di codice modificate in un dato periodo di tempo).

public class JavaFile {
	private String nome;
    private String buggy;
    private Integer linesOfCode; 
    private Integer linesOfCodeTouched;
    private Integer nr;
    private Integer nFix;
    private Integer linesOfCodeAdded;
    private List<Integer> linesOfCodeAddedList;
    private List<Integer> linesOfCodeTouchedList;

   
    private Integer maxlinesOfCodeAdded;
    private Integer avglinesOfCodeAdded;
    private Integer churn;
    private Integer locTouched;
    private Integer maxChurn;
    private Integer avgChurn;
    private Integer chgSetSize;


    private List<Integer> churnList;
    private List<Integer> locTouchedList;
    private List<String> oldPaths;
    private List<String> nAuth;
    
    
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
    	
    	}

    
    public JavaFile(String nome) {
        this.nome = nome;
        this.linesOfCodeAddedList = new ArrayList<Integer>();
        this.linesOfCodeTouchedList = new ArrayList<Integer>();
        this.churnList = new ArrayList<Integer>();
        this.locTouchedList = new ArrayList<Integer>();
        this.oldPaths = new ArrayList<String>();
        this.nAuth = new ArrayList<String>();
    }


    public boolean isBuggy(boolean buggyness) {
        return buggyness;
    }

   
    public String getName() {
        return nome;
    }
    public void setName(String nome) {
        this.nome = nome;
    }

    public String getBuggyness() {
        return buggy;
    }
    
    public void setBuggyness(String bug) {
        this.buggy = bug;
    }
    
    public List<String> getoldPaths() {
        return oldPaths;
    }
    
    public void setOldPaths(List<String> oldPaths) {
        this.oldPaths = oldPaths;
    }

    public Integer getlinesOfCode() {
    	if(linesOfCode == null) {
    		return 0;
    	}else {
    		return linesOfCode;	
    	}
        
    }
    public Integer getChgSetSize() {
        return chgSetSize;
    }
    public void setChgSetSize(Integer chgSetSize) {
        this.chgSetSize = chgSetSize;
    }
    public void setlinesOfCode(Integer linesOfCode) {
        this.linesOfCode = linesOfCode;
    }

    public Integer getlinesOfCodetouched() {
        return linesOfCodeTouched;
    }
    
    public void setlinesOfCodetouched(Integer linesOfCodeTouched) {
        this.linesOfCodeTouched = linesOfCodeTouched;
    }

    public int getlinesOfCodeadded() {
        if (linesOfCodeAdded == null) {
            return 0; 
        } else {
            return linesOfCodeAdded.intValue();
        }
    }
    public void setlinesOfCodeadded(Integer linesOfCodeAdded) {
        this.linesOfCodeAdded = linesOfCodeAdded;
    }
    

    public Integer getChurn() {
        if (churn == null) {
            // handle null value
            return 0; // return default value
            // or throw an exception to indicate that the value is not available
            // throw new NullPointerException("Churn value is null");
        } else {
            return churn;
        }
    }

    public Integer getLocTouched() {
    	
    	if(locTouched == null) {
    		return 0;
    	}else {
    		return locTouched;	
    	}
        
    }
    
    public void setChurn(Integer churn) {
        this.churn = churn;
    }
    public void setLocTouched(Integer locTouched) {
        this.locTouched = locTouched;
    }

    public Integer getMaxlinesOfCodeAdded() {
        return maxlinesOfCodeAdded;
    }
    public void setMaxlinesOfCodeAdded(Integer maxlinesOfCodeAdded) {
        this.maxlinesOfCodeAdded = maxlinesOfCodeAdded;
    }

    public Integer getAvglinesOfCodeAdded() {
        return avglinesOfCodeAdded;
    }
    
    public void setAvglinesOfCodeAdded(Integer avglinesOfCodeAdded) {
        this.avglinesOfCodeAdded = avglinesOfCodeAdded;
    }
    

    public Integer getNr() {
        if (nr == null) {
            return 0;
        } else {
            return nr;
        }
    }

    
    public void setNr(Integer nr) {
        this.nr = nr;
    }


    public List<Integer> getChurnList() {
        return churnList;
    }
    public List<Integer> getLocTouchedList() {
        return locTouchedList;
    }
    public List<Integer> getLinesOfCodeTouchedList() {
        return linesOfCodeTouchedList;
    }
    
    public void setChurnList(List<Integer> churnList) {
        this.churnList = churnList;
    }
    
    public void setlocTouchedList(List<Integer> locTouchedList) {
        this.locTouchedList = locTouchedList;
    }

    public List<Integer> getlinesOfCodeAddedList() {
        return linesOfCodeAddedList;
    }
    
    public List<String> getNAuth() {
        return nAuth;
    }
    public void setNAuth(List<String> nAuth) {
        this.nAuth = nAuth;
    }

    public Integer getnFix() {
        return nFix;
    }

    public void setnFix(Integer nFix) {
        this.nFix = nFix;
    }


    public void setlinesOfCodeAddedList(List<Integer> linesOfCodeAddedList) {
        this.linesOfCodeAddedList = linesOfCodeAddedList;
    }
    
    public void setlinesOfCodeTouchedList(List<Integer> linesOfCodeTouchedList) {
        this.linesOfCodeTouchedList = linesOfCodeTouchedList;
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
    







