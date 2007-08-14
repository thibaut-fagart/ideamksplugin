package org.intellij.vcs.mks.sicommands;

import java.util.List;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.MksRevisionNumber;
import org.intellij.vcs.mks.MksMemberState;
import com.intellij.openapi.vcs.VcsException;

/**
 * Obtains member revision, working revision, checkedout state (won't see
 * modified without checkout) for all members of a sandbox.
 *
 * @author Thibaut Fagart
 */
public class ViewSandboxWithoutChangesCommand extends AbstractViewSandboxCommand {


	public ViewSandboxWithoutChangesCommand(final List<VcsException> errors, final EncodingProvider encodingProvider, final String mksUsername, final String sandboxPath) {
		super(errors, encodingProvider, mksUsername, "--filter=", "--sandbox="+sandboxPath);
	}

	@Override
	protected MksMemberState createState(final String workingRev, final String memberRev, final String workingCpid, final String locker) throws VcsException {
		return new MksMemberState(new MksRevisionNumber(workingRev), new MksRevisionNumber(memberRev), workingCpid, false, false);
	}

}
