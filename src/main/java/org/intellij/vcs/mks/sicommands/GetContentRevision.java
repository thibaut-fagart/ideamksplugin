package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.vcs.VcsException;
import org.intellij.vcs.mks.MksContentRevision;
import org.intellij.vcs.mks.MksVcs;

import java.io.IOException;
import java.util.List;

/**
 * @author Thibaut Fagart
 */
public class GetContentRevision extends SiCLICommand {
	private final int prevErrorCount;

	public GetContentRevision(List<VcsException> errors, MksVcs mksvcs, MksContentRevision mksContentRevision) {
		super(errors, mksvcs, "viewrevision", "-r", mksContentRevision.getRevisionNumber().asString(), mksContentRevision.getFile().getPath());
		prevErrorCount = errors.size();

	}

	public void execute() {
		try {
			executeCommand();
		} catch (IOException e) {
			errors.add(new VcsException(e));
		}
	}

	public String getContent() throws VcsException {
		if (errors.size() > prevErrorCount) {
			return commandOutput;
		} else {
			throw new VcsException(errors.get(prevErrorCount));
		}
	}
}
