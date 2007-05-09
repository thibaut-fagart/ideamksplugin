package org.intellij.vcs.mks.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import mks.integrations.common.TriclopsException;
import mks.integrations.common.TriclopsSiMember;
import mks.integrations.common.TriclopsSiMembers;
import mks.integrations.common.TriclopsSiSandbox;
import org.intellij.vcs.mks.DispatchBySandboxCommand;
import org.intellij.vcs.mks.MKSHelper;
import org.intellij.vcs.mks.MksConfiguration;
import org.intellij.vcs.mks.MksVcs;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class BasicAction extends AnAction {
	protected MksConfiguration configuration;
	private final Logger LOG = Logger.getInstance(getClass().getName());
	protected static final String ACTION_CANCELLED_MSG = "The command was cancelled.";

	public BasicAction() {
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		final Project project = event.getData(DataKeys.PROJECT);
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			public void run() {
				FileDocumentManager.getInstance().saveAllDocuments();
			}
		});
		final VirtualFile[] vFiles = event.getData(DataKeys.VIRTUAL_FILE_ARRAY);

		final MksVcs mksvcs = MksVcs.getInstance(project);
		if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(mksvcs, vFiles)) {
			return;
		}

		String actionName = getActionName(mksvcs);
		AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
		com.intellij.openapi.localVcs.LvcsAction action = helper.startVcsAction(actionName);

		try {
// not sure this is usefull, according do javadoc
			//Runs the runnable inside the vcs transaction (if needed), collects all exceptions, commits/rollbacks transaction and returns all exceptions together.
			// todo but what kind of commit/rollback will be done against mks ?
			List<VcsException> exceptions = helper.runTransactionRunnable(mksvcs, new TransactionRunnable() {
				public void run(List exceptions) {
					final VirtualFile[] affectedFiles = collectAffectedFiles(project, vFiles);
					//noinspection unchecked
					perform(project, mksvcs, exceptions, affectedFiles);
					refreshFiles(project, affectedFiles);

				}

			}, null);
			mksvcs.showErrors(exceptions, actionName);
		} finally {
			helper.finishVcsAction(action);
		}
	}


	private void refreshFiles(@NotNull final Project project, @NotNull final VirtualFile[] affectedFiles) {
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			public void run() {
				for (VirtualFile file : affectedFiles) {
					file.refresh(false, true);
					FileStatusManager.getInstance(project).fileStatusChanged(file);
				}
			}

		});

	}

	protected abstract void perform(@NotNull Project project, MksVcs mksVcs, @NotNull List<VcsException> exceptions, @NotNull VirtualFile[] affectedFiles);

	/**
	 * given a list of action-target files, returns ALL the files that should be subject to the action
	 * Does not keep directories, but recursively adds directory contents
	 *
	 * @param project the project subject of the action
	 * @param files   the root selection
	 * @return the complete set of files this action should apply to
	 */
	@NotNull
	protected VirtualFile[] collectAffectedFiles(@NotNull Project project, @NotNull VirtualFile[] files) {
		List<VirtualFile> affectedFiles = new ArrayList<VirtualFile>(files.length);
		ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);
		for (VirtualFile file : files) {
			if (!file.isDirectory() && projectLevelVcsManager.getVcsFor(file) instanceof MksVcs) {
				affectedFiles.add(file);
			} else if (file.isDirectory() && isRecursive()) {
				addChildren(project, affectedFiles, file);
			}

		}
		return affectedFiles.toArray(new VirtualFile[0]);
	}

	/**
	 * recursively adds all the children of file to the files list, for which this action makes sense
	 * ({@link #appliesTo(com.intellij.openapi.project.Project,com.intellij.openapi.vfs.VirtualFile)} returns true)
	 *
	 * @param project the project subject of the action
	 * @param files   result list
	 * @param file	the file whose children should be added to the result list (recursively)
	 */
	private void addChildren(@NotNull Project project, @NotNull List<VirtualFile> files, @NotNull VirtualFile file) {
		VirtualFile[] children = file.getChildren();
		for (VirtualFile child : children) {
			if (!child.isDirectory() && appliesTo(project, child)) {
				files.add(child);
			} else if (child.isDirectory() && isRecursive()) {
				addChildren(project, files, child);
			}
		}
	}

	@NotNull
	protected abstract String getActionName(@NotNull AbstractVcs abstractvcs);

	protected boolean isRecursive() {
		return true;
	}

	protected boolean appliesTo(@NotNull Project project, @NotNull VirtualFile file) {
		return !file.isDirectory();
	}


	/**
	 * precondition : ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(mksvcs, vFiles)
	 *
	 * @param vcs   the vcs for this project
	 * @param files a list of files, all should be under mks, but do not to have to be under the same sandbox
	 * @return an array of same-sandbox TriclopsSiMembers
	 * @throws VcsException if anything goes wrong
	 */
	@NotNull
	protected TriclopsSiMembers[] createSiMembers(@NotNull MksVcs vcs, @NotNull VirtualFile... files) throws VcsException {
		try {
			DispatchBySandboxCommand dispatchAction = new DispatchBySandboxCommand(new ArrayList<VcsException>(), files);
			dispatchAction.execute();

			Map<TriclopsSiSandbox, ArrayList<VirtualFile>> filesBysandbox = dispatchAction.getFilesBySandbox();
			TriclopsSiMembers[] result = new TriclopsSiMembers[filesBysandbox.size()];
			int i = 0;
			for (Map.Entry<TriclopsSiSandbox, ArrayList<VirtualFile>> entry : filesBysandbox.entrySet()) {
				TriclopsSiMembers members = MKSHelper.createMembers(entry.getKey());
				result[i++] = members;
				for (VirtualFile virtualFile : entry.getValue()) {
					members.addMember(new TriclopsSiMember(virtualFile.getPresentableUrl()));
					MKSHelper.getMembersStatus(members);
				}
			}
			return result;
		} catch (TriclopsException e) {
			throw new VcsException("Unable to obtain file status");
		}

	}

	/**
	 * disable the action if the event does not apply on MksVcs enabled resources
	 * Hide it if the event does not have a project, or if no VirtualFiel are targeted
	 *
	 * @param e the update event
	 */
	@Override
	public void update(@NotNull AnActionEvent e) {
		super.update(e);
		Presentation presentation = e.getPresentation();
		DataContext dataContext = e.getDataContext();
		Project project = (Project) dataContext.getData(MksVcs.DATA_CONTEXT_PROJECT);
		if (project == null) {
			presentation.setEnabled(false);
			presentation.setVisible(false);
			return;
		}

		VirtualFile[] vFiles = (VirtualFile[]) dataContext.getData(MksVcs.DATA_CONTEXT_VIRTUAL_FILE_ARRAY);
		if (vFiles == null || vFiles.length == 0) {
			presentation.setEnabled(false);
			presentation.setVisible(true);
			return;
		}
		MksVcs mksvcs = MksVcs.getInstance(project);
		boolean enabled = ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(mksvcs, vFiles)
			&& isEnabled(project, mksvcs, vFiles);
		// only enable action if all the targets are under the vcs and the action suports all of them

		presentation.setEnabled(enabled);
		presentation.setVisible(enabled);
	}

	protected abstract boolean isEnabled(@NotNull Project project, @NotNull MksVcs mksvcs, @NotNull VirtualFile... vFiles);

}
