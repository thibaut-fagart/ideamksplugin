package org.intellij.vcs.mks.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.vcs.mks.MksVcs;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ViewGlobalChangesAction extends BasicAction {
	private static final Logger LOGGER = Logger.getInstance(ViewGlobalChangesAction.class.getName());

	@NotNull
	@Override
	protected String getActionName(@NotNull AbstractVcs abstractvcs) {
		return "View Global Changes";
	}

	@Override
	protected void perform(@NotNull Project project, MksVcs mksvcs, @NotNull List<VcsException> exceptions, @NotNull VirtualFile[] affectedFiles) {
		mksvcs.debug("ViewGlobalChangesAction " + affectedFiles);
		Map<VirtualFile, FileStatus> statuses = ApplicationManager.getApplication().runReadAction(new MksVcs.CalcStatusComputable(mksvcs, Arrays.asList(affectedFiles)));
		mksvcs.setChanges(statuses);
	}

	@Override
	protected boolean isEnabled(@NotNull Project project, @NotNull MksVcs mksvcs, @NotNull VirtualFile... vFiles) {
		return true;
	}

	@Override
	protected boolean isRecursive() {
		return true;
	}

	@Override
	protected boolean appliesTo(@NotNull Project project, @NotNull VirtualFile file) {
		return super.appliesTo(project, file) && !ProjectRootManager.getInstance(project).getFileIndex().isIgnored(file);
	}
}
