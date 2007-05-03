package org.intellij.vcs.mks;

/**
 * @author Thibaut Fagart
 */
public class MksChangePackageEntry {
	private String state;
	private String member;
	private String revision;
	private String project;

	public MksChangePackageEntry(String state, String member, String revision, String project) {
		this.member = member;
		this.project = project;
		this.revision = revision;
		this.state = state;
	}


	public String getMember() {
		return member;
	}

	public void setMember(String member) {
		this.member = member;
	}

	public String getProject() {
		return project;
	}

	public void setProject(String project) {
		this.project = project;
	}

	public String getRevision() {
		return revision;
	}

	public void setRevision(String revision) {
		this.revision = revision;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String toString() {
		return super.toString() + "[" + member + "]";
	}

	public boolean isLocked() {
		return "Lock".equals(state);
	}
}
