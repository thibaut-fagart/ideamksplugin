package org.intellij.vcs.mks;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.intellij.vcs.mks.model.MksChangePackage;
import org.intellij.vcs.mks.model.MksMemberState;
import org.intellij.vcs.mks.model.MksServerInfo;
import org.intellij.vcs.mks.realtime.MksSandboxInfo;
import org.intellij.vcs.mks.sicommands.ListChangePackages;
import org.intellij.vcs.mks.sicommands.ListServers;
import org.intellij.vcs.mks.sicommands.ViewSandboxLocalChangesCommand;
import org.intellij.vcs.mks.sicommands.ViewSandboxMissingCommand;
import org.intellij.vcs.mks.sicommands.ViewSandboxOutOfSyncCommand;
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

/**
 * @author Thibaut Fagart
 * @see com.intellij.openapi.vcs.changes.VcsDirtyScopeManager allows to notify
 *      idea if files should be marked dirty
 */
class MKSChangeProvider implements ChangeProvider, ProjectComponent, ChangeListDecorator {
	private final Logger LOGGER = Logger.getInstance(getClass().getName());
	private final Logger BUILDER_PROXY_LOGGER = Logger.getInstance(getClass().getName()+".ChangelistBuilder");
	@NotNull
	private final MksVcs mksvcs;

	public MKSChangeProvider(@NotNull MksVcs mksvcs) {
		this.mksvcs = mksvcs;
	}


	public void getChanges(final VcsDirtyScope dirtyScope, ChangelistBuilder builder, final ProgressIndicator progress) throws VcsException {
		ArrayList<VcsException> errors = new ArrayList<VcsException>();
		LOGGER.debug("start getChanges");
		try {
//            System.out.println("dirtyScope " + dirtyScope);
//			System.out.println("getDirtyFiles " + dirtyScope.getDirtyFiles());
//			System.out.println("getAffectedContentRoots " + dirtyScope.getAffectedContentRoots());
//            System.out.println("getRecursivelyDirtyDirectories " + dirtyScope.getRecursivelyDirtyDirectories());
			ArrayList<MksServerInfo> servers = getMksServers(progress, errors);
			final Map<MksServerInfo, Map<String, MksChangePackage>> changePackagesPerServer = getChangePackages(progress, errors, servers);
			// collect affected sandboxes
			final ChangelistBuilder myBuilder = builder;
			if (MksVcs.DEBUG) {
				builder = createBuilderLoggingProxy(myBuilder);
			}
			Map<String, MksServerInfo> serversByHostAndPort = distributeServersByHostAndPort(servers);
			Map<MksServerInfo, Map<String, MksMemberState>> states = new HashMap<MksServerInfo, Map<String, MksMemberState>>();
			for (VirtualFile dir : dirtyScope.getAffectedContentRoots()) {
				Set<MksSandboxInfo> sandboxes = mksvcs.getSandboxCache().getSandboxesIntersecting(dir);
				for (MksSandboxInfo sandbox : sandboxes) {
					MksServerInfo sandboxServer = serversByHostAndPort.get(sandbox.hostAndPort);
					if (states.get(sandboxServer) == null) {
						states.put(sandboxServer, new HashMap<String, MksMemberState>());
					}
					states.get(sandboxServer).putAll(getSandboxState(sandbox, errors, sandboxServer));
				}
			}
			for (Map.Entry<MksServerInfo, Map<String, MksMemberState>> entry : states.entrySet()) {
				MksServerInfo sandboxServer = entry.getKey();
				processDirtySandbox(builder, changePackagesPerServer.get(sandboxServer), entry.getValue());
			}
		} catch (RuntimeException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			throw e;
		} finally {
			LOGGER.debug("end getChanges");
		}
		if (!errors.isEmpty()) {
			mksvcs.showErrors(errors, "ChangeProvider");
		}


	}

	private ChangelistBuilder createBuilderLoggingProxy(final ChangelistBuilder myBuilder) {
		return (ChangelistBuilder) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ChangelistBuilder.class}, new InvocationHandler() {
			public Object invoke(final Object o, final Method method, final Object[] objects) throws Throwable {
				StringBuffer buf = new StringBuffer("(");
				for (int i = 0; i < objects.length; i++) {
					Object object = objects[i];
					buf.append(object).append(",");
				}
				buf.setLength(buf.length() - 1);
				buf.append(")");
				BUILDER_PROXY_LOGGER.debug(method.getName() + buf);
				return method.invoke(myBuilder, objects);
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
		for (Map.Entry<String, MksMemberState> entry : states.entrySet()) {
			MksMemberState state = entry.getValue();
			FilePath filePath = VcsUtil.getFilePath(entry.getKey());
			VirtualFile virtualFile = VcsUtil.getVirtualFile(entry.getKey());
			switch (state.status) {
				case CHECKED_OUT: {
					MksChangePackage changePackage = changePackages.get(state.workingChangePackageId);
					Change change = new Change(
						new MksContentRevision(mksvcs, filePath, state.workingRevision),
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
					builder.processModifiedWithoutCheckout(virtualFile);
					break;
				}
				case MISSISNG: {
					builder.processLocallyDeletedFile(filePath);
					break;
				}
				case SYNC:
					// handle dropped status as an out of sync 
				case DROPPED: {
					builder.processChange(new Change(
						new MksContentRevision(mksvcs, filePath, state.workingRevision),
						new MksContentRevision(mksvcs, filePath, state.memberRevision),
						FileStatus.OBSOLETE));
					break;
				}
				case NOT_CHANGED: break;
				default: {
					LOGGER.info("unhandled MKS status " + state.status);
				}
			}
		}
	}

	private Map<String, MksMemberState> getSandboxState(final MksSandboxInfo sandbox, final ArrayList<VcsException> errors, final MksServerInfo server) {
		Map<String, MksMemberState> states = new HashMap<String, MksMemberState>();

		ViewSandboxWithoutChangesCommand fullSandboxCommand = new ViewSandboxWithoutChangesCommand(errors, mksvcs, server.user, sandbox.sandboxPath);
		fullSandboxCommand.execute();
		states.putAll(fullSandboxCommand.getMemberStates());

		ViewSandboxOutOfSyncCommand outOfSyncCommand = new ViewSandboxOutOfSyncCommand(errors, mksvcs, server.user, sandbox.sandboxPath);
		outOfSyncCommand.execute();
		states.putAll(outOfSyncCommand.getMemberStates());

		ViewSandboxLocalChangesCommand localChangesCommand = new ViewSandboxLocalChangesCommand(errors, mksvcs, server.user, sandbox.sandboxPath);
		localChangesCommand.execute();
		states.putAll(localChangesCommand.getMemberStates());

		ViewSandboxMissingCommand missingCommand = new ViewSandboxMissingCommand(errors, mksvcs, server.user, sandbox.sandboxPath);
		missingCommand.execute();
		states.putAll(missingCommand.getMemberStates());

		return states;
	}

	/**
	 *
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

	private ArrayList<MksServerInfo> getMksServers(final ProgressIndicator progress, final ArrayList<VcsException> errors) {
		final ListServers listServersAction = new ListServers(errors, mksvcs);
		if (progress != null) {
			progress.setIndeterminate(true);
			progress.setText("Querying mks servers ...");
		}
		listServersAction.execute();
		if (listServersAction.foundError()) {
			LOGGER.warn("encountered errors querying servers");
		}
		ArrayList<MksServerInfo> servers = listServersAction.servers;
		return servers;
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

