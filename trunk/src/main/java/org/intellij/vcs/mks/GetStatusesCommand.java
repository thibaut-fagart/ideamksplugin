package org.intellij.vcs.mks;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import mks.integrations.common.TriclopsException;
import mks.integrations.common.TriclopsSiMember;
import mks.integrations.common.TriclopsSiMembers;
import mks.integrations.common.TriclopsSiSandbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Thibaut Fagart
 */
public class GetStatusesCommand extends AbstractMKSCommand {
    private MksVcs mksvcs;
    private final TriclopsSiSandbox sandbox;
    private final ArrayList<VirtualFile> files;
    public final HashMap<VirtualFile, FileStatus> statuses;

    public GetStatusesCommand(MksVcs mksvcs, ArrayList<VcsException> errors, TriclopsSiSandbox sandbox,
                              ArrayList<VirtualFile> files) {
        super(errors);
        this.mksvcs = mksvcs;
        this.sandbox = sandbox;
        this.files = files;
        statuses = new HashMap<VirtualFile, FileStatus>();
    }

    @Override
    public void execute() {
        long deb = System.currentTimeMillis();
        try {
            // todo begin
            String[] projectMembers;
            // la liste des fichiers qu'on va envoyer a mks
            ArrayList<VirtualFile> filesToQueryMksFor = new ArrayList<VirtualFile>();
            try {
                projectMembers = MKSHelper.getProjectMembers(sandbox);
            } catch (TriclopsException e) {
                MksVcs.LOGGER.info("could not get project members for sandbox : " + sandbox.getPath() + ", project:" + sandbox.getSandboxProject());
                projectMembers = null;
            }
            if (projectMembers != null) {
                // le virtual file du project.pj de la sandbox
                VirtualFile sandboxFolder = getSandboxFolder(files.get(0).getFileSystem(), sandbox);
                if (sandboxFolder == null) {
                    return;
                }
                HashSet<VirtualFile> projectFolders = getAllMksKnownFoldersInProject(sandboxFolder, projectMembers);

                //  We don't query mks for files not under one of the mks known folders
                for (VirtualFile virtualFile : files) {
                    if (virtualFile.isDirectory()) {
                        statuses.put(virtualFile, (projectFolders.contains(virtualFile)) ? FileStatus.NOT_CHANGED : FileStatus.UNKNOWN);
                    } else {
                        // add the file to the files to be queried only if it's parent folder is known by mks
                        if (projectFolders.contains(virtualFile.getParent())) {
                            filesToQueryMksFor.add(virtualFile);
                        } else {
                            // mksvcs.debug(virtualFile.getPath()+" does not seem to be part of the sandbox "+sandbox.getPath());
                            statuses.put(virtualFile, FileStatus.UNKNOWN);
                        }
                    }
                }
                if (MksVcs.LOGGER.isDebugEnabled()) {
                    MksVcs.LOGGER.debug("locally resolved " + statuses.size() + "n querying mks for " + filesToQueryMksFor.size());
                }
                // at this point filesToQueryMksFor only contains mks controlled files (or direct children of mks known folders)
            } else {
                filesToQueryMksFor = files;
            }
            // todo end
            TriclopsSiMembers members = queryMksMemberStatus(filesToQueryMksFor, sandbox);

            for (int i = 0; i < members.getNumMembers(); i++) {
                TriclopsSiMember member = members.getMember(i);
                // directories status is not available in mks
                VirtualFile virtualFile = filesToQueryMksFor.get(i);
                FileStatus status = mksvcs.getIdeaStatus(sandbox, member, virtualFile);
                statuses.put(filesToQueryMksFor.get(i), status);
            }
        } catch (TriclopsException e) {
            //noinspection ThrowableInstanceNeverThrown
            errors.add(new VcsException("unable to get status" + "\n" + MksVcs.getMksErrorMessage()));
        } catch (VcsException e) {
            errors.add(e);
        }
        long end = System.currentTimeMillis();
        if (MksVcs.LOGGER.isDebugEnabled()) {
            MksVcs.LOGGER.debug("got statuses for " + sandbox.getPath() + " in " + (end - deb));
        }

    }

    private VirtualFile getSandboxFolder(VirtualFileSystem aFileSystem, TriclopsSiSandbox aSandbox) throws VcsException {
        VirtualFile sandboxVFile = aFileSystem.findFileByPath(aSandbox.getPath().replace('\\', '/'));
        if (sandboxVFile == null) {
            throw new VcsException("can't find IDEA VirtualFile for sandbox");
        }
        // le repertoire de la sandbox
        VirtualFile sandboxFolder = sandboxVFile.getParent();
        if (sandboxFolder == null) {
            MksVcs.LOGGER.info("cant find sandbox folder)");
        }
        return sandboxFolder;
    }
}
