package org.intellij.vcs.mks;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import mks.integrations.common.TriclopsException;
import mks.integrations.common.TriclopsSiMember;
import mks.integrations.common.TriclopsSiMembers;
import mks.integrations.common.TriclopsSiSandbox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Command pattern, also feeds the {@link #errors} attribute with errors happening during execution
 *
 * @author Thibaut Fagart
 */
public abstract class AbstractMKSCommand {
	protected List<VcsException> errors;

	public AbstractMKSCommand(List<VcsException> errors) {
		this.errors = errors;
	}

	public abstract void execute();

	protected TriclopsSiMembers queryMksMemberStatus(ArrayList<VirtualFile> files, TriclopsSiSandbox sandbox) throws TriclopsException {
		TriclopsSiMembers members = MKSHelper.createMembers(sandbox);
		for (VirtualFile virtualFile : files) {
			members.addMember(new TriclopsSiMember(virtualFile.getPresentableUrl()));
		}
		MKSHelper.getMembersStatus(members);
		return members;
	}

	protected HashSet<VirtualFile> getAllMksKnownFoldersInProject(VirtualFile sandboxFolder, String[] projectMembers) {
		HashSet<VirtualFile> projectFolders = new HashSet<VirtualFile>();
		projectFolders.add(sandboxFolder);
		for (String projectMemberRelativePath : projectMembers) {
			int lastIndexOfSlash = projectMemberRelativePath.lastIndexOf('/');
			if (lastIndexOfSlash >= 0) {
				VirtualFile projectFolder = sandboxFolder.findFileByRelativePath(projectMemberRelativePath.substring(0, lastIndexOfSlash));
				if (projectFolder != null && !projectFolders.contains(projectFolder)) {
					if (MksVcs.LOGGER.isDebugEnabled()) {
						MksVcs.LOGGER.debug("detected project folder :" + projectFolder);
					}
					// add all parent folders : takes care of parent folders that do not have any file children (only directories)
					do {
						projectFolders.add(projectFolder);
						projectFolder = projectFolder.getParent();
					}
					while (projectFolder != null && !projectFolders.contains(projectFolder) && !projectFolder.equals(sandboxFolder));
				} else if (projectFolder == null) {
					MksVcs.LOGGER.debug("can't find folder for path " + projectMemberRelativePath);
				}
			}
		}
		return projectFolders;
	}
}
