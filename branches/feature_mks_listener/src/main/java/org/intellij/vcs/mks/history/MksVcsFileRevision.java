package org.intellij.vcs.mks.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.intellij.vcs.mks.MksContentRevision;
import org.intellij.vcs.mks.MksVcs;
import org.intellij.vcs.mks.model.MksMemberRevisionInfo;

import java.io.IOException;
import java.util.Date;

public class MksVcsFileRevision implements VcsFileRevision {
	private String author;
	private String commitMessage;
	private Date revisionDate;
	private final MksVcs mksvcs;
	private final FilePath myFile;
	private VcsRevisionNumber revision;
	private String myContent;

	public MksVcsFileRevision(MksVcs mksvcs, FilePath myFile, MksMemberRevisionInfo info) {
		this.mksvcs = mksvcs;
		this.myFile = myFile;

		author = info.getAuthor();
		commitMessage = info.getDescription();
		revisionDate = info.getDate();
		revision = info.getRevision();
	}

	public String getAuthor() {
		return author;
	}

	public String getBranchName() {
		return null;
	}

	public String getCommitMessage() {
		return commitMessage;
	}

	public Date getRevisionDate() {
		return revisionDate;
	}

	public void loadContent() throws VcsException {
		myContent = new MksContentRevision(mksvcs, myFile, revision).getContent();
	}

	public VcsRevisionNumber getRevisionNumber() {
		return revision;
	}

	public byte[] getContent() throws IOException {
		return myContent.getBytes();
	}

	public FilePath getMyFile() {
		return myFile;
	}

	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		MksVcsFileRevision that = (MksVcsFileRevision) o;

		return myFile.equals(that.myFile)
				&& !(revision == null ? that.revision != null : !revision.equals(that.revision));

	}

	public int hashCode() {
		int result;
		result = myFile.hashCode();
		result = 31 * result + (revision != null ? revision.hashCode() : 0);
		return result;
	}
}
