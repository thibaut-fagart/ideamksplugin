package org.intellij.vcs.mks;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
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
import org.intellij.vcs.mks.sicommands.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
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
			Set<MksSandboxInfo> sandboxesToRefresh = collectSandboxesToRefresh(dirtyScope, statusLabel);
			setStatusInfo(statusLabel, "collecting servers");
			ArrayList<MksServerInfo> servers = getMksServers(progress, errors);
			checkNeededServersAreOnlineAndReconnectIfNeeded(sandboxesToRefresh, servers);
			setStatusInfo(statusLabel, "collecting change packages");
			// collect affected sandboxes
//			if (MksVcs.DEBUG) {
//				builder = createBuilderLoggingProxy(builder);
//			}
			final Map<String, MksServerInfo> serversByHostAndPort = distributeServersByHostAndPort(servers);

			int sandboxCountToRefresh = sandboxesToRefresh.size();
			int refreshedSandbox = 0;
			final Map<MksServerInfo, Map<String, MksChangePackage>> changePackagesPerServer = getChangePackages(progress, errors, servers);
			for (MksSandboxInfo sandbox : sandboxesToRefresh) {
				@Nullable MksServerInfo sandboxServer = serversByHostAndPort.get(sandbox.hostAndPort);
				if (sandboxServer == null) {
					logger.warn("sandbox [" + sandbox.sandboxPath + "] bound to unknown or not connected server[" + sandbox.hostAndPort + "], skipping");
					continue;
				}
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
					} else if (mksvcs.getSandboxCache().isSandboxProject(filePath.getVirtualFile())) {
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

	private void checkNeededServersAreOnlineAndReconnectIfNeeded(Set<MksSandboxInfo> sandboxesToRefresh, ArrayList<MksServerInfo> servers) {
		Set<String> connectedServers = new HashSet<String>();
		for (MksServerInfo server : servers) {
			connectedServers.add(server.host + ":" + server.port);
		}
		Set<String> serversNeedingReconnect = new HashSet<String>();
		for (MksSandboxInfo sandboxInfo : sandboxesToRefresh) {
			if (!connectedServers.contains(sandboxInfo.hostAndPort)) {
				serversNeedingReconnect.add(sandboxInfo.hostAndPort);
			}
		}
		for (final String hostAndPort : serversNeedingReconnect) {
			// request user and password

			int colonIndex = hostAndPort.indexOf(':');
			String host = hostAndPort.substring(0, colonIndex);
			String port = hostAndPort.substring(colonIndex + 1);
			class UserAndPassword {
				String user;
				String password;
			}
			final UserAndPassword userAndPassword = new UserAndPassword();
			try {
				SwingUtilities.invokeAndWait(new Runnable() {
					public void run() {
						userAndPassword.user = Messages.showInputDialog(mksvcs.getProject(), "user", "MKS : reconnect to " + hostAndPort, null);
						userAndPassword.password = Messages.showInputDialog(mksvcs.getProject(), "password", "MKS : reconnect to " + hostAndPort, null);

					}
				});
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (InvocationTargetException e) {
				logger.error(e);
				continue;
			}
			if (userAndPassword.user == null || userAndPassword.password == null) {
				continue;
			}
			SiConnectCommand command = new SiConnectCommand(mksvcs, host, port, userAndPassword.user, userAndPassword.password);
			command.execute();
			if (!command.foundError() && (command.getServer() != null)) {
				servers.add(command.getServer());
			} else {
				logger.warn("unable to connect to " + hostAndPort);
			}
		}
	}

	private Set<MksSandboxInfo> collectSandboxesToRefresh(VcsDirtyScope dirtyScope, JLabel statusLabel) {
		setStatusInfo(statusLabel, "collecting relevant sandboxes");
		Set<MksSandboxInfo> sandboxesToRefresh = new HashSet<MksSandboxInfo>();
		for (FilePath dir : dirtyScope.getRecursivelyDirtyDirectories()) {
			mksvcs.debug("VcsDirtyScope : recursivelyDirtyDir " + dir);
			VirtualFile vFile = dir.getVirtualFile();
			if (vFile != null) {
				Set<MksSandboxInfo> sandboxes = mksvcs.getSandboxCache().getSandboxesIntersecting(vFile);
				StringBuffer log = new StringBuffer("=> dirtySandbox : ");
				for (MksSandboxInfo sandbox : sandboxes) {
					log.append(sandbox.sandboxPath).append(", ");
				}
				mksvcs.debug(log.substring(0, log.length() - 2));
				sandboxesToRefresh.addAll(sandboxes);
			}
		}
		for (FilePath path : dirtyScope.getDirtyFiles()) {
			mksvcs.debug("VcsDirtyScope : dirtyFile " + path);
			VirtualFile vFile = path.getVirtualFile();
			if (vFile != null) {
				final MksSandboxInfo mksSandboxInfo = mksvcs.getSandboxCache().getSandboxInfo(vFile);
				if (mksSandboxInfo != null) {
					mksvcs.debug("=> dirtySandbox : " + mksSandboxInfo.sandboxPath);
					sandboxesToRefresh.add(mksSandboxInfo);
				}
			}
		}
		return sandboxesToRefresh;
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

	private Map<String, MksMemberState> getSandboxState(@NotNull final MksSandboxInfo sandbox, final ArrayList<VcsException> errors, @NotNull final MksServerInfo server) {
		Map<String, MksMemberState> states = new HashMap<String, MksMemberState>();

		ViewSandboxWithoutChangesCommand fullSandboxCommand = new ViewSandboxWithoutChangesCommand(errors, mksvcs, sandbox.sandboxPath);
		fullSandboxCommand.execute();
		addNonExcludedStates(states, fullSandboxCommand.getMemberStates());

		ViewSandboxLocalChangesOrLockedCommand localChangesCommand = new ViewSandboxLocalChangesOrLockedCommand(errors, mksvcs, server.user, sandbox.sandboxPath);
		localChangesCommand.execute();
		addNonExcludedStates(states, localChangesCommand.getMemberStates());

		ViewNonMembersCommand nonMembersCommand = new ViewNonMembersCommand(errors, mksvcs, sandbox);
		nonMembersCommand.execute();
/*
		for (Map.Entry<String, MksMemberState> entry : nonMembersCommand.getMemberStates().entrySet()) {
			VirtualFile virtualFile = VcsUtil.getVirtualFile(entry.getKey());
			if (null == virtualFile) {
//				logger.warn("no virtual file for filepath " + entry.getKey() + ", trying refreshing");
				final HashSet<FilePath> set = new HashSet<FilePath>();
				set.add(VcsUtil.getFilePath(entry.getKey()));
				VcsUtil.refreshFiles(myProject, set);
				virtualFile = VcsUtil.getVirtualFile(entry.getKey());
				if (null == virtualFile) {
//					logger.warn("refreshing did not help");
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
*/
		addNonExcludedStates(states, nonMembersCommand.getMemberStates());
// todo the below belong to incoming changes
//		ViewSandboxOutOfSyncCommand outOfSyncCommand = new ViewSandboxOutOfSyncCommand(errors, mksvcs, sandbox.sandboxPath);
//		outOfSyncCommand.execute();
//		states.putAll(outOfSyncCommand.getMemberStates());

//		ViewSandboxRemoteChangesCommand missingCommand = new ViewSandboxRemoteChangesCommand(errors, mksvcs, sandbox.sandboxPath);
//		missingCommand.execute();
//		states.putAll(missingCommand.getMemberStates());

		return states;
	}

	private void addNonExcludedStates(Map<String, MksMemberState> collectingMap, Map<String, MksMemberState> source) {
		for (Map.Entry<String, MksMemberState> entry : source.entrySet()) {
			final FilePath path = VcsUtil.getFilePath(entry.getKey());
			if (path.getVirtualFile() != null && mksvcs.getProject().getProjectScope().contains(path.getVirtualFile())) {
				collectingMap.put(entry.getKey(), entry.getValue());
			} else if (logger.isDebugEnabled()) {
				logger.debug("skipping " + path.getPath());
			}

		}
	}

	/**
	 * @param progress
	 * @param errors
	 * @param servers
	 * @return Map <MksServerInfo, Map<MksChangePackage.id,MksChangePackage>>
	 */
	private Map<MksServerInfo, Map<String, MksChangePackage>> getChangePackages(final ProgressIndicator progress, final ArrayList<VcsException> errors, final ArrayList<MksServerInfo> servers) {
		final Map<MksServerInfo, Map<String, MksChangePackage>> changePackages = new HashMap<MksServerInfo, Map<String, MksChangePackage>>();
		final MksConfiguration config = ApplicationManager.getApplication().getComponent(MksConfiguration.class);
		for (MksServerInfo server : servers) {
			if (!config.isServerSiServer(server)) {
				mksvcs.debug("ignoring " + server.host + ":" + server.port + " when querying changepackages");
				continue;
			}
			final ListChangePackages listCpsAction = new ListChangePackages(errors, mksvcs, server);
			if (progress != null) {
				progress.setIndeterminate(true);
				progress.setText("Querying change packages for " + server + "...");
			}
			listCpsAction.execute();
			if (listCpsAction.foundError()) {
				logger.warn("error querying mks cps");
			}
			config.serverIsSiServer(listCpsAction.serverInfo, listCpsAction.serverInfo.isSIServer);

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

