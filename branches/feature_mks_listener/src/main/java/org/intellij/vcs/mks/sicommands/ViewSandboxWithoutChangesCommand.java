package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.model.MksMemberState;

import java.util.List;

/**
 * Obtains member revision, working revision, checkedout state (won't see
 * modified without checkout) for all members of a sandbox.
 *
 * @author Thibaut Fagart
 */
public class ViewSandboxWithoutChangesCommand extends AbstractViewSandboxCommand {


	public ViewSandboxWithoutChangesCommand(final List<VcsException> errors, final EncodingProvider encodingProvider, final String mksUsername, final String sandboxPath) {
		super(errors, encodingProvider, mksUsername, sandboxPath);
	}

	@Override
	protected MksMemberState createState(final String workingRev, final String memberRev, final String workingCpid,
										 final String locker, final String lockedSandbox, final String type, final String deferred) throws VcsException {
		VcsRevisionNumber workingRevision = createRevision(workingRev);
		VcsRevisionNumber memberRevision = createRevision(memberRev);
		if (isDropped(type)) {
			return new MksMemberState(workingRevision, memberRevision, workingCpid, MksMemberState.Status.DROPPED);
		} else if (memberRevision != null && workingRevision == null) {
			return new MksMemberState(workingRevision, memberRevision, workingCpid, MksMemberState.Status.MISSISNG);
		} else {
			return new MksMemberState(workingRevision, memberRevision, workingCpid, MksMemberState.Status.NOT_CHANGED);
		}
	}

}