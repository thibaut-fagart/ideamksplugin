package org.intellij.vcs.mks.realtime;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.intellij.vcs.mks.MKSHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.vcsUtil.VcsUtil;
import mks.integrations.common.TriclopsException;
import mks.integrations.common.TriclopsSiSandbox;

/**
 * @author Thibaut Fagart
 */
public class SandboxCacheImpl implements SandboxCache {
    private final Logger LOGGER = Logger.getInstance(getClass().getName());
    private final Object lock = new Object();
    /**
     * sandboxFolder virtualFile => TriclopsSiSandbox
     */
    private final HashMap<VirtualFile, TriclopsSiSandbox> sandboxByFolder = new HashMap<VirtualFile, TriclopsSiSandbox>();
    private final HashMap<VirtualFile, VirtualFile> folderBySandboxFile = new HashMap<VirtualFile, VirtualFile>();
    private final HashSet<VirtualFile> outOfScopeSandboxes = new HashSet<VirtualFile>();
    @NotNull
    private final Project project;
    /**
     * keeps those paths that were rejected because IDEA directoryIndex is not
     * intialized
     */
    private final ArrayList<String> pendingUpdates = new ArrayList<String>();

    final Thread backgroundUpdates = new Thread(new Runnable() {
        static final long SLEEP_TIME = 5000;

        public void run() {
            while (true) {
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException e) {
                }
                ArrayList<String> tempList;
                synchronized (pendingUpdates) {
                    tempList = new ArrayList<String>(pendingUpdates.size());
                    tempList.addAll(pendingUpdates);
                    pendingUpdates.clear();
                }
                for (String sandboxPath : tempList) {
                    LOGGER.info("re-adding"+sandboxPath);
                    addSandboxPath(sandboxPath);
                }
            }
        }
    }, "MKS sandbox synchronizer retrier");

    public SandboxCacheImpl(final Project project) {
        this.project = project;
        backgroundUpdates.setDaemon(true);
        backgroundUpdates.setPriority((Thread.MIN_PRIORITY + Thread.NORM_PRIORITY) / 2);
        backgroundUpdates.start();
    }


    public boolean isSandboxProject(@NotNull VirtualFile virtualFile) {
        return folderBySandboxFile.containsKey(virtualFile) || virtualFile.getName().equals("project.pj");
    }

    // todo semble pas renvoyer la bonne sandbox
    @Nullable
    public TriclopsSiSandbox findSandbox(@NotNull VirtualFile virtualFile) {
        TriclopsSiSandbox sandbox = null;
        VirtualFile cursorDir = (virtualFile.isDirectory() ? virtualFile : virtualFile.getParent());
        for (; cursorDir != null && sandbox == null; cursorDir = cursorDir.getParent())
        {
            sandbox = sandboxByFolder.get(cursorDir);
        }
        return sandbox;
    }

    public void addSandboxPath(@NotNull String sandboxPath) {

            VirtualFile sandboxVFile = VcsUtil.getVirtualFile(sandboxPath);
            VirtualFile sandboxFolder;
            if (sandboxVFile != null) {
                sandboxFolder = (sandboxVFile.isDirectory()) ? sandboxVFile : sandboxVFile.getParent();
//                if (project.getAllScope().contains(sandboxVFile)) {
                boolean isSandboxInProject = false;
                try {
                    isSandboxInProject = project.getProjectScope().contains(sandboxVFile);
                } catch (Throwable e) {
                    LOGGER.warn("caught exception while checking if [" + sandboxVFile + "] is in project, postponing check");
                    addRejected(sandboxPath);
                }
                synchronized (lock) {
                    if (isSandboxInProject) {
                        // ok sandbox in project path
                        try {
                            TriclopsSiSandbox sandbox = MKSHelper.createSandbox(sandboxPath);
                            sandboxByFolder.put(sandboxFolder, sandbox);
                            folderBySandboxFile.put(sandboxVFile, sandboxFolder);
                            LOGGER.info("updated sandbox in cache : " + sandboxVFile);
                        } catch (TriclopsException e) {
                            LOGGER.error("invalid sandbox ? (" + sandboxPath + ")", e);
                            addRejected(sandboxPath);
                        }
                    } else {
                        LOGGER.info("ignoring out-of-project sandbox " + sandboxVFile);
                        outOfScopeSandboxes.add(sandboxVFile);
                    }
                }
            } else {
                LOGGER.error("unable to find the virtualFile for " + sandboxPath);
                addRejected(sandboxPath);
            }

    }

    private void addRejected(final String sandboxPath) {
        synchronized (pendingUpdates) {
            pendingUpdates.add(sandboxPath);
        }
    }

    public void clear() {
        synchronized (lock) {
            sandboxByFolder.clear();
            folderBySandboxFile.clear();
            outOfScopeSandboxes.clear();
        }
    }

    // for mks monitoring
    public void dumpStateOn(PrintWriter pw) {
        pw.println("in project sandboxes");
        List<VirtualFile> sortList = new ArrayList<VirtualFile>(folderBySandboxFile.keySet());
        Comparator<VirtualFile> comparator = new Comparator<VirtualFile>() {
            public int compare(final VirtualFile virtualFile, final VirtualFile other) {
                if (virtualFile == null) {
                    return -1;
                } else if (other == null) {
                    return 1;
                } else {
                    return virtualFile.toString().compareTo(other.toString());
                }
            }
        };
        Collections.sort(sortList, comparator);
        for (VirtualFile virtualFile : sortList) {
            pw.println(virtualFile);
        }
        sortList.clear();
        sortList.addAll(outOfScopeSandboxes);
        Collections.sort(sortList, comparator);

        pw.println("OUT OF project sandboxes");
        for (VirtualFile virtualFile : sortList) {
            pw.println(virtualFile);
        }
    }

    public void beforeRootsChange(final ModuleRootEvent event) {
    }

    public void rootsChanged(final ModuleRootEvent event) {
        System.err.println("rootsChanged");
        synchronized (lock) {
            List<VirtualFile> allSandboxes  = new ArrayList<VirtualFile>(folderBySandboxFile.size()+outOfScopeSandboxes.size());
            allSandboxes.addAll(folderBySandboxFile.keySet());
            allSandboxes.addAll(outOfScopeSandboxes);
            clear();
            for (VirtualFile virtualFile : allSandboxes) {
                addSandboxPath(virtualFile.getPath());
            }
        }
    }
}