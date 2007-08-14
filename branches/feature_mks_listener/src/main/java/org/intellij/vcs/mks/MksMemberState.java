package org.intellij.vcs.mks;

import org.intellij.vcs.mks.MksRevisionNumber;

/**
 * @author Thibaut Fagart
*/
public final class MksMemberState {

	public final MksRevisionNumber memberRevision;
	public final MksRevisionNumber workingRevision;
	public final boolean modifiedWithoutCheckout;
	public final boolean checkedout;
	public final String workingChangePackageId;

	public MksMemberState(final MksRevisionNumber workingRevision, final MksRevisionNumber memberRevision, final
	String workingChangePackageId, final boolean checkedout, final boolean modifiedWithoutCheckout) {
		this.workingRevision = workingRevision;
		this.memberRevision = memberRevision;
		this.modifiedWithoutCheckout = modifiedWithoutCheckout;
		this.workingChangePackageId = workingChangePackageId;
		this.checkedout = checkedout;
	}

	@Override
	public String toString() {
		return "memberRev " + memberRevision.asString() + ", workingRev " + workingRevision.asString() + ", checkedout " + checkedout
			+ ", modified without checkout " + modifiedWithoutCheckout
			+ ", wokingCpid " + workingChangePackageId;
	}
}
