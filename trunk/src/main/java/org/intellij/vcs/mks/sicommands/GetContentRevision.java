package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.vcs.VcsException;
import org.intellij.vcs.mks.MksContentRevision;
import org.intellij.vcs.mks.MksVcs;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.List;

/**
 * @author Thibaut Fagart
 */
public class GetContentRevision extends SiCLICommand {
	private final int prevErrorCount;
	@NonNls
	public static final String COMMAND = "viewrevision";

	public GetContentRevision(List<VcsException> errors, MksVcs mksvcs, MksContentRevision mksContentRevision) {
		super(errors, mksvcs, COMMAND, "-r", mksContentRevision.getRevisionNumber().asString(), mksContentRevision.getFile().getPath());
		prevErrorCount = errors.size();

	}

	@Override
	public void execute() {
		try {
			executeCommand();
		} catch (IOException e) {
			errors.add(new VcsException(e));
		}
	}

	public String getContent() throws VcsException {
		if (errors.size() > prevErrorCount) {
			for (VcsException vcsException : errors.subList(prevErrorCount, errors.size())) {
				LOGGER.error(vcsException);
			}
			throw new VcsException(errors.get(prevErrorCount));
		} else {
			return commandOutput;
		}
	}
}
