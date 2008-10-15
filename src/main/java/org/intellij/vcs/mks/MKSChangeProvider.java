package org.intellij.vcs.mks;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VfsUtil;
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
class MKSChangeProvider extends AbstractProjectComponent
		implements ChangeProvider, ChangeListDecorator, ProjectComponent {
	private final Logger logger = Logger.getInstance(getClass().getName());
	private final Logger BUILDER_PROXY_LOGGER = Logger.getInstance(getClass().getName() + ".ChangelistBuilder");

	public MKSChangeProvider(@NotNull Project project) {
		super(project);
	}

	private
	@NotNull
	Project getProject() {
		return getMksvcs().getProject();
	}


	public void getChanges(final VcsDirtyScope dirtyScope, ChangelistBuilder builder,
						   final ProgressIndicator progress) throws VcsException {
		ArrayList<VcsException> errors = new ArrayList<VcsException>();
		logger.debug("start getChanges");
		final JLabel statusLabel = new JLabel();
		final MksChangeListAdapter adapter = getChangeListAdapter();

		try {
			adapter.setUpdating(true);
			WindowManager.getInstance().getStatusBar(getProject()).addCustomIndicationComponent(statusLabel);
			final SandboxesToRefresh newSandboxesToRefresh = processRecursively(dirtyScope);
			Set<MksSandboxInfo> sandboxesToRefresh = newSandboxesToRefresh.getSandboxes();
			setStatusInfo(statusLabel, MksBundle.message("collecting.servers"));
			ArrayList<MksServerInfo> servers = getMksServers(progress, errors);
			checkNeededServersAreOnlineAndReconnectIfNeeded(sandboxesToRefresh, servers);
			setStatusInfo(statusLabel, MksBundle.message("collecting.change.packages"));
			// collect affected sandboxes
//			if (MksVcs.DEBUG) {
//				builder = createBuilderLoggingProxy(builder);
//			}
			final Map<String, MksServerInfo> serversByHostAndPort = distributeServersByHostAndPort(servers);

			int sandboxCountToRefresh = sandboxesToRefresh.size();
			int refreshedSandbox = 0;
			final Map<MksServerInfo, Map<String, MksChangePackage>> changePackagesPerServer =
					getChangePackages(progress, errors, servers);
			updateChangeListNames(changePackagesPerServer);
			for (MksSandboxInfo sandbox : sandboxesToRefresh) {
				@Nullable MksServerInfo sandboxServer = serversByHostAndPort.get(sandbox.hostAndPort);
				if (sandboxServer == null) {
					logger.warn("sandbox [" + sandbox.sandboxPath + "] bound to unknown or not connected server[" +
							sandbox.hostAndPort + "], skipping");
					continue;
				}
				final String message = MksBundle.message("requesting.mks.sandbox.name.index.total", sandbox.sandboxPath,
						++refreshedSandbox, sandboxCountToRefresh);
				setStatusInfo(statusLabel, message);
				Map<String, MksChangePackage> changePackageMap = changePackagesPerServer.get(sandboxServer);
				if (changePackageMap == null) {
					changePackageMap = Collections.emptyMap();
				}
				newSandboxesToRefresh.getRecursivelyIncludedRoots(sandbox);

				final Map<String, MksMemberState> sandboxState = getSandboxState(sandbox, errors, sandboxServer,
						newSandboxesToRefresh);
				processDirtySandbox(builder, changePackageMap, sandboxState);
			}
			final ChangelistBuilder finalBuilder = builder;
			// iterate over the local dirty scope to flag unversioned files
			setStatusInfo(statusLabel, MksBundle.message("processing.unversioned.files"));
			dirtyScope.iterate(new Processor<FilePath>() {
				public boolean process(FilePath filePath) {
					if (filePath.isDirectory()) {
						return true;
					} else if (filePath.getVirtualFile() == null) {
						logger.warn("no VirtualFile for " + filePath.getPath()
								+ ", ignoring");
						return true;
					} else if (getMksvcs().getSandboxCache().isSandboxProject(filePath.getVirtualFile())) {
						finalBuilder.processIgnoredFile(filePath.getVirtualFile());
						//                        System.err.println("ignoring project.pj file");
						return true;
					} else {
						return true;
					}
				}
			});
		} finally {
			adapter.setUpdating(false);
			WindowManager.getInstance().getStatusBar(getProject()).removeCustomIndicationComponent(statusLabel);
			logger.debug("end getChanges");
		}
		if (!errors.isEmpty()) {
			getMksvcs().showErrors(errors, "ChangeProvider");
		}


	}

	static final class SandboxesToRefresh {
		final Map<MksSandboxInfo, ArrayList<VirtualFile>> excludedRootsPerSandbox =
				new HashMap<MksSandboxInfo, ArrayList<VirtualFile>>();
		private Map<MksSandboxInfo, HashSet<VirtualFile>> recursivelyIncludedRootsPerSandbox =
				new HashMap<MksSandboxInfo, HashSet<VirtualFile>>();
		private Map<MksSandboxInfo, HashSet<VirtualFile>> nonRecursivelyIncludedRootsPerSandbox =
				new HashMap<MksSandboxInfo, HashSet<VirtualFile>>();
		private boolean computed = false;

		public void refresh(MksSandboxInfo sandbox, VirtualFile[] excludedRoots) {
			assert !computed : "too late !";
			ArrayList<VirtualFile> excluded = excludedRootsPerSandbox.get(sandbox);
			if (excluded == null) {
				excluded = new ArrayList<VirtualFile>();
				excludedRootsPerSandbox.put(sandbox, excluded);
			}
			for (VirtualFile excludedRoot : excludedRoots) {
				if (excludedRoot.isDirectory() && sandbox.contains(excludedRoot)) {
					excluded.add(excludedRoot);
				}
			}
		}

		public String toString() {
			StringBuffer buf = new StringBuffer();
			buf.append("SandboxesToRefresh[");
			for (Map.Entry<MksSandboxInfo, ArrayList<VirtualFile>> entry : excludedRootsPerSandbox
					.entrySet()) {
				buf.append(entry.getKey());
				final ArrayList<VirtualFile> excludedRoots = entry.getValue();
				if (!excludedRoots.isEmpty()) {
					buf.append("{");
					for (VirtualFile root : excludedRoots) {
						buf.append(root).append(",");
					}
					buf.append("}");
				}
				buf.append("\n");
			}
			buf.append("]");
			return buf.toString();
		}

		public Set<MksSandboxInfo> getSandboxes() {
			return excludedRootsPerSandbox.keySet();
		}

		public ArrayList<VirtualFile> getExcludedRoots(MksSandboxInfo sandbox) {
			return excludedRootsPerSandbox.get(sandbox);
		}

		/**
		 * returns a list a directories where each one needs to be refreshed inclusively
		 *
		 * @param sandbox
		 * @return
		 */
		public HashSet<VirtualFile> getRecursivelyIncludedRoots(MksSandboxInfo sandbox) {
			computeInclusions();
			final HashSet<VirtualFile> files = recursivelyIncludedRootsPerSandbox.get(sandbox);
			return files == null ? new HashSet<VirtualFile>() : files;
		}

		public void addNonRecursive(MksSandboxInfo owningSandbox, VirtualFile vFile) {
			assert !computed : "too late !";
			if (vFile.isDirectory()) {
				addAndCreateSetIfAbsent(nonRecursivelyIncludedRootsPerSandbox, owningSandbox, vFile);
			}
		}

		private static void addAndCreateSetIfAbsent(
				Map<MksSandboxInfo, HashSet<VirtualFile>> map,
				MksSandboxInfo sandboxInfo, VirtualFile root) {
			HashSet<VirtualFile> files = map.get(sandboxInfo);
			if (files == null) {
				files = new HashSet<VirtualFile>();
				map.put(sandboxInfo, files);
			}
			files.add(root);
		}

		private final static class DirectoryWalker {
			public void process(SandboxesToRefresh refreshSpec) {
				for (Map.Entry<MksSandboxInfo, ArrayList<VirtualFile>> entry : refreshSpec
						.excludedRootsPerSandbox.entrySet()) {
					final VirtualFile root = entry.getKey().getSandboxDir();
					processRecursivelyExcluding(entry.getKey(), root, entry.getValue(), refreshSpec);
				}
			}

			private void processRecursivelyExcluding(MksSandboxInfo sandboxInfo, VirtualFile root,
													 ArrayList<VirtualFile> excluded,
													 SandboxesToRefresh spec) {
				boolean containsExcluded = false;
				if (root.isDirectory()) {
					if (excluded.contains(root)) {
						return;
					}
					for (VirtualFile file : excluded) {
						if (file.isDirectory() && VfsUtil.isAncestor(root, file, false)) {
							containsExcluded = true;
							break;
						}
					}
					if (containsExcluded) {
//						System.out.println("adding " + root + " to nonRecursivelyIncludedRootsPerSandbox for " +
//								sandboxInfo.getSandboxDir());
						addAndCreateSetIfAbsent(spec.nonRecursivelyIncludedRootsPerSandbox, sandboxInfo, root);
						for (VirtualFile child : root.getChildren()) {
							if (child.isDirectory()) {
								processRecursivelyExcluding(sandboxInfo, child, excluded, spec);
							}
						}
					} else {
//						System.out.println("adding " + root + " to recursivelyIncludedRootsPerSandbox for " +
//								sandboxInfo.getSandboxDir());
						addAndCreateSetIfAbsent(spec.recursivelyIncludedRootsPerSandbox, sandboxInfo, root);
					}
				}
			}

		}

		private void computeInclusions() {
			if (computed) {
				return;
			}
			final DirectoryWalker walker = new DirectoryWalker();
			walker.process(this);
			computed = true;

		}

		/**
		 * returns a list a directories where each one should be refreshed NOT inclusively
		 *
		 * @param sandbox
		 * @return
		 */
		public HashSet<VirtualFile> getNonRecursivelyIncludedRoots(MksSandboxInfo sandbox) {
			computeInclusions();
			final HashSet<VirtualFile> files = nonRecursivelyIncludedRootsPerSandbox.get(sandbox);
			return files == null ? new HashSet<VirtualFile>() : files;
		}
	}

	@NotNull
	private SandboxesToRefresh processRecursively(@NotNull VcsDirtyScope dirtyScope) {
		final Set<FilePath> recursivelyDirtyDirectories = dirtyScope.getRecursivelyDirtyDirectories();
		final SandboxesToRefresh sandboxesToRefresh = new SandboxesToRefresh();
		final SandboxCache cache = getMksvcs().getSandboxCache();
		for (FilePath directory : recursivelyDirtyDirectories) {

			final VirtualFile dirVFile = directory.getVirtualFile();
			if (dirVFile == null) {
//				System.err.println("no vfile for " + directory);
			} else {
				processRecursively(dirVFile, cache, sandboxesToRefresh);
			}
		}
		for (FilePath path : dirtyScope.getDirtyFiles()) {
			final VirtualFile file = path.getVirtualFile();
			if (null == file) {
//				System.err.println("no vfile for " + file);
			} else {
				processRecursively(file, cache, sandboxesToRefresh);
			}
		}

//		System.out.println("refreshSpec " + sandboxesToRefresh.toString());
		return sandboxesToRefresh;
	}

	private void processRecursively(@NotNull final VirtualFile vFile,
									@NotNull final SandboxCache cache,
									@NotNull final SandboxesToRefresh refresh) {
		final MksSandboxInfo owningSandbox = cache.getSandboxInfo(vFile);
		if (owningSandbox != null) {
			final Module module = ModuleUtil.findModuleForFile(vFile, getProject());
			if (module == null) {
				refresh.addNonRecursive(owningSandbox, vFile);
			} else {
				refresh.refresh(owningSandbox, ModuleRootManager.getInstance(module).getExcludeRoots());
			}
		} else if (vFile.isDirectory()) {
			for (VirtualFile child : vFile.getChildren()) {
				if (child.isDirectory()) {
					processRecursively(child, cache, refresh);
				}
			}
		}

	}

	/**
	 * renames change lists mapped to change packages if the change package was renamed
	 *
	 * @param changePackagesPerServer
	 */
	private void updateChangeListNames(Map<MksServerInfo, Map<String, MksChangePackage>> changePackagesPerServer) {
		MksChangeListAdapter adapter = getChangeListAdapter();
		for (Map<String, MksChangePackage> packageMap : changePackagesPerServer.values()) {
			for (MksChangePackage changePackage : packageMap.values()) {
				final ChangeList changeList = adapter.getChangeList(changePackage);
				if (changeList != null && !changePackage.getSummary().equals(changeList.getName())
						&& changeList instanceof LocalChangeList) {
					((LocalChangeList) changeList).setName(changePackage.getSummary());
				}

			}
		}
	}

	private void checkNeededServersAreOnlineAndReconnectIfNeeded(@NotNull Set<MksSandboxInfo> sandboxesToRefresh,
																 @NotNull ArrayList<MksServerInfo> servers) {
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
				String user = null;
				String password = null;
			}
			final UserAndPassword userAndPassword = new UserAndPassword();
			try {
				SwingUtilities.invokeAndWait(new Runnable() {
					public void run() {
						final String message = MksBundle
								.message("mks.reconnect.to.server", hostAndPort);
						userAndPassword.user =
								Messages.showInputDialog(getMksvcs().getProject(), "user", message, null);
						userAndPassword.password =
								Messages.showInputDialog(getMksvcs().getProject(), "password", message, null);

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
			SiConnectCommand command =
					new SiConnectCommand(getMksvcs(), host, port, userAndPassword.user, userAndPassword.password);
			command.execute();
			if (!command.foundError() && (command.getServer() != null)) {
				servers.add(command.getServer());
			} else {
				reportErrors(command.errors, "unable to connect to " + hostAndPort);
			}
		}
	}

	private void setStatusInfo(final JLabel statusLabel, final String message) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				statusLabel.setText(message);
			}
		});
	}

	private ChangelistBuilder createBuilderLoggingProxy(final ChangelistBuilder myBuilder) {
		return (ChangelistBuilder) Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class[]{ChangelistBuilder.class},
				new InvocationHandler() {
					public Object invoke(final Object o, final Method method, final Object[] args) throws Throwable {
						StringBuffer buffer = new StringBuffer("(");
						for (Object arg : args) {
							buffer.append(arg).append(",");
						}
						buffer.setLength(buffer.length() - 1);
						buffer.append(")");
						BUILDER_PROXY_LOGGER.debug(method.getName() + buffer);
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

	private void processDirtySandbox(final ChangelistBuilder builder,
									 @NotNull final Map<String, MksChangePackage> changePackages,
									 final Map<String, MksMemberState> states) {
		final ChangeListManager listManager = ChangeListManager.getInstance(getProject());
		for (Map.Entry<String, MksMemberState> entry : states.entrySet()) {
			MksMemberState state = entry.getValue();
			FilePath filePath = VcsUtil.getFilePath(entry.getKey());
			VirtualFile virtualFile = VcsUtil.getVirtualFile(entry.getKey());
			if (null == virtualFile || getMksvcs().getSandboxCache().isSandboxProject(virtualFile)) {
				continue;
			}
			if (!myProject.getAllScope().contains(virtualFile)) {
				logger.warn("project excluded file, skipping " + virtualFile);
				continue;
			}
			if (listManager.isIgnoredFile(virtualFile)) {
				builder.processIgnoredFile(virtualFile);
				continue;
			}
			switch (state.status) {
				case ADDED: {
					MksChangePackage changePackage = getChangePackage(changePackages, state);
					Change change = new Change(null, CurrentContentRevision.create(filePath), FileStatus.ADDED);
					if (changePackage == null) {
						builder.processChange(change);
					} else {
						ChangeList changeList = getMksvcs().getChangeListAdapter().trackMksChangePackage(changePackage);
						builder.processChangeInList(change, changeList);
					}
					break;
				}
				case CHECKED_OUT: {
					MksChangePackage changePackage = getChangePackage(changePackages, state);
					Change change = new Change(new MksContentRevision(getMksvcs(), filePath, state.memberRevision),
							CurrentContentRevision.create(filePath), FileStatus.MODIFIED);
					if (changePackage == null) {
						builder.processChange(change);
					} else {
						ChangeList changeList = getMksvcs().getChangeListAdapter().trackMksChangePackage(changePackage);
						builder.processChangeInList(change, changeList);
					}
					break;
				}
				case MODIFIED_WITHOUT_CHECKOUT: {
					builder.processModifiedWithoutCheckout(virtualFile);
					break;
				}
				case MISSISNG: {
					builder.processLocallyDeletedFile(filePath);
					break;
				}
				case SYNC:
					// todo some of those changes belong to the Incoming tab
					builder.processChange(new Change(
							new MksContentRevision(getMksvcs(), filePath, state.workingRevision),
							new MksContentRevision(getMksvcs(), filePath, state.memberRevision), FileStatus.OBSOLETE));
					break;
				case DROPPED: {
					MksChangePackage changePackage = getChangePackage(changePackages, state);
					Change change = new Change(new MksContentRevision(getMksvcs(), filePath, state.memberRevision),
							CurrentContentRevision.create(filePath), FileStatus.DELETED);
					if (changePackage == null) {
						builder.processChange(change);
					} else {
						ChangeList changeList = getMksvcs().getChangeListAdapter().trackMksChangePackage(changePackage);
						builder.processChangeInList(change, changeList);
					}
					break;
				}
				case NOT_CHANGED:
					break;
				case UNVERSIONED: {
					builder.processUnversionedFile(virtualFile);
					break;
				}
				case REMOTELY_DROPPED: {
					builder.processChange(new Change(
							new MksContentRevision(getMksvcs(), filePath, state.memberRevision),
							new MksContentRevision(getMksvcs(), filePath, state.workingRevision), FileStatus.OBSOLETE));
					break;
				}
				case UNKNOWN: {
					builder.processChange(new Change(
							new MksContentRevision(getMksvcs(), filePath, state.memberRevision),
							new MksContentRevision(getMksvcs(), filePath, state.workingRevision), FileStatus.UNKNOWN));
					break;
				}
				default: {
					logger.info("unhandled MKS status " + state.status);
				}
			}
		}
	}

	@Nullable
	private MksChangePackage getChangePackage(@NotNull final Map<String, MksChangePackage> changePackages,
											  @NotNull final MksMemberState state) {
		return state.workingChangePackageId == null ? null : changePackages.get(state.workingChangePackageId);
	}

	@NotNull
	private Map<String, MksMemberState> getSandboxState(@NotNull final MksSandboxInfo sandbox,
														@NotNull final ArrayList<VcsException> errors,
														@NotNull final MksServerInfo server,
														final SandboxesToRefresh refreshSpec) {
		Map<String, MksMemberState> states = new HashMap<String, MksMemberState>();

		ViewSandboxWithoutChangesCommand fullSandboxCommand = new ViewSandboxWithoutChangesCommand(errors, getMksvcs(),
				sandbox.sandboxPath);
		fullSandboxCommand.execute();
		addNonExcludedStates(states, fullSandboxCommand.getMemberStates());

		ViewSandboxLocalChangesOrLockedCommand localChangesCommand =
				new ViewSandboxLocalChangesOrLockedCommand(errors, getMksvcs(), server.user, sandbox.sandboxPath);
		localChangesCommand.execute();
		addNonExcludedStates(states, localChangesCommand.getMemberStates());

		if (ApplicationManager.getApplication().getComponent(MksConfiguration.class).isSynchronizeNonMembers()) {
			final HashSet<VirtualFile> recursivelyIncludedDirs = refreshSpec.getRecursivelyIncludedRoots(sandbox);
			final HashSet<VirtualFile> nonRecursivelyIncludedDirs = refreshSpec.getNonRecursivelyIncludedRoots(sandbox);
			String[] recursivelyIncludedDirsArray = convertToSandboxRelativePaths(sandbox, recursivelyIncludedDirs);
			String[] nonRecursivelyIncludedDirsArray =
					convertToSandboxRelativePaths(sandbox, nonRecursivelyIncludedDirs);

			// process recursive inclusions
			ViewNonMembersCommand nonMembersCommandRecursive = new ViewNonMembersCommand(errors, getMksvcs(), sandbox,
					recursivelyIncludedDirsArray, true);
			nonMembersCommandRecursive.execute();
			addNonExcludedStates(states, nonMembersCommandRecursive.getMemberStates());

			// process non recursive inclusions
			ViewNonMembersCommand nonMembersCommandNonRecursive =
					new ViewNonMembersCommand(errors, getMksvcs(), sandbox,
							nonRecursivelyIncludedDirsArray, false);
			nonMembersCommandNonRecursive.execute();
			addNonExcludedStates(states, nonMembersCommandNonRecursive.getMemberStates());
		}

// todo the below belong to incoming changes
		ViewSandboxOutOfSyncCommand outOfSyncCommand =
				new ViewSandboxOutOfSyncCommand(errors, getMksvcs(), sandbox.sandboxPath);
		outOfSyncCommand.execute();
		states.putAll(outOfSyncCommand.getMemberStates());

//		ViewSandboxRemoteChangesCommand missingCommand = new ViewSandboxRemoteChangesCommand(errors, getMksvcs(), sandbox.sandboxPath);
//		missingCommand.execute();
//		states.putAll(missingCommand.getMemberStates());

		return states;
	}

	private String[] convertToSandboxRelativePaths(MksSandboxInfo sandbox,
												   HashSet<VirtualFile> recursivelyIncludedDirs) {
		String[] recursivelyIncludedDirsArray = new String[recursivelyIncludedDirs.size()];
		int i = 0;
		for (VirtualFile dir : recursivelyIncludedDirs) {
			recursivelyIncludedDirsArray[i++] = sandbox.getRelativePath(dir);
		}
		return recursivelyIncludedDirsArray;
	}

	private void addNonExcludedStates(Map<String, MksMemberState> collectingMap, Map<String, MksMemberState> source) {
		for (Map.Entry<String, MksMemberState> entry : source.entrySet()) {
			final FilePath path = VcsUtil.getFilePath(entry.getKey());
			if (path.getVirtualFile() != null && myProject.getProjectScope().contains(path.getVirtualFile())) {
				collectingMap.put(entry.getKey(), entry.getValue());
			} else if (logger.isDebugEnabled()) {
				logger.debug("skipping " + path.getPath());
			}

		}
	}

	/**
	 * @param progress the progress bar to report progress with
	 * @param errors   container to store encountered errors
	 * @param servers  the si servers to query change packages for
	 * @return Map <MksServerInfo, Map<MksChangePackage.id,MksChangePackage>>
	 */
	private Map<MksServerInfo, Map<String, MksChangePackage>> getChangePackages(final ProgressIndicator progress,
																				final ArrayList<VcsException> errors,
																				final ArrayList<MksServerInfo> servers) {
		final Map<MksServerInfo, Map<String, MksChangePackage>> changePackages =
				new HashMap<MksServerInfo, Map<String, MksChangePackage>>();
		final MksConfiguration config = ApplicationManager.getApplication().getComponent(MksConfiguration.class);
		for (MksServerInfo server : servers) {
			if (!config.isServerSiServer(server)) {
				getMksvcs().debug("ignoring " + server.host + ":" + server.port + " when querying changepackages");
				continue;
			}
			final ListChangePackages listCpsAction = new ListChangePackages(errors, getMksvcs(), server);
			if (progress != null) {
				progress.setIndeterminate(true);
				progress.setText("Querying change packages for " + server + "...");
			}
			listCpsAction.execute();
			if (listCpsAction.foundError()) {
				reportErrors(listCpsAction.errors, "error querying mks cps");
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

	@NotNull
	private ArrayList<MksServerInfo> getMksServers(final ProgressIndicator progress,
												   final ArrayList<VcsException> errors) {
		final ListServers listServersAction = new ListServers(errors, getMksvcs());
		if (progress != null) {
			progress.setIndeterminate(true);
			progress.setText("Querying mks servers ...");
		}
		listServersAction.execute();
		if (listServersAction.foundError()) {
			reportErrors(listServersAction.errors, "encountered errors querying servers");
		}
		return listServersAction.servers;
	}

	private void reportErrors(List<VcsException> errors, String message) {
		for (VcsException error : errors) {
			logger.warn(message, error);
		}
	}

	public boolean isModifiedDocumentTrackingRequired() {
		return false;
	}

	@NotNull
	public MksChangeListAdapter getChangeListAdapter() {
		return getMksvcs().getChangeListAdapter();
	}


	public void decorateChangeList(LocalChangeList changeList, ColoredTreeCellRenderer cellRenderer, boolean selected,
								   boolean expanded, boolean hasFocus) {
		MksChangeListAdapter changeListAdapter = getChangeListAdapter();
		if (!changeListAdapter.isChangeListMksControlled(changeList.getName())) {
			return;
		}
		MksChangePackage aPackage = changeListAdapter.getMksChangePackage(changeList.getName());
		if (aPackage != null) {
			cellRenderer.append(" - MKS #" + aPackage.getId(), SimpleTextAttributes.GRAY_ATTRIBUTES);
		}
	}

	@NotNull
	private MksVcs getMksvcs() {
		return (MksVcs) ProjectLevelVcsManager.getInstance(myProject).findVcsByName(MksVcs.VCS_NAME);
	}
}