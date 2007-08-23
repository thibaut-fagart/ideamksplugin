package org.intellij.vcs.mks.sicommands;

import java.util.List;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.MksRevisionNumber;
import org.intellij.vcs.mks.model.MksMemberState;
import com.intellij.openapi.vcs.VcsException;

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
	                                     final String locker, final String lockedSandbox, final String type) throws VcsException {
		MksRevisionNumber workingRevision = createRevision(workingRev);
		MksRevisionNumber memberRevision = createRevision(memberRev);
		if (memberRevision != null && workingRevision == null) {
			return new MksMemberState(workingRevision, memberRevision, workingCpid,
				(DROPPED_TYPE.equals(type) ? MksMemberState.Status.DROPPED : MksMemberState.Status.MISSISNG));
		} else {
			return new MksMemberState(workingRevision, memberRevision, workingCpid,
				(DROPPED_TYPE.equals(type) ? MksMemberState.Status.DROPPED : MksMemberState.Status.NOT_CHANGED));
		}
	}

}
