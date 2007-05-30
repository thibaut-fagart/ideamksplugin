package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.vcs.VcsException;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.MksContentRevision;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.List;

/**
 * @author Thibaut Fagart
 */
public class GetContentRevision extends SiCLICommand {
	@NonNls
	public static final String COMMAND = "viewrevision";

	public GetContentRevision(List<VcsException> errors, EncodingProvider encodingProvider, MksContentRevision mksContentRevision) {
		super(errors, encodingProvider, COMMAND, "-r", mksContentRevision.getRevisionNumber().asString(), mksContentRevision.getFile().getPath());

	}

	@Override
	public void execute() {
		try {
			executeCommand();
		} catch (IOException e) {
			//noinspection ThrowableInstanceNeverThrown
			errors.add(new VcsException(e));
		}
	}

	public String getContent() throws VcsException {
		if (foundError()) {
			for (VcsException vcsException : errors.subList(previousErrorCount, errors.size())) {
				LOGGER.error(vcsException);
			}
			throw new VcsException(errors.get(previousErrorCount));
		} else {
			return commandOutput;
		}
	}
}
