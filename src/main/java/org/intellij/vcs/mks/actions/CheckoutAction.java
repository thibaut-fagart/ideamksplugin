package org.intellij.vcs.mks.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import org.intellij.vcs.mks.MksVcs;
import org.intellij.vcs.mks.actions.triclops.CheckoutTriclopsCommand;
import org.jetbrains.annotations.NotNull;


public class CheckoutAction extends MultipleTargetAction {

	public CheckoutAction() {
		super(new CheckoutTriclopsCommand());
	}

	protected boolean isEnabled(@NotNull Project project, @NotNull MksVcs vcs, @NotNull VirtualFile file) {
		FilePath filePathOn = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file);
		return vcs.fileExistsInVcs(filePathOn);
	}
}
