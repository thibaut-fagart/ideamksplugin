package org.intellij.vcs.mks.actions.triclops;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import mks.integrations.common.TriclopsException;
import mks.integrations.common.TriclopsSiMembers;
import org.intellij.vcs.mks.MKSHelper;
import org.intellij.vcs.mks.MksVcs;
import org.intellij.vcs.mks.MksVcsException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.List;

public abstract class AbstractSingleTargetTriclopsCommand extends AbstractTriclopsCommand {
	protected abstract void perform(@NotNull TriclopsSiMembers siMembers) throws TriclopsException;

	public void executeCommand(@NotNull MksVcs mksVcs, @NotNull List<VcsException> exceptions,
							   @NotNull VirtualFile[] affectedFiles) throws VcsException {
		if (affectedFiles.length > 1) {
			//noinspection ThrowableInstanceNeverThrown
			exceptions.add(new VcsException("expecting only one target for this action"));
			return;
		}
		TriclopsSiMembers[] members = createSiMembers(mksVcs, affectedFiles);
		for (TriclopsSiMembers siMembers : members) {
			try {
				// todo if active change list is an mks one, use it as the change package ?
				perform(siMembers);
			} catch (TriclopsException e) {
				if (MKSHelper.isLastCommandCancelled()) {
					@Nls final String pattern = "{0} Error: {1}";
					//noinspection ThrowableInstanceNeverThrown
					exceptions.add(new MksVcsException(
							MessageFormat.format(pattern, getActionName(mksVcs), MKSHelper.getMksErrorMessage()), e));
				}
			}
		}

	}
}
