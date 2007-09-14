package org.intellij.vcs.mks.model;

import org.intellij.vcs.mks.MksRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used to keep track of the current state of a sandbox. <br/> Is associated
 * with a sandbox member and keeps all the VCS related info. <br/> It is set by
 * the {@link org.intellij.vcs.mks.sicommands.AbstractViewSandboxCommand}
 * subclasses
 *
 * @author Thibaut Fagart
 */
public final class MksMemberState {
	public static enum Status {
		CHECKED_OUT("checked out"),
		MODIFIED_WITHOUT_CHECKOUT("modified without checkout"),
		MISSISNG("missing"),
		NOT_CHANGED("not changed"),
		SYNC("sync"),
		DROPPED("dropped"),
		ADDED ("added");
		private final String description;

		private Status(String description) {
			this.description = description;
		}

		public String toString() {
			return description;
		}
	}

	/**
	 * member revision of the member in the Project. <br/> If it is different than
	 * {@link #workingRevision} this means the member should be resynced
	 */
	public final MksRevisionNumber memberRevision;
	/**
	 * revision currently in the sandbox
	 */
	public final MksRevisionNumber workingRevision;
	@NotNull
	public final Status status;
	@Nullable
	public final String workingChangePackageId;

	public MksMemberState(final MksRevisionNumber workingRevision, final MksRevisionNumber memberRevision, final
	String workingChangePackageId, final Status status) {
		this.workingRevision = workingRevision;
		this.memberRevision = memberRevision;
		this.workingChangePackageId = workingChangePackageId;
		this.status = status;
	}

	@Override
	public String toString() {
		return "memberRev " + ((memberRevision == null) ? "null": memberRevision.asString()) + ", workingRev " + ((workingRevision ==null) ? "null" : workingRevision.asString())
			+ ", status " + status/* +", checkedout " + checkedout+ ", modified without checkout " + modifiedWithoutCheckout*/
			+ ", wokingCpid " + workingChangePackageId;
	}
}