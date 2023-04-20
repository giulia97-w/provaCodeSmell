package main;

import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;

public class DiffInfo {
	 private DiffEntry diffEntry;
	    private String type;
	    private String authName;
	    private List<JavaFile> fileList;
	    private DiffFormatter diff;
	    private String fileName;

    public DiffInfo(DiffEntry diffEntry, String type, String authName, List<JavaFile> fileList, DiffFormatter diff) {
        this.diffEntry = diffEntry;
        this.type = type;
        this.authName = authName;
        this.fileList = fileList;
        this.diff = diff;
    }

    public DiffEntry getDiffEntry() {
        return diffEntry;
    }

    public String getType() {
        return type;
    }

    public String getAuthName() {
        return authName;
    }
    
    public List<JavaFile> getFileList() {
        return fileList;
    }

    public DiffFormatter getDiff() {
        return diff;
    }

	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
	    this.fileName = fileName;
	}

}

