package org.intellij.vcs.mks;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import javax.swing.*;
import org.intellij.vcs.mks.sicommands.GetRevisionInfo;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;

/**
 * @author Thibaut Fagart
 */
public class MksDiffProvider implements DiffProvider {
    private final Logger LOGGER = Logger.getInstance(getClass().getName());
    private final MksVcs mksVcs;

    public MksDiffProvider(final MksVcs mksVcs) {
        this.mksVcs = mksVcs;
    }

    @Nullable
    public VcsRevisionNumber getCurrentRevision(final VirtualFile virtualFile) {
        ArrayList<VcsException> errors = new ArrayList<VcsException>();
        GetRevisionInfo command = getRevisionInfo(virtualFile, errors);
        if (errors.isEmpty()) {
            return command.getWorkingRev();
        } else {
            LOGGER.warn("error occurred org.intellij.vcs.mks.MksDiffProvider.getCurrentRevision");
            return null;
        }
    }

    private GetRevisionInfo getRevisionInfo(final VirtualFile virtualFile, final ArrayList<VcsException> errors) {
        GetRevisionInfo command = new GetRevisionInfo(errors, mksVcs, virtualFile.getPath(), VfsUtil.virtualToIoFile(virtualFile.getParent()));
        command.execute();
		if (command.errors.isEmpty()) {
        return command;
		} else if (errors.size() == 1 && errors.get(0).getMessage().equals(GetRevisionInfo.NOT_A_MEMBER)){
			Messages.showMessageDialog("Not (or not any more) a member", "title", Messages.getInformationIcon());
			return command;
		} else {
			LOGGER.warn("error occurred retrieving version info for "+virtualFile.getPresentableName());
			return command;
        }
    }

    @Nullable
    public VcsRevisionNumber getLastRevision(final VirtualFile virtualFile) {
        ArrayList<VcsException> errors = new ArrayList<VcsException>();
        GetRevisionInfo command = getRevisionInfo(virtualFile, errors);
        if (errors.isEmpty()) {
            return command.getMemberRev();
        } else {
            LOGGER.warn("error occurred org.intellij.vcs.mks.MksDiffProvider.getLastRevision");
            return null;
        }
    }

    @Nullable
    public ContentRevision createFileContent(final VcsRevisionNumber vcsRevisionNumber, final VirtualFile virtualFile) {
	    if (VcsRevisionNumber.NULL.equals(vcsRevisionNumber)) {
		    try {
			    SwingUtilities.invokeAndWait( new Runnable() {
				    public void run() {
					    Messages.showWarningDialog("This revision is not mks controlled", "Error");
				    }
			    });
		    } catch (InterruptedException e) {
			    Thread.currentThread().interrupt();
		    } catch (InvocationTargetException e) {
			    LOGGER.error(e.getTargetException());
		    }

		    return null;
	    }
        return new MksContentRevision(mksVcs, PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(virtualFile), vcsRevisionNumber);
    }
}
