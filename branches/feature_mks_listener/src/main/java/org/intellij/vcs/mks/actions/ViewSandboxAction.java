package org.intellij.vcs.mks.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import mks.integrations.common.TriclopsSiSandbox;
import org.intellij.vcs.mks.MksVcs;
import org.intellij.vcs.mks.actions.triclops.ViewSandboxTriclopsCommand;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Map;


/**
 * Opens the current sandbox in the mks client
 */
public class ViewSandboxAction extends BasicAction {

	@Override
	protected boolean isRecursive() {
		return false;
	}

	public ViewSandboxAction() {
		super(new ViewSandboxTriclopsCommand());
	}

	@Override
	protected boolean isEnabled(@NotNull Project project, @NotNull MksVcs vcs, @NotNull VirtualFile... virtualFiles) {
		Map<TriclopsSiSandbox, ArrayList<VirtualFile>> map = vcs.dispatchBySandbox(virtualFiles);
		return map.size() == 1;
	}
}
