package org.intellij.vcs.mks.realtime;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.vcsUtil.VcsUtil;
//import org.intellij.vcs.mks.MKSHelper;
import org.intellij.vcs.mks.MksRevisionNumber;
import org.intellij.vcs.mks.MksVcs;
import org.intellij.vcs.mks.model.MksMemberState;
import org.intellij.vcs.mks.sicommands.cli.AbstractViewSandboxCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public abstract class AbstractSandboxCacheImpl implements SandboxCache {
    protected final HashSet<VirtualFile> sandboxVFiles = new HashSet<VirtualFile>();
    protected final Logger LOGGER = Logger.getInstance(getClass().getName());
    protected final Object lock = new Object();
    /**
     * sandboxFolder virtualFile => TriclopsSiSandbox
     */
    protected final HashMap<VirtualFile, List<MksSandboxInfo>> sandboxByFolder =
            new HashMap<VirtualFile, List<MksSandboxInfo>>();
    protected final HashSet<MksSandboxInfo> outOfScopeSandboxes = new HashSet<MksSandboxInfo>();
    @NotNull
    protected final Project project;

    public AbstractSandboxCacheImpl(final Project project) {
        this.project = project;
        ProjectLevelVcsManager.getInstance(project).addVcsListener(this);
    }

    public boolean isSandboxProject(@NotNull final VirtualFile virtualFile) {
        return sandboxVFiles.contains(virtualFile) || virtualFile.getName().equals(MksVcs.PROJECT_PJ_FILE);
    }

    public void removeSandboxPath(@NotNull final String sandboxPath, final boolean isSubSandbox) {
        if (doesSandboxIntersectProject(new File(sandboxPath))) {
            final VirtualFile sandboxVFile = VcsUtil.getVirtualFile(sandboxPath);
            if (sandboxVFile == null) {
                LOGGER.warn("trying to remove a sandbox with a null virtual file " + sandboxPath);
                return;
            }
            synchronized (lock) {
                if (!sandboxVFiles.contains(sandboxVFile)) {
                    LOGGER.warn("trying to remove a sandbox not in sandboxVFiles " + sandboxPath);
                } else {
                    sandboxVFiles.remove(sandboxVFile);
                    final VirtualFile parent = sandboxVFile.getParent();
                    final List<MksSandboxInfo> infoList = sandboxByFolder.get(parent);

                    for (Iterator<MksSandboxInfo> it = infoList.iterator(); it.hasNext(); ) {
                        final MksSandboxInfo sandboxInfo = it.next();
                        if (sandboxInfo.sandboxPath.equals(sandboxPath)) {
                            it.remove();
                            break;
                        }
                    }
                }
            }
        } else {
            for (Iterator<MksSandboxInfo> it = outOfScopeSandboxes.iterator(); it.hasNext(); ) {
                final MksSandboxInfo sandbox = it.next();
                if (sandboxPath.equals(sandbox.sandboxPath)) {
                    it.remove();
                    return;
                }
            }
        }
    }

    /**
     * @param sandboxFile the sandbox file (aka project.pj)
     * @return true if the sandbox is either UNDER the current project or above it,
     *         eg if at least part of the files controlled by the sandbox should be
     *         monitored
     */
    protected boolean doesSandboxIntersectProject(@NotNull final File sandboxFile) {
        final File sandboxFolder = sandboxFile.getParentFile();
        final GlobalSearchScope projectScope = ProjectScope.getProjectScope(project);
        final VirtualFile sandboxVFile = VcsUtil.getVirtualFile(sandboxFile);
        boolean sandboxRelevant = (sandboxVFile != null) && projectScope.contains(sandboxVFile);
        if (!sandboxRelevant) {
            final VirtualFile[] projectContentRoots = ProjectRootManager.getInstance(project).getContentRoots();
            for (int i = 0; i < projectContentRoots.length && !sandboxRelevant; i++) {
                final VirtualFile projectContentRoot = projectContentRoots[i];
                if (VfsUtil.isAncestor(sandboxFolder, VfsUtil.virtualToIoFile(projectContentRoot), true)) {
                    LOGGER.debug("sandbox [" + sandboxFolder + "] contains contentRoot [" + projectContentRoot + "]");
                    sandboxRelevant = true;
                }
            }
        }
        return sandboxRelevant;
    }

    public void addSandboxPath(@NotNull final String sandboxPath, @NotNull final String mksHostAndPort,
                               @NotNull final String mksProject, @Nullable final String devPath, final boolean isSubSandbox) {

        final VirtualFile sandboxVFile = VcsUtil.getVirtualFile(sandboxPath);
        final MksSandboxInfo sandboxInfo =
                createSandboxInfo(sandboxPath, mksHostAndPort, mksProject, devPath, isSubSandbox, sandboxVFile);
        addSandbox(sandboxInfo);

    }

    protected abstract MksSandboxInfo createSandboxInfo(String sandboxPath, String mksHostAndPort, String mksProject, String devPath, boolean isSubSandbox, VirtualFile sandboxVFile);

    protected void addSandbox(final MksSandboxInfo sandboxInfo) {
        if (doesSandboxIntersectProject(new File(sandboxInfo.sandboxPath))) {
            addSandboxBelongingToProject(sandboxInfo);
        } else {
            outOfScopeSandboxes.add(sandboxInfo);
            LOGGER.debug("ignoring out of project sandbox [" + sandboxInfo.sandboxPath + "]");
        }
    }

    public void updateSandboxPath(@NotNull final String sandboxPath, @NotNull final String serverHostAndPort,
                                  @NotNull final String mksProject, @Nullable final String devPath, final boolean isSubSandbox) {
        if (doesSandboxIntersectProject(new File(sandboxPath))) {
            final VirtualFile sandboxVFile = VcsUtil.getVirtualFile(sandboxPath);
            synchronized (lock) {
                if (!sandboxVFiles.contains(sandboxVFile)) {
                    LOGGER.warn("trying to remove a sandbox not in sandboxVFiles " + sandboxPath);
                } else if (sandboxVFile == null) {
                    LOGGER.warn("trying to update a sandbox with no sandboxVFile " + sandboxPath);
                } else {
                    final List<MksSandboxInfo> infoList = sandboxByFolder.get(sandboxVFile.getParent());

                    for (Iterator<MksSandboxInfo> iterator = infoList.iterator(); iterator.hasNext(); ) {
                        MksSandboxInfo sandboxInfo = iterator.next();
                        if (sandboxInfo.sandboxPath.equals(sandboxPath)) {
                            infoList.remove(sandboxInfo);
                            infoList.add(createSandboxInfo(sandboxPath, serverHostAndPort, mksProject, devPath, isSubSandbox, sandboxVFile));
                            break;
                        }
                    }
                }
            }
        } else {
            for (Iterator<MksSandboxInfo> iterator = outOfScopeSandboxes.iterator(); iterator.hasNext(); ) {
                MksSandboxInfo sandbox = iterator.next();
                if (sandboxPath.equals(sandbox.sandboxPath)) {
                    outOfScopeSandboxes.remove(sandbox);
                    outOfScopeSandboxes.add(createSandboxInfo(sandboxPath, serverHostAndPort, mksProject, devPath, isSubSandbox, null));
                    return;
                }
            }
        }
    }

    @Nullable
    public MksSandboxInfo getSubSandbox(@NotNull final VirtualFile virtualFile) {
        return getSandbox(virtualFile, true);
    }

    /**
     * returns the highest level non ambiguous sandbox for the given file
     *
     * @param virtualFile the virtual file we want the parent sandbox for (may be a directory)
     * @return the sandbox containing  the give file if one exists, null otherwise
     */
    @Nullable
    public MksSandboxInfo getSandboxInfo(@NotNull final VirtualFile virtualFile) {
        return getSandbox(virtualFile, false);
    }

    /**
     * @param virtualFile a file or a directory
     * @param closest     if true, then the deepest (sub)sandbox will be returned, if false only top level sandboxes will match
     * @return
     */
    private MksSandboxInfo getSandbox(@NotNull final VirtualFile virtualFile, final boolean closest) {
        MksSandboxInfo sandbox = null;
        VirtualFile cursorDir = (virtualFile.isDirectory() ? virtualFile : virtualFile.getParent());
        MksSandboxInfo foundSubSandbox = null;
        // walk up directory hierarchy until we find a (or several) sandbox
        for (; cursorDir != null && sandbox == null; cursorDir = cursorDir.getParent()) {
            final List<MksSandboxInfo> infoList = sandboxByFolder.get(cursorDir);
            if (infoList == null || infoList.isEmpty()) {
                // no sandbox for this folder
                continue;
            } else if (infoList.size() == 1) {
                // only one sandbox
                final MksSandboxInfo sandboxInfo = infoList.get(0);
                if (sandboxInfo.isSubSandbox) {
                    foundSubSandbox = sandboxInfo;
                } else {
                    sandbox = sandboxInfo;
                }
            } else {
                // several sandboxes in this directory
                if (foundSubSandbox != null) {
                    sandbox = foundSubSandbox;
                } else if (!virtualFile.isDirectory()) {
                    // ambiguous sandbox, check them all
                    for (Iterator<MksSandboxInfo> iterator = infoList.iterator(); iterator.hasNext(); ) {
                        MksSandboxInfo mksSandboxInfo = iterator.next();
                        if (checkSandboxContains(mksSandboxInfo, virtualFile)) {
                            sandbox = mksSandboxInfo;
                            break;
                        }
                    }
                } else {
                    LOGGER.warn("unable to find sandbox for " + virtualFile);
                }
            }
            if (closest && foundSubSandbox != null) {
                return foundSubSandbox;
            }
        }
        return sandbox;
    }

    /**
     * This only works when sandbox is the bottom most subsandbox including
     * virtualfile. Thus this is not supported when subsandboxes are not monitored
     *
     * @param sandbox     the candidate sandbox
     * @param virtualFile the file
     * @return true if sandbox is the bottom most subsandbox including virtualfile.
     */
    private boolean checkSandboxContains(@NotNull final MksSandboxInfo sandbox, @NotNull final VirtualFile virtualFile) {
        if (virtualFile.isDirectory()) {
            throw new IllegalArgumentException("directories don't belong to sandboxes");
        }

        final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(virtualFile);
        if (!filePath.getIOFile().exists() || sandbox.sandboxPjFile == null) {
            return false;
        }
        final FilePath sandboxFolderFilePath =
                VcsContextFactory.SERVICE.getInstance().createFilePathOn(sandbox.sandboxPjFile.getParent());
        final String relativePath = MksVcs.getInstance(project).getRelativePath(filePath, sandboxFolderFilePath);
        if ("".equals(relativePath.trim())) {
            LOGGER.warn("no relative path for " + virtualFile + " from " + sandboxFolderFilePath +
                    ", assuming different sandboxes");
            return false;
        }
        final AbstractViewSandboxCommand command =
                new AbstractViewSandboxCommand(new ArrayList<VcsException>(), MksVcs.getInstance(project),
                        sandbox.sandboxPath,
                        "--filter=file:" + relativePath) {
                    @Override
                    protected MksMemberState createState(final String workingRev, final String memberRev, final String workingCpid,
                                                         final String locker, final String lockedSandbox, final String type,
                                                         final String deferred) throws VcsException {
                        return new MksMemberState(MksRevisionNumber.createRevision(workingRev),
                                MksRevisionNumber.createRevision(memberRev), workingCpid,
                                MksMemberState.Status.UNKNOWN);
                    }
                };
        command.execute();
        if (command.foundError()) {
            LOGGER.error("error while checking if sandbox " + sandbox + " contains " + virtualFile);
            for (final VcsException error : command.errors) {
                LOGGER.warn(error);
            }

        }
        return command.getMemberStates().get(filePath.getPath()) != null;

    }

    protected void dirDirtyRecursively(final VirtualFile sandboxParentFolderVFile) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                    public void run() {
                        if (!project.isDisposed()) {
                            LOGGER.warn("dirtying recursively " + sandboxParentFolderVFile);
                            VcsDirtyScopeManager.getInstance(project)
                                    .dirDirtyRecursively(sandboxParentFolderVFile);
                        }
                    }
                });
            }
        });
    }

    // for mks monitoring
    public void dumpStateOn(@NotNull final PrintWriter pw) {
        pw.println("in project sandboxes");

        final List<MksSandboxInfo> sortList = new ArrayList<MksSandboxInfo>(sandboxByFolder.size());
        for (final List<MksSandboxInfo> infoList : sandboxByFolder.values()) {
            for (final MksSandboxInfo sandboxInfo : infoList) {
                if (!sandboxInfo.isSubSandbox) {
                    sortList.add(sandboxInfo);
                }
            }
        }
        final Comparator<MksSandboxInfo> comparator = new Comparator<MksSandboxInfo>() {
            public int compare(final MksSandboxInfo first, final MksSandboxInfo other) {
                if (first == null) {
                    return -1;
                } else if (other == null) {
                    return 1;
                } else {
                    return first.sandboxPath.compareTo(other.sandboxPath);
                }
            }
        };
        Collections.sort(sortList, comparator);
        for (MksSandboxInfo mksSandboxInfo : sortList) {
            pw.println(mksSandboxInfo);
        }
        sortList.clear();
        for (MksSandboxInfo sandbox : outOfScopeSandboxes) {
            if (!sandbox.isSubSandbox) {
                sortList.add(sandbox);
            }
        }
        Collections.sort(sortList, comparator);

        pw.println("OUT OF project sandboxes");
        for (MksSandboxInfo sandboxInfo : sortList) {
            pw.println(sandboxInfo + "( " + sandboxInfo.sandboxPjFile + ")");
        }
    }

    @Override
    public void release() {
        this.outOfScopeSandboxes.clear();
        this.sandboxByFolder.clear();
        this.sandboxVFiles.clear();

    }

    /**
     * Should only be called for sandbox relevant for the project (eg : sandbox
     * content and project content intersect)
     *
     * @param sandboxInfo the sandbox
     */
    protected void addSandboxBelongingToProject(@NotNull MksSandboxInfo sandboxInfo) {
        final String sandboxPath;
        final VirtualFile sandboxVFile;
        sandboxPath = sandboxInfo.sandboxPath;
        sandboxVFile = sandboxInfo.sandboxPjFile == null ? VcsUtil.getVirtualFile(sandboxInfo.sandboxPath) :
                sandboxInfo.sandboxPjFile;
        if (sandboxVFiles.contains(sandboxVFile)) {
            LOGGER.warn("trying to add an already monitored sandbox " + sandboxInfo);
            return;
        }
        final VirtualFile sandboxFolder;
        if (sandboxVFile != null) {
            sandboxFolder = (sandboxVFile.isDirectory()) ? sandboxVFile : sandboxVFile.getParent();
            if (sandboxFolder == null) {
                LOGGER.warn("unable to find parent VirtualFile for sandbox [" + sandboxVFile + "]");
            }

            synchronized (lock) {
                // ok sandbox in project path
//				try {
                List<MksSandboxInfo> infoList = sandboxByFolder.get(sandboxFolder);
                if (infoList == null) {
                    infoList = new ArrayList<MksSandboxInfo>();
                    sandboxByFolder.put(sandboxFolder, infoList);
                }
                infoList.add(sandboxInfo);
                sandboxVFiles.add(sandboxVFile);
                LOGGER.debug("updated sandbox in cache : " + sandboxVFile);
                if (!sandboxInfo.isSubSandbox) {
                    final VirtualFile sandboxParentFolderVFile = sandboxVFile.getParent();
                    LOGGER.info("marking " + sandboxParentFolderVFile + " as dirty");
// is this necessary
                    dirDirtyRecursively(sandboxParentFolderVFile);
                }
            }
        } else {
            LOGGER.warn("unable to find the virtualFile for " + sandboxPath);
            final VirtualFile sandboxPjFile = VcsUtil.getVirtualFile(sandboxPath);
            if (sandboxPjFile != null) {
                sandboxInfo =
                        createSandboxInfo(sandboxInfo.sandboxPath, sandboxInfo.hostAndPort, sandboxInfo.mksProject, sandboxInfo.devPath, sandboxInfo.isSubSandbox, sandboxPjFile);
            }
            addRejected(sandboxInfo);
        }
    }

    public void rootsChanged(final ModuleRootEvent event) {
        LOGGER.info("rootsChanged, re computing in/out of project sandboxes");
        determineSandboxesInProject();
    }

    protected void determineSandboxesInProject() {
        synchronized (lock) {
            final List<MksSandboxInfo> newSandboxesInProject = new ArrayList<MksSandboxInfo>();
            for (Iterator<MksSandboxInfo> iterator = outOfScopeSandboxes.iterator(); iterator.hasNext(); ) {
                MksSandboxInfo sandbox = iterator.next();
                if (this.doesSandboxIntersectProject(new File(sandbox.sandboxPath))) {
                    newSandboxesInProject.add(sandbox);
                }
            }
            final List<MksSandboxInfo> sandboxesRemovedFromProject = new ArrayList<MksSandboxInfo>();
            final List<VirtualFile> foldersToRemove = new ArrayList<VirtualFile>();
            for (final Map.Entry<VirtualFile, List<MksSandboxInfo>> entry : sandboxByFolder.entrySet()) {
                final List<MksSandboxInfo> sandboxes = entry.getValue();
                for (Iterator<MksSandboxInfo> it = sandboxes.iterator(); it.hasNext(); ) {
                    final MksSandboxInfo sandbox = it.next();
                    if (!this.doesSandboxIntersectProject(new File(sandbox.sandboxPath))) {
                        sandboxesRemovedFromProject.add(sandbox);
                        it.remove();
                    }
                }
                if (sandboxes.isEmpty()) {
                    foldersToRemove.add(entry.getKey());
                }
            }
            outOfScopeSandboxes.removeAll(newSandboxesInProject);
            for (Iterator<MksSandboxInfo> iterator = newSandboxesInProject.iterator(); iterator.hasNext(); ) {
                MksSandboxInfo sandboxInfo = iterator.next();
                addSandboxBelongingToProject(sandboxInfo);
            }
            outOfScopeSandboxes.addAll(sandboxesRemovedFromProject);
            for (final VirtualFile virtualFile : foldersToRemove) {
                sandboxByFolder.remove(virtualFile);
            }
        }
    }

    protected abstract void addRejected(MksSandboxInfo sandbox);

    /**
     * @param directory the directory we want the sandboxes for
     * @return all the TOP sandboxes intersecting the given directory
     */
    @NotNull
    public Set<MksSandboxInfo> getSandboxesIntersecting(@NotNull final VirtualFile directory) {
        final Set<MksSandboxInfo> result = new HashSet<MksSandboxInfo>();

        final ArrayList<List<MksSandboxInfo>> sandboxInfoListOfList;
        synchronized (lock) {
            sandboxInfoListOfList = new ArrayList<List<MksSandboxInfo>>(sandboxByFolder.values());
        }
        for (final List<MksSandboxInfo> infoList : sandboxInfoListOfList) {
            for (Iterator<MksSandboxInfo> iterator = infoList.iterator(); iterator.hasNext(); ) {
                MksSandboxInfo sandboxInfo = iterator.next();
                if (sandboxInfo.isSubSandbox) {
                    continue;
                }
                final VirtualFile sandboxFile = sandboxInfo.sandboxPjFile;
                if (sandboxFile == null) {
                    synchronized (lock) {
                        LOGGER.warn("SandboxInfo with NULL virtualFile !! removing from registered sandboxes");
                        infoList.remove(sandboxInfo);
                        addRejected(sandboxInfo);
                    }
                } else if (VfsUtil.isAncestor(directory, sandboxFile, false)) {
                    result.add(sandboxInfo);
                } else {
                    final VirtualFile sandboxParentFile = sandboxFile.getParent();
                    if (sandboxParentFile != null && VfsUtil.isAncestor(sandboxParentFile, directory, false)) {
                        result.add(sandboxInfo);
                    }
                }
            }
        }
        return result;
    }

    public void beforeRootsChange(final ModuleRootEvent event) {
    }

    public void directoryMappingChanged() {
        LOGGER.info("directoryMappingChanged, re computing in/out of project sandboxes");
        determineSandboxesInProject();
    }


}
