package org.intellij.vcs.mks.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.util.ui.ColumnInfo;
import org.intellij.vcs.mks.MksVcs;
import org.intellij.vcs.mks.realtime.MksSandboxInfo;
import org.intellij.vcs.mks.sicommands.ListMemberRevisionsCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MksVcsHistoryProvider implements VcsHistoryProvider {
	private final MksVcs vcs;

	public MksVcsHistoryProvider(MksVcs vcs) {
		this.vcs = vcs;
	}

	@Nullable
	public VcsHistorySession createSessionFor(final FilePath filePath) throws VcsException {
		final boolean isDirectory = filePath.isDirectory();
		final MksSandboxInfo sandbox = getSandbox(filePath);
		List<VcsFileRevision> revisions = getRevisions(filePath, sandbox);
		return new VcsHistorySession(revisions) {
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

	private VcsRevisionNumber getCurrentRevision(MksSandboxInfo sandbox, FilePath filePath) {
		throw new UnsupportedOperationException("todo");

	}

	private List<VcsFileRevision> getRevisions(FilePath filePath, MksSandboxInfo sandbox) {
		final ListMemberRevisionsCommand command = new ListMemberRevisionsCommand(new ArrayList<VcsException>(), vcs, sandbox, filePath.getPath());
		command.execute();
		throw new UnsupportedOperationException("todo");
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
