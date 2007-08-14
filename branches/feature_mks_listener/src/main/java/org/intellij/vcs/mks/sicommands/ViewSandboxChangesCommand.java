package org.intellij.vcs.mks.sicommands;

import java.util.List;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.MksRevisionNumber;
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
		super(errors, encodingProvider, username, "--filter=changed,locked", sandboxPath);
	}

	@Override
	protected MemberState createState(final String workingRev, final String memberRev, final String workingCpid, final boolean checkedout) throws VcsException {
		return new MemberState(new MksRevisionNumber(workingRev), new MksRevisionNumber(memberRev), workingCpid, checkedout, true);
	}
}