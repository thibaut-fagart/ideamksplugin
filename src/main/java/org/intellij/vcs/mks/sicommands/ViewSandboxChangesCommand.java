package org.intellij.vcs.mks.sicommands;

import java.util.List;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.MksRevisionNumber;
import org.intellij.vcs.mks.MksMemberState;
import com.intellij.openapi.vcs.VcsException;

/**
 * @author Thibaut Fagart
 */
public class ViewSandboxChangesCommand extends AbstractViewSandboxCommand {

	/**
	 * username is availble in si viewservers
	 *
	 * @param errors
	 * @param encodingProvider
	 * @param sandboxPath
	 * @param username
	 */
	public ViewSandboxChangesCommand(final List<VcsException> errors, final EncodingProvider encodingProvider, final String username, final String sandboxPath) {
		super(errors, encodingProvider, username, "--filter=changed,locked:"+username, "--sandbox="+sandboxPath);
	}

	@Override
	protected MksMemberState createState(final String workingRev, final String memberRev, final String workingCpid, final String locker) throws VcsException {
		return new MksMemberState(new MksRevisionNumber(workingRev), new MksRevisionNumber(memberRev), workingCpid, mksUsername.equals(locker), locker == null);
	}
}