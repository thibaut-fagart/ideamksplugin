package org.intellij.vcs.mks;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Processor;
import com.intellij.vcsUtil.VcsUtil;
import org.intellij.vcs.mks.model.MksChangePackage;
import org.intellij.vcs.mks.model.MksMemberState;
import org.intellij.vcs.mks.model.MksServerInfo;
import org.intellij.vcs.mks.realtime.MksSandboxInfo;
import org.intellij.vcs.mks.realtime.SandboxCache;
import org.intellij.vcs.mks.sicommands.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * @author Thibaut Fagart
 * @see com.intellij.openapi.vcs.changes.VcsDirtyScopeManager allows to notify
 *      idea if files should be marked dirty
 */
class MKSChangeProvider extends AbstractProjectComponent implements ChangeProvider, ProjectComponent, ChangeListDecorator {
	private final Logger logger = Logger.getInstance(getClass().getName());
	private final Logger BUILDER_PROXY_LOGGER = Logger.getInstance(getClass().getName() + ".ChangelistBuilder");
	@NotNull
	private final MksVcs mksvcs;
	private static final String UNVERSIONED_LIST = "Unversioned";
	private static final String MODIFIED_WITHOUT_CHECKOUT_LIST = "Modified without checkout";

	public MKSChangeProvider(@NotNull MksVcs mksvcs) {
		super(mksvcs.getProject());
		this.mksvcs = mksvcs;
	}


	public void getChanges(final VcsDirtyScope dirtyScope, ChangelistBuilder builder, final ProgressIndicator progress) throws VcsException {
		ArrayList<VcsException> errors = new ArrayList<VcsException>();
		logger.debug("start getChanges");
		final JLabel statusLabel = new JLabel();
		try {
			WindowManager.getInstance().getStatusBar(myProject).addCustomIndicationComponent(statusLabel);
			setStatusInfo(statusLabel, "collecting servers");
			ArrayList<MksServerInfo> servers = getMksServers(progress, errors);
			setStatusInfo(statusLabel, "collecting change packages");
			final Map<MksServerInfo, Map<String, MksChangePackage>> changePackagesPerServer = getChangePackages(progress, errors, servers);
			// collect affected sandboxes
//			if (MksVcs.DEBUG) {
//				builder = createBuilderLoggingProxy(myBuilder);
//			}
			setStatusInfo(statusLabel, "collecting relevant sandboxes");
			final Map<String, MksServerInfo> serversByHostAndPort = distributeServersByHostAndPort(servers);
			final SandboxCache sandboxCache = mksvcs.getSandboxCache();
			Set<MksSandboxInfo> sandboxesToRefresh = new HashSet<MksSandboxInfo>();

			for (VirtualFile dir : dirtyScope.getAffectedContentRoots()) {
				Set<MksSandboxInfo> sandboxes = sandboxCache.getSandboxesIntersecting(dir);
				sandboxesToRefresh.addAll(sandboxes);
			}
			int sandboxCountToRefresh = sandboxesToRefresh.size();
			int refreshedSandbox = 0;
			for (MksSandboxInfo sandbox : sandboxesToRefresh) {
				MksServerInfo sandboxServer = serversByHostAndPort.get(sandbox.hostAndPort);
				final String message = "requesting mks sandbox "
						+ sandbox.sandboxPath + " (" + (++refreshedSandbox) + "/" + sandboxCountToRefresh + ") ";
				setStatusInfo(statusLabel, message);
				processDirtySandbox(builder, changePackagesPerServer.get(sandboxServer), getSandboxState(sandbox, errors, sandboxServer));
			}
			final ChangelistBuilder finalBuilder = builder;
			// iterate over the local dirty scope to flag unversioned files
			setStatusInfo(statusLabel, "processing unversioned files");
			dirtyScope.iterate(new Processor<FilePath>() {
				public boolean process(FilePath filePath) {
					if (filePath.isDirectory()) {
						return true;
					} else if (filePath.getVirtualFile() == null) {
						logger.warn("no VirtualFile for " + filePath.getPath() + ", ignoring");
						return true;
					} else if (sandboxCache.isSandboxProject(filePath.getVirtualFile())) {
						finalBuilder.processIgnoredFile(filePath.getVirtualFile());
						//                        System.err.println("ignoring project.pj file");
						return true;
					} else {
						return true;
					}
				}
			});
		} finally {
			WindowManager.getInstance().getStatusBar(myProject).removeCustomIndicationComponent(statusLabel);
			logger.debug("end getChanges");
		}
		if (!errors.isEmpty()) {
			mksvcs.showErrors(errors, "ChangeProvider");
		}


	}

	private void setStatusInfo(JLabel statusLabel, String message) {
		statusLabel.setText(message);
	}

	private ChangelistBuilder createBuilderLoggingProxy(final ChangelistBuilder myBuilder) {
		return (ChangelistBuilder) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ChangelistBuilder.class}, new InvocationHandler() {
			public Object invoke(final Object o, final Method method, final Object[] args) throws Throwable {
				StringBuffer buf = new StringBuffer("(");
				for (Object arg : args) {
					buf.append(arg).append(",");
				}
				buf.setLength(buf.length() - 1);
				buf.append(")");
				BUILDER_PROXY_LOGGER.debug(method.getName() + buf);
				return method.invoke(myBuilder, args);
			}
		});
	}

	private Map<String, MksServerInfo> distributeServersByHostAndPort(final ArrayList<MksServerInfo> servers) {
		Map<String, MksServerInfo> result = new HashMap<String, MksServerInfo>();
		for (MksServerInfo server : servers) {
			result.put(server.host + ":" + server.port, server);
		}
		return result;
	}

	private void processDirtySandbox(final ChangelistBuilder builder, final Map<String, MksChangePackage> changePackages,
									 final Map<String, MksMemberState> states) {
		final ChangeListManager listManager = ChangeListManager.getInstance(myProject);
		for (Map.Entry<String, MksMemberState> entry : states.entrySet()) {
			MksMemberState state = entry.getValue();
			FilePath filePath = VcsUtil.getFilePath(entry.getKey());
			VirtualFile virtualFile = VcsUtil.getVirtualFile(entry.getKey());
			if (null != virtualFile && mksvcs.getSandboxCache().isSandboxProject(virtualFile)) {
				continue;
			}
			switch (state.status) {
				case ADDED: {
					MksChangePackage changePackage = getChangePackage(changePackages, state);
					Change change = new Change(
							null,
							CurrentContentRevision.create(filePath),
							FileStatus.ADDED);
					if (changePackage == null) {
						builder.processChange(change);
					} else {
						ChangeList changeList = mksvcs.getChangeListAdapter().trackMksChangePackage(changePackage);
						builder.processChangeInList(change, changeList);
					}
					break;
				}
				case CHECKED_OUT: {
					MksChangePackage changePackage = getChangePackage(changePackages, state);
					Change change = new Change(
							new MksContentRevision(mksvcs, filePath, state.memberRevision),
							CurrentContentRevision.create(filePath),
							FileStatus.MODIFIED);
					if (changePackage == null) {
						builder.processChange(change);
					} else {
						ChangeList changeList = mksvcs.getChangeListAdapter().trackMksChangePackage(changePackage);
						builder.processChangeInList(change, changeList);
					}
					break;
				}
				case MODIFIED_WITHOUT_CHECKOUT: {
					ChangeList userChangeList = getUserChangelist(filePath, listManager);
					builder.processChangeInList(new Change(new MksContentRevision(mksvcs, filePath, state.memberRevision),
							CurrentContentRevision.create(filePath),
							FileStatus.HIJACKED), (userChangeList == null) ? MODIFIED_WITHOUT_CHECKOUT_LIST : userChangeList.getName());

//					builder.processModifiedWithoutCheckout(virtualFile);
					break;
				}
				case MISSISNG: {
					// todo some of those changes belong to the Incoming tab
					ChangeList userChangeList = getUserChangelist(filePath, listManager);
					builder.processChangeInList(new Change(
							new MksContentRevision(mksvcs, filePath, state.memberRevision),
							new MksContentRevision(mksvcs, filePath, state.workingRevision),
							FileStatus.DELETED_FROM_FS), (userChangeList == null) ? UNVERSIONED_LIST : userChangeList.getName());
//					builder.processLocallyDeletedFile(filePath);
					break;
				}
				case SYNC:
					// todo some of those changes belong to the Incoming tab
					builder.processChange(new Change(
							new MksContentRevision(mksvcs, filePath, state.workingRevision),
							new MksContentRevision(mksvcs, filePath, state.memberRevision),
							FileStatus.OBSOLETE));
					break;
				case DROPPED: {
					MksChangePackage changePackage = getChangePackage(changePackages, state);
					Change change = new Change(
							new MksContentRevision(mksvcs, filePath, state.memberRevision),
							CurrentContentRevision.create(filePath),
							FileStatus.DELETED);
					if (changePackage == null) {
						builder.processChange(change);
					} else {
						ChangeList changeList = mksvcs.getChangeListAdapter().trackMksChangePackage(changePackage);
						builder.processChangeInList(change, changeList);
					}
					break;
				}
				case NOT_CHANGED:
					break;
				case UNVERSIONED: {
					ChangeList userChangeList = getUserChangelist(filePath, listManager);

					Change change = new Change(
							null,
							CurrentContentRevision.create(filePath),
							FileStatus.UNKNOWN);
					builder.processChangeInList(change, (userChangeList == null) ? UNVERSIONED_LIST : userChangeList.getName());
					break;
				}
				case UNKNOWN: {
					builder.processChange(new Change(
							new MksContentRevision(mksvcs, filePath, state.memberRevision),
							new MksContentRevision(mksvcs, filePath, state.workingRevision),
							FileStatus.UNKNOWN));
				}
				default: {
					logger.info("unhandled MKS status " + state.status);
				}
			}
		}
	}

	private ChangeList getUserChangelist(FilePath filePath, ChangeListManager changeListManager) {
		final Change change = changeListManager.getChange(filePath);
		return (change == null) ? null : changeListManager.getChangeList(change);
	}

	private MksChangePackage getChangePackage(final Map<String, MksChangePackage> changePackages, final MksMemberState state) {
		return state.workingChangePackageId == null ? null : changePackages.get(state.workingChangePackageId);
	}

	private Map<String, MksMemberState> getSandboxState(@NotNull final MksSandboxInfo sandbox, final ArrayList<VcsException> errors, final MksServerInfo server) {
		Map<String, MksMemberState> states = new HashMap<String, MksMemberState>();

		ViewSandboxWithoutChangesCommand fullSandboxCommand = new ViewSandboxWithoutChangesCommand(errors, mksvcs, sandbox.sandboxPath);
		fullSandboxCommand.execute();
		states.putAll(fullSandboxCommand.getMemberStates());

		ViewSandboxLocalChangesOrLockedCommand localChangesCommand = new ViewSandboxLocalChangesOrLockedCommand(errors, mksvcs, server.user, sandbox.sandboxPath);
		localChangesCommand.execute();
		states.putAll(localChangesCommand.getMemberStates());

		ViewNonMembersCommand nonMembersCommand = new ViewNonMembersCommand(errors, mksvcs, sandbox);
		nonMembersCommand.execute();
		for (Map.Entry<String, MksMemberState> entry : nonMembersCommand.getMemberStates().entrySet()) {
			VirtualFile virtualFile = VcsUtil.getVirtualFile(entry.getKey());
			if (null == virtualFile) {
				logger.warn("no virtual file for filepath " + entry.getKey() + ", trying refreshing");
				final HashSet<FilePath> set = new HashSet<FilePath>();
				set.add(VcsUtil.getFilePath(entry.getKey()));
				VcsUtil.refreshFiles(myProject, set);
				virtualFile = VcsUtil.getVirtualFile(entry.getKey());
				if (null == virtualFile) {
					logger.warn("refreshing did not help");
					continue;
				}
			}
			if (myProject.getProjectScope().contains(virtualFile)) {
				states.put(entry.getKey(), entry.getValue());
			}
//				System.err.println("adding "+entry.getKey());
//			} else {
//				System.err.println("ignoring "+entry.getKey());


		}
//		states.putAll(nonMembersCommand.getMemberStates());
// todo the below belong to incoming changes
//		ViewSandboxOutOfSyncCommand outOfSyncCommand = new ViewSandboxOutOfSyncCommand(errors, mksvcs, sandbox.sandboxPath);
//		outOfSyncCommand.execute();
//		states.putAll(outOfSyncCommand.getMemberStates());

//		ViewSandboxRemoteChangesCommand missingCommand = new ViewSandboxRemoteChangesCommand(errors, mksvcs, sandbox.sandboxPath);
//		missingCommand.execute();
//		states.putAll(missingCommand.getMemberStates());

		return states;
	}

	/**
	 * @param progress
	 * @param errors
	 * @param servers
	 * @return Map <MksServerInfo, Map<MksChangePackage.id,MksChangePackage>>
	 */
	private Map<MksServerInfo, Map<String, MksChangePackage>> getChangePackages(final ProgressIndicator progress, final ArrayList<VcsException> errors, final ArrayList<MksServerInfo> servers) {
		final Map<MksServerInfo, Map<String, MksChangePackage>> changePackages = new HashMap<MksServerInfo, Map<String, MksChangePackage>>();
		for (MksServerInfo server : servers) {
			final ListChangePackages listCpsAction = new ListChangePackages(errors, mksvcs, server);
			if (progress != null) {
				progress.setIndeterminate(true);
				progress.setText("Querying change packages for " + server + "...");
			}
			listCpsAction.execute();
			if (listCpsAction.foundError()) {
				logger.warn("error querying mks cps");
			}
			Map<String, MksChangePackage> serverChangePackages = new HashMap<String, MksChangePackage>();
			for (MksChangePackage changePackage : listCpsAction.changePackages) {
				serverChangePackages.put(changePackage.getId(), changePackage);
			}
			changePackages.put(server, serverChangePackages);
		}
		return changePackages;
	}

	private ArrayList<MksServerInfo> getMksServers(final ProgressIndicator progress, final ArrayList<VcsException> errors) {
		final ListServers listServersAction = new ListServers(errors, mksvcs);
		if (progress != null) {
			progress.setIndeterminate(true);
			progress.setText("Querying mks servers ...");
		}
		listServersAction.execute();
		if (listServersAction.foundError()) {
			logger.warn("encountered errors querying servers");
		}
		return listServersAction.servers;
	}

	public boolean isModifiedDocumentTrackingRequired() {
		return false;
	}

	@NotNull
	public MksChangeListAdapter getChangeListAdapter() {
		return mksvcs.getChangeListAdapter();
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

