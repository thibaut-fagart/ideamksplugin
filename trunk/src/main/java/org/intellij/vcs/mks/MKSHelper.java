package org.intellij.vcs.mks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import mks.integrations.common.*;
import org.intellij.vcs.mks.sicommands.ListSandboxes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class MKSHelper {
	private static final Logger LOGGER = Logger.getInstance(MKSHelper.class.getName());

	private static TriclopsSiClient CLIENT;
	private static final Object mksLock = new Object();
	//	protected static final String CLIENT_LIBRARY_NAME = "mkscmapi";

	private static boolean isClientLoaded;
	private static boolean isClientValid;
	/**
	 * sandboxFolder virtualFile => TriclopsSiSandbox
	 */
	private static final HashMap<VirtualFile, TriclopsSiSandbox> SANDBOX_CACHE = new HashMap<VirtualFile, TriclopsSiSandbox>();
	private static long LAST_SANDBOX_CACHE_REFRESH = 0;
	private static final long SECOND = 1000;
	private static long SANDBOX_REFRESH_IF_OLDER = 5 * SECOND;

	public static void getMembersStatus(TriclopsSiMembers members) throws TriclopsException {
		synchronized (mksLock) {
			if (members.getNumMembers() > 0) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("queryStatuses {" + members.getMember(0).getPath() + "}");
				}
			} else {
				LOGGER.debug("queryStatuses {emptyMembers}");
			}
			members.getMembersStatus();
		}
	}

	public static synchronized String getMksErrorMessage() {
		return "Mks.errorMessage = " + CLIENT.getErrorMessage() +
			"\nMks.extendedErrorMessage = " + CLIENT.getExtendedErrorMessage();

	}


	/**
	 * @param virtualFile a mks controlled file
	 * @param mksVcs
	 * @return the mks sandbox in which the filepath is if any
	 * @throws com.intellij.openapi.vcs.VcsException
	 *          if the file is not in a sandbox
	 */
	@NotNull
	public static TriclopsSiSandbox getSandbox(@NotNull VirtualFile virtualFile, MksVcs mksVcs) throws VcsException {
		TriclopsSiSandbox sandbox = findSandboxInCache(virtualFile, false);
		if (sandbox == null) {
			refreshSandboxCache(mksVcs);
			sandbox = findSandboxInCache(virtualFile, true);
			if (sandbox == null) {
				System.out.println("can't find sandbox for file[" + virtualFile.getPath() + "]");
				throw new VcsException("can't find sandbox for file[" + virtualFile.getPath() + "]");
			}
		}
		return sandbox;
		//		VirtualFile temp = virtualFile.getParent(), candidate = null;
//		while (candidate == null && temp != null) {
//			candidate = temp.findFileByRelativePath("project.pj");
//			temp = temp.getParent();
//		}
//		if (candidate != null) {
//			TriclopsSiSandbox sandbox = getSandBoxForProjectPj(candidate, virtualFile);
//			sandbox.setIdeProjectPath(virtualFile.getPresentableUrl());
//			return sandbox;
//
//		} else {
//			if (LOGGER.isDebugEnabled()) {
//				LOGGER.debug("did not find sandbox for " + virtualFile);
//			}
//			synchronized (mksLock) {
//				if (!isClientValid) {
//					startClient();
//				}
//				try {
//					TriclopsSiSandbox sandbox = new TriclopsSiSandbox(CLIENT);
//					sandbox.setIdeProjectPath(virtualFile.getPresentableUrl());
//					sandbox.validate();
//					return sandbox;
//				} catch (TriclopsException e) {
//					throw new VcsException("can't find sandbox for file[" + virtualFile.getPath() + "]" + "\n" + getMksErrorMessage());
//				}
//			}
//		}
	}

	@Nullable
	private static TriclopsSiSandbox findSandboxInCache(@NotNull VirtualFile virtualFile, boolean recursive) {
		TriclopsSiSandbox sandbox = null;
		VirtualFile cursorDir = (virtualFile.isDirectory() ? virtualFile : virtualFile.getParent());
		if (recursive) {
			for (; cursorDir != null && sandbox == null; cursorDir = cursorDir.getParent()) {
				sandbox = SANDBOX_CACHE.get(cursorDir);
			}
		} else {
			return SANDBOX_CACHE.get(cursorDir);
		}
		return sandbox;
	}

	private static void refreshSandboxCache(MksVcs mksVcs) {
		synchronized (SANDBOX_CACHE) {
			if (System.currentTimeMillis() > LAST_SANDBOX_CACHE_REFRESH + SANDBOX_REFRESH_IF_OLDER) {
				ListSandboxes listSandboxes = new ListSandboxes(new ArrayList<VcsException>(), mksVcs);
				listSandboxes.execute();
				if (listSandboxes.foundError()) {
					LOGGER.error("error while refreshing sandbox list");
					return;
				} else {
					SANDBOX_CACHE.clear();
					for (TriclopsSiSandbox sandbox : listSandboxes.sandboxes) {
						VirtualFile virtualFile = VcsUtil.getVirtualFile(sandbox.getPath());
						if (virtualFile == null) {
							LOGGER.error("No VirtualFile for " + sandbox.getPath());
							break;
						}
						SANDBOX_CACHE.put(virtualFile.getParent(), sandbox);
					}
					LAST_SANDBOX_CACHE_REFRESH = System.currentTimeMillis();
				}
			}
		}
	}

/*
	private static TriclopsSiSandbox getSandBoxForProjectPj(String sandboxPath, VirtualFile sandboxMember) throws VcsException {
//		System.out.println("looking up sandbox cache for " + sandboxPath);
		synchronized (mksLock) {
			TriclopsSiSandbox sandbox = SANDBOX_CACHE.get(sandboxPath);
			if (sandbox == null) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("SANDBOX_CACHE cache miss");
				}
				if (!isClientValid) {
					startClient();
				}
				try {
					sandbox = new TriclopsSiSandbox(CLIENT);
					sandbox.setIdeProjectPath(sandboxMember.getPresentableUrl());
					sandbox.validate();

				} catch (TriclopsException e) {
					throw new VcsException("can't find sandbox for file[" + sandboxPath + "]" + "\n" + getMksErrorMessage());
				}
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("SANDBOX_CACHE adding  " + sandboxPath + " for member " + sandboxMember);
				}
				SANDBOX_CACHE.put(sandbox.getPath(), sandbox);
			} else {
//				System.out.println("SANDBOX_CACHE cache hit");
			}
			return sandbox;
		}

	}
*/

	public static void disconnect() throws VcsException {
		if (CLIENT != null) {
			try {
				CLIENT.disconnect();
			} catch (TriclopsException e) {
				throw new VcsException("error disconnecting ");
			}
			SANDBOX_CACHE.clear();
		}

	}

	public static void startClient() {
		if (!isClientLoaded) {
			LOGGER.debug("Starting MKS client");
			try {
				// todo would it be needed to allow configuration to locate that dll ?
				System.loadLibrary("mkscmapi");
				isClientLoaded = true;
				CLIENT = new TriclopsSiClient();
				if (!CLIENT.isIntegrityClientRunning()) {
					CLIENT.initialize("IntelliJ IDEA", 1, 1);
				}
				isClientValid = true;
			} catch (Throwable t) {
				LOGGER.error("FAILED Starting MKS client", t);
				isClientLoaded = false;
				isClientValid = false;
			}
		}
	}


	public static TriclopsSiMembers createMembers(TriclopsSiSandbox sandbox) {
		return new TriclopsSiMembers(CLIENT, sandbox);
	}

	public static boolean isLastCommandCancelled() {
		synchronized (mksLock) {
			return "The command was cancelled.".equals(CLIENT.getErrorMessage());
		}
	}

	public static void checkoutMembers(TriclopsSiMembers members, int flags) throws TriclopsException {
		synchronized (mksLock) {
			if (CLIENT != null) {
				members.checkoutMembers(flags);
				getMembersStatus(members);
			}
		}
	}

	public static void checkinMembers(TriclopsSiMembers members, int flags) throws TriclopsException {
		synchronized (mksLock) {
			if (CLIENT != null) {
				members.checkinMembers(flags);
				getMembersStatus(members);
			}
		}

	}

	public static void aboutBox() throws TriclopsException {
		synchronized (mksLock) {
			if (!isClientValid) {
				startClient();
			}
			if (CLIENT != null) {
				CLIENT.aboutBox();
			}
		}

	}

	public static void addMembers(TriclopsSiMembers members, int flags) throws TriclopsException {
		synchronized (mksLock) {
			if (CLIENT != null) {
				members.addMembers(flags);
				getMembersStatus(members);
			}
		}

	}

	public static void dropMembers(TriclopsSiMembers members, int flags) throws TriclopsException {
		synchronized (mksLock) {
			if (CLIENT != null) {
				members.dropMembers();
				getMembersStatus(members);
			}
		}

	}

	public static void launchClient() {
		synchronized (mksLock) {
			if (!isClientValid) {
				startClient();
			}
			try {
				CLIENT.launch();
			} catch (TriclopsException e) {
				LOGGER.error("error while launching MKS Client", e);
			}
		}
	}

	public static void openMemberDiffView(TriclopsSiMembers members, int flags) throws TriclopsException {
		synchronized (mksLock) {
			if (CLIENT != null) {
				members.openMemberDiffView(0);
			}
		}

	}

	public static void openMemberArchiveView(TriclopsSiMembers members, int flags) throws TriclopsException {
		synchronized (mksLock) {
			if (CLIENT != null) {
				members.openMemberArchiveView(0);
			}
		}

	}

	public static void openMemberInformationView(TriclopsSiMembers members, int flags) throws TriclopsException {
		synchronized (mksLock) {
			if (CLIENT != null) {
				members.openMemberInformationView(flags);
			}
		}

	}

	public static void resyncMembers(TriclopsSiMembers members, int flags) throws TriclopsException {
		synchronized (mksLock) {
			if (CLIENT != null) {
				members.resyncMembers(flags);
				getMembersStatus(members);
			}
		}

	}

	public static void revertMembers(TriclopsSiMembers members, int flags) throws TriclopsException {
		synchronized (mksLock) {
			if (CLIENT != null) {
				members.revertMembers(flags);
				getMembersStatus(members);
			}
		}

	}

	public static void viewSandbox(TriclopsSiSandbox sandbox) throws TriclopsException {
		synchronized (mksLock) {
			if (CLIENT != null) {
				sandbox.openSandboxView(null);
			}
		}

	}

	public static void openConfigurationView() throws TriclopsException {
		synchronized (mksLock) {
			if (!isClientValid) {
				startClient();
			}
			if (CLIENT == null) {
				return;
			}
			CLIENT.openConfigurationView();
		}

	}

	public static String[] getProjectMembers(TriclopsSiSandbox sandbox) throws TriclopsException {
		synchronized (mksLock) {
			TriclopsSiProject project = new TriclopsSiProject(CLIENT);
			project.setPath(sandbox.getSandboxProject());
			return project.getMemberList();
		}
	}

	/**
	 * Ignores the files materializing MKS projects
	 *
	 * @param sandbox	 the sandbox containing the file
	 * @param virtualFile the file to be tested for ignore
	 * @return whether this file is ignorable according to MKS
	 */
	public static boolean isIgnoredFile(TriclopsSiSandbox sandbox, VirtualFile virtualFile) {
		return VfsUtil.virtualToIoFile(virtualFile).equals(new File(sandbox.getPath()));
	}

	public static TriclopsSiSandbox createSandbox(String pjFilePath) throws TriclopsException {
		synchronized (mksLock) {
			TriclopsSiSandbox siSandbox = new TriclopsSiSandbox(CLIENT);
			siSandbox.setPath(pjFilePath);
			siSandbox.setIdeProjectPath(pjFilePath);
			siSandbox.validate();
			return siSandbox;
		}
	}

}
