package org.intellij.vcs.mks.sicommands;

import java.util.List;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.MksRevisionNumber;
import org.intellij.vcs.mks.model.MksMemberState;
import com.intellij.openapi.vcs.VcsException;

/**
 * Command backed by si viewsandbox.
 * Allows fetching sandbox deltas, complete state of the sandbox is given by
 * {@link org.intellij.vcs.mks.sicommands.ViewSandboxWithoutChangesCommand
 * @author Thibaut Fagart
 */
public class ViewSandboxChangesCommand extends AbstractViewSandboxCommand {

	/**
	 * username is available in si viewservers
	 *
	 * @param errors collects all errors encountered
	 * @param encodingProvider provides encoding configuration for the command
	 * @param sandboxPath filepath of the sandbox project file (usually project.pj)
	 * @param username username of the current user : allows detecting which locks are checkouts of the IDEA user
	 */
	public ViewSandboxChangesCommand(final List<VcsException> errors, final EncodingProvider encodingProvider,
									 final String username, final String sandboxPath) {
		super(errors, encodingProvider, username, "--filter=changed,locked:"+username, "--sandbox="+sandboxPath);
	}

	@Override
	protected MksMemberState createState(final String workingRev, final String memberRev, final String workingCpid,
									  final String locker) throws VcsException {
		return new MksMemberState(new MksRevisionNumber(workingRev), new MksRevisionNumber(memberRev), workingCpid,
				mksUsername.equals(locker), locker == null);
	}
}