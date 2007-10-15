package org.intellij.vcs.mks.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsUtil;
import org.intellij.vcs.mks.MksRevisionNumber;
import org.intellij.vcs.mks.MksVcs;
import org.intellij.vcs.mks.model.MksMemberRevisionInfo;
import org.intellij.vcs.mks.realtime.MksSandboxInfo;
import org.intellij.vcs.mks.sicommands.ListMemberRevisionsCommand;
import org.intellij.vcs.mks.sicommands.SiCLICommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class MksVcsHistoryProvider implements VcsHistoryProvider {
	private final MksVcs vcs;
	private final Logger LOGGER = Logger.getInstance(getClass().getName());

	public MksVcsHistoryProvider(MksVcs vcs) {
		this.vcs = vcs;
	}

	@Nullable
	public VcsHistorySession createSessionFor(final FilePath filePath) throws VcsException {
		final boolean isDirectory = filePath.isDirectory();
		final MksSandboxInfo sandbox = getSandbox(filePath);
		List<VcsFileRevision> revisions = getRevisions(filePath, sandbox);
		return new VcsHistorySession(revisions) {
			@Override
			@Nullable
			public VcsRevisionNumber calcCurrentRevisionNumber() {
				return getCurrentRevision(sandbox, filePath);
			}

			@Override
			public boolean isContentAvailable(final VcsFileRevision revision) {
				return !isDirectory;
			}
		};
	}

	private VcsRevisionNumber getCurrentRevision(MksSandboxInfo sandbox, final FilePath filePath) {
		FilePath sandboxPath = VcsUtil.getFilePath(sandbox.sandboxPath);
		FilePath sandboxFolder = sandboxPath.getParentPath();
		if (!filePath.getPath().startsWith(sandboxFolder.getPath())) {
			LOGGER.info("error");
		} else {
			final String[] currentRevisionHolder = new String[1];
			(new SiCLICommand(new ArrayList<VcsException>(), vcs, "viewsandbox", "--sandbox=" + sandbox.sandboxPath,
					"--filter=file:" + filePath.getPath().substring(sandboxFolder.getPath().length() + 1),
					"--fields=workingrev"
			) {
				@Override
				public void execute() {
					try {
						super.executeCommand();
						BufferedReader reader = new BufferedReader(new StringReader(commandOutput));
						currentRevisionHolder[0] = reader.readLine();
					} catch (IOException e) {
						LOGGER.error("error obtaining current revision for " + filePath, e);
					}

				}
			}).execute();
			if (currentRevisionHolder[0] == null) {
				LOGGER.error("error obtaining current revision for " + filePath);
			}
			try {
				return new MksRevisionNumber(currentRevisionHolder[0]);
			} catch (VcsException e) {
				LOGGER.error("bad revision " + currentRevisionHolder[0] + " for " + filePath, e);
				return null;
			}
		}
		return null;

	}

	private List<VcsFileRevision> getRevisions(FilePath filePath, MksSandboxInfo sandbox) {
		final ListMemberRevisionsCommand command = new ListMemberRevisionsCommand(new ArrayList<VcsException>(), vcs, sandbox, filePath.getPath());
		command.execute();
		final List<MksMemberRevisionInfo> revisions = command.getRevisionsInfo();
		final ArrayList<VcsFileRevision> vcsRevisions = new ArrayList<VcsFileRevision>(revisions.size());
		for (MksMemberRevisionInfo revision : revisions) {
			vcsRevisions.add(new MksVcsRevision(vcs, filePath, revision));
		}
		return vcsRevisions;
//		throw new UnsupportedOperationException("todo");
//		return null;
	}

	private MksSandboxInfo getSandbox(FilePath filePath) {
		return vcs.getSandboxCache().getSandboxInfo(filePath.getVirtualFile());
	}

	public AnAction[] getAdditionalActions(FileHistoryPanel panel) {
		return new AnAction[0];
	}

	@Nullable
	@NonNls
	public String getHelpId() {
		return null;
	}

	public ColumnInfo[] getRevisionColumns() {
		return new ColumnInfo[0];
	}//return null if your revisions cannot be tree

	@Nullable
	public HistoryAsTreeProvider getTreeHistoryProvider() {
		// todo is this for branching ?
		return null;
	}

	public boolean isDateOmittable() {
		return false;
	}
}
