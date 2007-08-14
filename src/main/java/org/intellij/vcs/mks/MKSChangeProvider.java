package org.intellij.vcs.mks;

import java.io.File;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.intellij.vcs.mks.realtime.MksSandboxInfo;
import org.intellij.vcs.mks.sicommands.ListChangePackages;
import org.intellij.vcs.mks.sicommands.ListServers;
import org.intellij.vcs.mks.sicommands.ViewSandboxChangesCommand;
import org.intellij.vcs.mks.sicommands.ViewSandboxWithoutChangesCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListDecorator;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.ChangelistBuilder;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.vcsUtil.VcsUtil;
import mks.integrations.common.TriclopsSiMember;
import mks.integrations.common.TriclopsSiSandbox;

/**
 * @author Thibaut Fagart
 * @see com.intellij.openapi.vcs.changes.VcsDirtyScopeManager allows to notify
 *      idea if files should be marked dirty
 */
class MKSChangeProvider implements ChangeProvider, ProjectComponent, ChangeListDecorator {
    private final Logger LOGGER = Logger.getInstance(getClass().getName());

    @NotNull
    private final MksVcs mksvcs;

    public MKSChangeProvider(@NotNull MksVcs mksvcs) {
        this.mksvcs = mksvcs;
    }


    public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress) throws VcsException {
        ArrayList<VcsException> errors = new ArrayList<VcsException>();
        LOGGER.info("start getChanges");
        try {
            System.out.println("dirtyScope " + dirtyScope);
//			System.out.println("getDirtyFiles " + dirtyScope.getDirtyFiles());
//			System.out.println("getAffectedContentRoots " + dirtyScope.getAffectedContentRoots());
            System.out.println("getRecursivelyDirtyDirectories " + dirtyScope.getRecursivelyDirtyDirectories());
	        ArrayList<ListServers.MksServerInfo> servers = getMksServers(progress, errors);
	        final Map<ListServers.MksServerInfo, Map<String, MksChangePackage>> changePackagesPerServer = getChangePackages(progress, errors, servers);
	        // collect affected sandboxes
	        ChangelistBuilder proxiedBuilder = (ChangelistBuilder) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ChangelistBuilder.class}, new InvocationHandler() {
		        public Object invoke(final Object o, final Method method, final Object[] objects) throws Throwable {
			        StringBuffer buf = new StringBuffer("(");
			        for (int i = 0; i < objects.length; i++) {
				        Object object = objects[i];
				        buf.append(object).append(",");
			        }
			        buf.setLength(buf.length()-1);
			        buf.append(")");
			        System.out.println(method.getName() + buf);
			        return method.invoke(builder, objects);
		        }
	        });
	        Map <String, ListServers.MksServerInfo> serversByHostAndPort = distributeServersByHostAndPort(servers);
	        Map<ListServers.MksServerInfo, Map<String, MksMemberState>> states = new HashMap<ListServers.MksServerInfo, Map<String, MksMemberState>>();
	        for (VirtualFile dir : dirtyScope.getAffectedContentRoots()) {
		        Set<MksSandboxInfo> sandboxes = mksvcs.getSandboxCache().getSandboxesIntersecting(dir);
		        for (MksSandboxInfo sandbox : sandboxes) {
			        ListServers.MksServerInfo sandboxServer = serversByHostAndPort.get(sandbox.hostAndPort);
			        if (states.get(sandboxServer) ==null) {
				        states.put(sandboxServer, new HashMap<String, MksMemberState>());
			        }
			        states.get(sandboxServer).putAll(getSandboxState(sandbox, errors, sandboxServer));
		        }
	        }
	        for (Map.Entry<ListServers.MksServerInfo, Map<String, MksMemberState>> entry : states.entrySet()) {
		        ListServers.MksServerInfo sandboxServer = entry.getKey();
		        processDirtySandbox(proxiedBuilder,changePackagesPerServer.get(sandboxServer),entry.getValue());
	        }
//            final Map<String, List<MksChangePackageEntry>> changePackageEntriesByMksProject = new HashMap<String, List<MksChangePackageEntry>>();
//            final Map<MksChangePackageEntry, MksChangePackage> changePackagesByChangePackageEntry = new HashMap<MksChangePackageEntry, MksChangePackage>();
/*
            for (final MksChangePackage changePackage : changePackagesPerServer) {
                final ListChangePackageEntries listEntries = new ListChangePackageEntries(errors, mksvcs, changePackage);
                if (progress != null) {
                    progress.setIndeterminate(true);
                    progress.setText("Querying change package entries [" + changePackage.getId() + "] ...");
                }
                listEntries.execute();
                if (listEntries.foundError()) {
                    LOGGER.error("failed querying mks cp entries for " + changePackage);
                } else {
                    changePackage.setEntries(listEntries.changePackageEntries);
                    for (MksChangePackageEntry changePackageEntry : listEntries.changePackageEntries) {
                        List<MksChangePackageEntry> list = changePackageEntriesByMksProject.get(changePackageEntry.getProject());
                        if (list == null) {
                            list = new ArrayList<MksChangePackageEntry>();
                            changePackageEntriesByMksProject.put(changePackageEntry.getProject(), list);
                        }
                        list.add(changePackageEntry);
                        changePackagesByChangePackageEntry.put(changePackageEntry, changePackage);
                    }
                }
            }
            if (progress != null) {
                progress.setIndeterminate(true);
                progress.setText("Collecting files to query ...");
            }
            final Collection<VirtualFile> filesTocheckVFiles = new ArrayList<VirtualFile>();
            final Map<VirtualFile, FilePath> filePathsByVFile = new IdentityHashMap<VirtualFile, FilePath>();
            dirtyScope.iterate(new Processor<FilePath>() {
                public boolean process(FilePath filePath) {
//                    assert VcsUtil.isFileUnderVcs(mksvcs.getProject(), filePath) : "file not under vcs : " + filePath;
//					if (VcsUtil.isFileUnderVcs(mksvcs.getProject(), filePath)) {
                    VirtualFile virtualFile = filePath.getVirtualFile();
                    if (virtualFile == null) {
                        LOGGER.warn("null virtual file for path : "+filePath);
                    } else {
                        filesTocheckVFiles.add(virtualFile);
                        filePathsByVFile.put(virtualFile, filePath);
                    }
//					} else {
//						LOGGER.warn("file not under vcs : " + filePath);
//					}

                    return true;
                }
            });
            if (progress != null) {
                progress.setText("Dispatching files by sandbox");
            }
            DispatchBySandboxCommand dispatchCommand = new DispatchBySandboxCommand(mksvcs, errors, filesTocheckVFiles.toArray(new VirtualFile[filesTocheckVFiles.size()]));
            dispatchCommand.execute();
            final Map<TriclopsSiSandbox, ArrayList<VirtualFile>> filesBySandbox = dispatchCommand.filesBySandbox;
            ArrayList<VirtualFile> unversionedFiles = dispatchCommand.getNotInSandboxFiles();
            for (VirtualFile file : unversionedFiles) {
                builder.processUnversionedFile(file);
            }
            int numberOfProcessedFiles = 0;
            int numberOfFilesToProcess = 0;
            if (progress != null) {
                numberOfFilesToProcess = getNumberOfFiles(filesBySandbox);
                progress.setFraction(0);
                progress.setText("Querying status");
            }
            for (Map.Entry<TriclopsSiSandbox, ArrayList<VirtualFile>> entry : filesBySandbox.entrySet()) {
                final TriclopsSiSandbox sandbox = entry.getKey();
                final ArrayList<VirtualFile> sandboxFiles = entry.getValue();
                final MksQueryMemberStatusCommand command = new MksQueryMemberStatusCommand(errors, sandbox, sandboxFiles);
                command.execute();
                if (command.foundError()) {
                    break;
                }
                for (int i = 0, max = command.triclopsSiMembers.getNumMembers(); i < max; i++) {

                    final TriclopsSiMember triclopsSiMember = command.triclopsSiMembers.getMember(i);
                    final VirtualFile virtualFile = sandboxFiles.get(i);
                    processMember(sandbox, triclopsSiMember, virtualFile, builder, changePackagesPerServer, filePathsByVFile.get(virtualFile), changePackageEntriesByMksProject, changePackagesByChangePackageEntry);
                    if (progress != null) {
                        progress.setFraction(((double) numberOfProcessedFiles++) / numberOfFilesToProcess);
                    }
                }
            }
            // todo : status unknown for directories with no known children
*/
        } catch (RuntimeException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            throw e;
        } finally {
            LOGGER.info("end getChanges");
        }
        if (!errors.isEmpty()) {
            mksvcs.showErrors(errors, "ChangeProvider");
        }


    }

	private Map<String, ListServers.MksServerInfo> distributeServersByHostAndPort(final ArrayList<ListServers.MksServerInfo> servers) {
		Map<String, ListServers.MksServerInfo> result = new HashMap<String, ListServers.MksServerInfo>();
		for (ListServers.MksServerInfo server : servers) {
			result.put(server.host + ":" + server.port, server);
		}
		return result;
	}

	private void processDirtySandbox(final ChangelistBuilder builder,final Map<String, MksChangePackage> changePackages,
	                                 final Map<String, MksMemberState> states) {
		for (Map.Entry<String, MksMemberState> entry : states.entrySet()) {
			MksMemberState state = entry.getValue();
			FilePath filePath = VcsUtil.getFilePath(entry.getKey());
			VirtualFile virtualFile = VcsUtil.getVirtualFile(entry.getKey());
			if (state.checkedout) {
				MksChangePackage changePackage = changePackages.get(state.workingChangePackageId);
				Change change = new Change(
				    new MksContentRevision(mksvcs, filePath, state.workingRevision),
				    CurrentContentRevision.create(filePath),
				    FileStatus.MODIFIED);
				if (changePackage == null) {
//					System.out.println("processChange "+virtualFile+", MODIFIED)");
					builder.processChange(change);
				} else {
					ChangeList changeList = mksvcs.getChangeListAdapter().trackMksChangePackage(changePackage);
//					System.out.println("processChange "+virtualFile+", MODIFIED IN CP "+changePackage.getId());
					builder.processChangeInList(change, changeList);
				}
			} else if (state.modifiedWithoutCheckout) {
//				System.out.println("modified without checkout "+virtualFile);
				builder.processModifiedWithoutCheckout(virtualFile);
			} else {
//				if (entry.getKey().contains("log4j.properties")) {
//					System.out.println("NOT MODIFIED :" +virtualFile );
//				}
				// not changed
//				Change change = new Change(
//				    new MksContentRevision(mksvcs, filePath, state.workingRevision),
//				    CurrentContentRevision.create(filePath),
//				    FileStatus.NOT_CHANGED);
//				builder.processChange(change);
			}
		}
	}

	private Map<String, MksMemberState> getSandboxState(final MksSandboxInfo sandbox, final ArrayList<VcsException> errors, final ListServers.MksServerInfo server) {
		ViewSandboxWithoutChangesCommand command1 = new ViewSandboxWithoutChangesCommand(errors,mksvcs, server.user, sandbox.sandboxPath);
		command1.execute();
		Map<String, MksMemberState> states = new HashMap<String, MksMemberState>();
		states.putAll(command1.getMemberStates());
		ViewSandboxChangesCommand command2 = new ViewSandboxChangesCommand(errors,mksvcs, server.user, sandbox.sandboxPath);
		command2.execute();
		states.putAll(command2.getMemberStates());
		return states;
	}

	/**
	 *
	 * @param progress
	 * @param errors
	 * @param servers
	 * @return Map <MksServerInfo, Map<MksChangePackage.id,MksChangePackage>>
	 */
	private Map<ListServers.MksServerInfo, Map<String, MksChangePackage>> getChangePackages(final ProgressIndicator progress, final ArrayList<VcsException> errors, final ArrayList<ListServers.MksServerInfo> servers) {
	    final Map<ListServers.MksServerInfo, Map<String, MksChangePackage>> changePackages = new HashMap<ListServers.MksServerInfo, Map<String, MksChangePackage>>();
	    for (ListServers.MksServerInfo server : servers) {
            final ListChangePackages listCpsAction = new ListChangePackages(errors, mksvcs, server);
            if (progress != null) {
                progress.setIndeterminate(true);
                progress.setText("Querying change packages for " + server + "...");
            }
            listCpsAction.execute();
            if (listCpsAction.foundError()) {
                LOGGER.warn("error querying mks cps");
            }
		    Map<String, MksChangePackage> serverChangePackages = new HashMap<String, MksChangePackage>();
		    for (MksChangePackage changePackage : listCpsAction.changePackages) {
			    serverChangePackages.put(changePackage.getId(), changePackage);
		    }
            changePackages.put(server, serverChangePackages);
        }
        return changePackages;
    }

	private ArrayList<ListServers.MksServerInfo> getMksServers(final ProgressIndicator progress, final ArrayList<VcsException> errors) {
		final ListServers listServersAction = new ListServers(errors, mksvcs);
		if (progress != null) {
		    progress.setIndeterminate(true);
		    progress.setText("Querying mks servers ...");
		}
		listServersAction.execute();
		if (listServersAction.foundError()) {
		    LOGGER.warn("encountered errors querying servers");
		}
		ArrayList<ListServers.MksServerInfo> servers = listServersAction.servers;
		return servers;
	}

	private void processMember(final TriclopsSiSandbox sandbox, final TriclopsSiMember triclopsSiMember, final VirtualFile virtualFile,
	                           final ChangelistBuilder builder, final List<MksChangePackage> changePackages, final FilePath filePath,
	                           final Map<String, List<MksChangePackageEntry>> cpEntriesByMksProject,
	                           final Map<MksChangePackageEntry, MksChangePackage> mksCpsByCPEntry) throws VcsException {
        if (virtualFile.isDirectory()) {
            // todo  status = FileStatus.NOT_CHANGED;
        } else if (mksvcs.getSandboxCache().isSandboxProject(virtualFile)) {
            // todo better handle MKS project files : should they be returned by DispatchBySandbox ?
            builder.processIgnoredFile(virtualFile);
        } else
        if (triclopsSiMember.isStatusKnown() && triclopsSiMember.isStatusNotControlled()) {
            LOGGER.debug("UNKNOWN " + virtualFile);
            builder.processUnversionedFile(virtualFile);
//				status = FileStatus.UNKNOWN;
        } else
        if (triclopsSiMember.isStatusKnown() && triclopsSiMember.isStatusControlled()) {
            if (triclopsSiMember.isStatusNoWorkingFile()) {
                LOGGER.debug("LOCALLY DELETED " + virtualFile);
                builder.processLocallyDeletedFile(filePath);
//					status = FileStatus.DELETED_FROM_FS;

            } else
            if (triclopsSiMember.isStatusDifferent() && !triclopsSiMember.isStatusLocked()) {
                LOGGER.debug("MODIFIED WITHOUT CHECKOUT" + virtualFile);
                builder.processModifiedWithoutCheckout(virtualFile);
            } else
            if (triclopsSiMember.isStatusDifferent() && triclopsSiMember.isStatusLocked()) {
                LOGGER.debug("MODIFIED " + virtualFile);
                MksChangePackage changePackage = findChangePackage(filePath, getRevision(triclopsSiMember).asString(), sandbox, changePackages,
                    cpEntriesByMksProject, mksCpsByCPEntry);
                Change change = createChange(sandbox, triclopsSiMember, virtualFile, filePath);
                if (changePackage != null) {
                    ChangeList changeList = mksvcs.getChangeListAdapter().trackMksChangePackage(changePackage);
                    builder.processChangeInList(change, changeList);
                } else {
                    builder.processChange(change);
                }
//					status = FileStatus.MODIFIED;
            } else if (!triclopsSiMember.isStatusDifferent()) {
                // todo anything to do if unchanged ?
                LOGGER.debug("UNCHANGED " + virtualFile);
//					status = FileStatus.NOT_CHANGED;
            }
        }
    }

	private Change createChange(final TriclopsSiSandbox sandbox, final TriclopsSiMember triclopsSiMember, final VirtualFile virtualFile, final FilePath filePath) throws VcsException {
		return new Change(
		    new MksContentRevision(mksvcs, filePath, getRevision(triclopsSiMember)),
		    CurrentContentRevision.create(filePath),
		    getStatus(sandbox, triclopsSiMember, virtualFile));
	}


	//  todo optimize this, it is not efficient
    private MksChangePackage findChangePackage(FilePath filePath, final String mksRevision, TriclopsSiSandbox sandbox, List<MksChangePackage> changeLists, Map<String, List<MksChangePackageEntry>> changePackageEntriesByMksProject, Map<MksChangePackageEntry, MksChangePackage> changePackagesByChangePackageEntry) throws VcsException {
        String sandboxProject = sandbox.getSandboxProject();

	    final List<MksChangePackageEntry> candidateEntries = changePackageEntriesByMksProject.get(sandboxProject);
        if (candidateEntries == null) {
            // no changePackageEntries have been found under this project , return null
            return null;
        }
        final File filePathFile = filePath.getIOFile();
        final File projectFileDir = new File(sandbox.getPath()).getParentFile();
        final ArrayList<MksChangePackage> candidates = new ArrayList<MksChangePackage>();
        for (MksChangePackageEntry candidateEntry : candidateEntries) {
            final File memberFile = new File(projectFileDir, candidateEntry.getMember());
            if (memberFile.equals(filePathFile)) {
//						System.out.println("found a candidate, checking states and revisions (member.revision=" + mksRevision + ",cpRevision=" + changePackageEntry.getRevision() + ")");
                if (candidateEntry.isLocked() && candidateEntry.getRevision().equals(mksRevision)) {
                    candidates.add(changePackagesByChangePackageEntry.get(candidateEntry));
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            throw new VcsException("MORE THAN ONE CHANGELIST FOR A CHANGE " + filePath + ", " + candidates);
        }
        final MksChangePackage changeList = candidates.get(0);
//		System.out.println("found changelist : " + changeList + " for " + filePath);
        return changeList;
    }

    private FileStatus getStatus(TriclopsSiSandbox sandbox, TriclopsSiMember member, VirtualFile virtualFile) throws VcsException {
        FileStatus status = FileStatus.UNKNOWN;
        if (virtualFile.isDirectory()) {
            status = FileStatus.NOT_CHANGED;
        } else if (MKSHelper.isIgnoredFile(sandbox, virtualFile)) {
            status = FileStatus.IGNORED;
        } else if (member.isStatusKnown() && member.isStatusNotControlled()) {
            status = FileStatus.UNKNOWN;
        } else if (member.isStatusKnown() && member.isStatusControlled()) {
            if (member.isStatusNoWorkingFile()) {
                status = FileStatus.DELETED_FROM_FS;
            } else if (member.isStatusDifferent() && !member.isStatusLocked()) {
                // todo this is a FileSystem modificaction, with no prior checkout, which filestatus should we use ?
                status = FileStatus.MODIFIED;
            } else if (member.isStatusDifferent()) {
                status = FileStatus.MODIFIED;
            } else if (!member.isStatusDifferent()) {
                status = FileStatus.NOT_CHANGED;
            }
        }
        if (MksVcs.DEBUG) {
            LOGGER.debug("status " + member.getPath() + "==" + status);
        }
        return status;
    }

    private MksRevisionNumber getRevision(TriclopsSiMember triclopsSiMember) throws VcsException {

        String mksRevision = triclopsSiMember.getRevision();
        String[] localAndRemoteRevisions = mksRevision.split("&\\*&");
        return new MksRevisionNumber(localAndRemoteRevisions[0]);
    }

    private int getNumberOfFiles(Map<TriclopsSiSandbox, ArrayList<VirtualFile>> filesBySandbox) {
        int nb = 0;
        for (ArrayList<VirtualFile> virtualFiles : filesBySandbox.values()) {
            nb += virtualFiles.size();
        }
        return nb;
    }

    private void debugMember(TriclopsSiMember newMember) {
        if (MksVcs.DEBUG) {
            System.out.println("isStatusCanCheckIn : " + newMember.isStatusCanCheckIn());
            System.out.println("isStatusCanCheckOut : " + newMember.isStatusCanCheckOut());
            System.out.println("isStatusControlled : " + newMember.isStatusControlled());
            System.out.println("isStatusDifferent : " + newMember.isStatusDifferent());
            System.out.println("isStatusKnown : " + newMember.isStatusKnown());
            System.out.println("isStatusLocked : " + newMember.isStatusLocked());
            System.out.println("isStatusLockedByOther : " + newMember.isStatusLockedByOther());
            System.out.println("isStatusLockedByUser : " + newMember.isStatusLockedByUser());
            System.out.println("isStatusNewRevisionAvail : " + newMember.isStatusNewRevisionAvail());
            System.out.println("isStatusNotAuthorized : " + newMember.isStatusNotAuthorized());
            System.out.println("isStatusNotControlled : " + newMember.isStatusNotControlled());
            System.out.println("isStatusNoWorkingFile : " + newMember.isStatusNoWorkingFile());
            System.out.println("isStatusOutOfDate : " + newMember.isStatusOutOfDate());
            System.out.println("getArchive : " + newMember.getArchive());
            System.out.println("getArgFlags : " + newMember.getArgFlags());
            System.out.println("getLocker : " + newMember.getLocker());
            System.out.println("getPath : " + newMember.getPath());
            System.out.println("getRevision : " + newMember.getRevision());
            System.out.println("getStatus : " + newMember.getStatus());
            System.out.println("getStringArg : " + newMember.getStringArg());
        }
    }

    public boolean isModifiedDocumentTrackingRequired() {
        return false;
    }

    @NotNull
    public MksChangeListAdapter getChangeListAdapter() {
        return mksvcs.getChangeListAdapter();
    }

    public void projectClosed() {
    }

    public void projectOpened() {
    }

    public void disposeComponent() {
    }

    @NonNls
    @NotNull
    public String getComponentName() {
        return "MKS Change List Support";
    }

    public void initComponent() {
    }

    public void decorateChangeList(LocalChangeList changeList, ColoredTreeCellRenderer cellRenderer, boolean selected, boolean expanded, boolean hasFocus) {
        MksChangeListAdapter changeListAdapter = getChangeListAdapter();
        if (!changeListAdapter.isChangeListMksControlled(changeList.getName())) {
            return;
        }
        MksChangePackage aPackage = changeListAdapter.getMksChangePackage(changeList.getName());
        if (aPackage != null) {

            cellRenderer.append(" - MKS #" + aPackage.getId(), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
    }
}

