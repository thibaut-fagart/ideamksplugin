package org.intellij.vcs.mks.sicommands;

import java.util.List;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.MksRevisionNumber;
import com.intellij.openapi.vcs.VcsException;

/**
 * Obtains member revision, working revision, checkedout state (won't see
 * modified without checkout) for all members of a sandbox.
 *
 * @author Thibaut Fagart
 */
public class ViewSandboxWithoutChangesCommand extends AbstractViewSandboxCommand {


	public ViewSandboxWithoutChangesCommand(final List<VcsException> errors, final EncodingProvider encodingProvider, final String mksUsername, final String sandboxPath) {
		super(errors, encodingProvider, mksUsername, "--filter=", sandboxPath);
	}

	@Override
	protected MemberState createState(final String workingRev, final String memberRev, final String workingCpid, final boolean checkedout) throws VcsException {
		return new MemberState(new MksRevisionNumber(workingRev), new MksRevisionNumber(memberRev), workingCpid, checkedout, false);
	}

}
