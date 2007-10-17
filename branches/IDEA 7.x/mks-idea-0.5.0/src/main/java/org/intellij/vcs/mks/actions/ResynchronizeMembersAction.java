package org.intellij.vcs.mks.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import mks.integrations.common.TriclopsException;
import mks.integrations.common.TriclopsSiMembers;
import org.intellij.vcs.mks.MKSHelper;
import org.intellij.vcs.mks.MksVcs;

// Referenced classes of package org.intellij.vcs.mks.actions:
//            BasicAction

public class ResynchronizeMembersAction extends BasicAction {

    public ResynchronizeMembersAction() {
    }

    protected void perform(Project project, Module module, MksVcs vcs, VirtualFile file, DataContext dataContext)
            throws VcsException {
        TriclopsSiMembers members = createSiMembers(file, vcs);
        try {
            MKSHelper.resyncMembers(members, 0);
        }
        catch (TriclopsException e) {
            if (!MksVcs.isLastCommandCancelled())
                throw new VcsException("Resynchronize Error: " + MksVcs.getMksErrorMessage());
        }
        WindowManager.getInstance().getStatusBar(project).setInfo("Resynchronize complete.");
    }

    protected String getActionName(AbstractVcs vcs) {
        return "Resynchronize";
    }

    protected boolean isEnabled(Project project, AbstractVcs vcs, VirtualFile file) {
        return true;
    }
}