package org.intellij.vcs.mks;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import mks.integrations.common.TriclopsException;
import mks.integrations.common.TriclopsSiMember;
import mks.integrations.common.TriclopsSiMembers;
import mks.integrations.common.TriclopsSiSandbox;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.List;

// Referenced classes of package org.intellij.vcs.mks:
//            MksConfigurable

public class MksVcs extends AbstractVcs implements ProjectComponent {
	static final Logger LOGGER = Logger.getInstance(MksVcs.class.getName());
	//	public static TriclopsSiClient CLIENT;
	public static final String TOOL_WINDOW_ID = "MKS";
	private static final int MAJOR_VERSION = 0;
	private static final int MINOR_VERSION = 5;
	private ToolWindow mksToolWindow;
	private JTabbedPane mksContentPanel;
	private JTextPane mksTextArea;
	private MksVcs.MyVirtualFileAdapter myVirtualFileAdapter;
	static final boolean DEBUG = true;
	private ChangedResourcesTableModel changedResourcesTableModel;
	private static final int CHANGES_TAB_INDEX = 1;
	public static final String DATA_CONTEXT_PROJECT = "project";
	public static final String DATA_CONTEXT_MODULE = "module";
	public static final String DATA_CONTEXT_VIRTUAL_FILE_ARRAY = "virtualFileArray";


	public MksVcs(Project project) {
		super(project);
//        fileStatusProvider = new _FileStatusProvider(myProject);
	}

	@NotNull
	public String getComponentName() {
		return "MKS";
	}

	public void initComponent() {
		MKSHelper.startClient();
	}

	public void disposeComponent() {
	}

	public Configurable getConfigurable() {
		return new MksConfigurable(myProject);
	}

	public String getDisplayName() {
		return "MKS";
	}

	//	public byte[] getFileContent(String path) throws VcsException {
	//		return new byte[0];
	//	}

	public String getName() {
		return "MKS";
	}

	public void start() throws VcsException {
		super.start();

		StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
			public void run() {
				initToolWindow();
			}
		});

	}

	public void shutdown() throws VcsException {
		super.shutdown();
		unregisterToolWindow();
		MKSHelper.disconnect();
	}

	public void showErrors(java.util.List<VcsException> list, String action) {
		if (list.size() > 0) {
			StringBuffer buffer = new StringBuffer(mksTextArea.getText());
			buffer.append("\n");
			buffer.append(action).append(" Error: ");
			VcsException e;
			for (Iterator<VcsException> iterator = list.iterator(); iterator.hasNext(); buffer.append(e.getMessage())) {
				e = iterator.next();
				buffer.append("\n");
			}
			mksTextArea.setText(buffer.toString());
		}
	}

	private void initToolWindow() {
		ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
		mksContentPanel = new JTabbedPane();
		//new JPanel(new java.awt.BorderLayout());
		mksTextArea = new JTextPane();
		mksTextArea.setEditable(false);
		javax.swing.text.Style def = StyleContext.getDefaultStyleContext().getStyle("default");
		javax.swing.text.Style regular = mksTextArea.addStyle("REGULAR", def);
		StyleConstants.setFontFamily(def, "SansSerif");
		javax.swing.text.Style s = mksTextArea.addStyle("ITALIC", regular);
		StyleConstants.setItalic(s, true);
		s = mksTextArea.addStyle("BOLD", regular);
		StyleConstants.setBold(s, true);

		JPanel panelDebug = new JPanel(new BorderLayout());
		mksContentPanel.add(panelDebug, "Log", 0);
		panelDebug.add(new JScrollPane(mksTextArea), BorderLayout.CENTER);

		mksToolWindow = toolWindowManager.registerToolWindow("MKS", mksContentPanel, ToolWindowAnchor.BOTTOM);
		java.net.URL iconUrl = getClass().getResource("/icons/mks.gif");
		javax.swing.Icon icn = new ImageIcon(iconUrl);
		mksToolWindow.setIcon(icn);
		final JPopupMenu menu = new JPopupMenu();
		JMenuItem item = new JMenuItem("Clear");
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mksTextArea.setText("");
			}
		});
		menu.add(item);
		mksTextArea.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				maybeShowPopup(e, menu);
			}

			public void mouseReleased(MouseEvent e) {
				maybeShowPopup(e, menu);
			}
		});

		class MyJTable extends JTable implements DataProvider {
			@Nullable
			/** returns the key properties for the table elements : project, module, the VirtualFile*/
			public Object getData(@NonNls String name) {
				if (DATA_CONTEXT_PROJECT.equals(name)) {
					return MksVcs.this.myProject;
				} else if (DATA_CONTEXT_MODULE.equals(name)) {
					return findModule(MksVcs.this.myProject, changedResourcesTableModel.getVirtualFile(getSelectedRow()));
				} else if (DATA_CONTEXT_VIRTUAL_FILE_ARRAY.equals(name)) {
					return new VirtualFile[]{changedResourcesTableModel.getVirtualFile(getSelectedRow())};
				}
				//                System.out.println("ignoring data : "+name);
				return null;
			}
		}
		MyJTable changesTable = new MyJTable();
		changedResourcesTableModel = new ChangedResourcesTableModel();
		changesTable.setModel(changedResourcesTableModel);
		mksContentPanel.add(new JScrollPane(changesTable), "Changes", CHANGES_TAB_INDEX);
		final class MksChangePanelMouseAdapter extends MouseAdapter {
			public void mouseClicked(MouseEvent e) {
				if (MouseEvent.BUTTON3 == e.getButton() && e.getClickCount() == 1) {
					// Single right click
					ActionManager actionManager = ActionManager.getInstance();
					ActionGroup actionGroup = (ActionGroup) actionManager.getAction("MKS.Menu");
					ActionPopupMenu menu = actionManager.createActionPopupMenu("MksVcs.changes", actionGroup);
					JPopupMenu popupMenu = menu.getComponent();
					popupMenu.pack();
					popupMenu.show(e.getComponent(), e.getX(), e.getY());

					e.consume();
				}
			}
		}
		changesTable.addMouseListener(new MksChangePanelMouseAdapter());
	}

	private void maybeShowPopup(MouseEvent e, JPopupMenu menu) {
		if (e.isPopupTrigger()) {
			menu.show(mksTextArea, e.getX(), e.getY());
		}
	}

	private void unregisterToolWindow() {
		ToolWindowManager.getInstance(myProject).unregisterToolWindow("MKS");
		mksToolWindow = null;
	}

	public static AbstractVcs getInstance(Project project) {
		return project.getComponent(MksVcs.class);
	}

	public synchronized boolean fileExistsInVcs(FilePath filePath) {
		if (DEBUG) {
			debug("fileExistsInVcs : " + filePath.getPresentableUrl());
		}
		try {

			TriclopsSiSandbox sandbox = MKSHelper.getSandbox(filePath.getVirtualFile());
			TriclopsSiMembers members = MKSHelper.createMembers(sandbox);
			TriclopsSiMember triclopsSiMember = new TriclopsSiMember(filePath.getPresentableUrl());
			members.addMember(triclopsSiMember);
			try {
				MKSHelper.getMembersStatus(members);
			} catch (TriclopsException e) {
				throw new VcsException("can't get MKS status for [" + filePath.getPath() + "]\n" + getMksErrorMessage());
			}
			TriclopsSiMember returnedMember = members.getMember(0);
			return returnedMember.isStatusControlled();
		} catch (VcsException e) {
			ArrayList<VcsException> l = new ArrayList<VcsException>();
			l.add(e);
			showErrors(l, "fileExistsInVcs for " + filePath.getPath());
			return false;
		}
	}

	public static synchronized String getMksErrorMessage() {
		return MKSHelper.getMksErrorMessage();
	}

	public void debug(String s) {
		debug(s, null);
	}

	/**
	 * checks if the file is in a directory controlled by mks
	 *
	 * @param filePath
	 * @return true if the file is in a directory controlled by mks
	 */
	@SuppressWarnings({"JavaDoc"})
	// IDEA should document it
	@Override
	public synchronized boolean fileIsUnderVcs(FilePath filePath) {
		if (!filePath.getName().equals("project.pj")) {
			if (DEBUG) {
				debug("fileIsUnderVcs : " + filePath.getPresentableUrl());
			}
			try {
				MKSHelper.getSandbox(filePath.getVirtualFile());
				return true;
			} catch (VcsException e) {
				ArrayList<VcsException> l = new ArrayList<VcsException>();
				l.add(e);
				showErrors(l, "fileExistsInVcs[" + filePath.getPath() + "]");
				return false;
			}
		} else {
			return false;
		}
	}

	private void debug(String s, Exception e) {
		StringBuffer oldText = new StringBuffer(mksTextArea.getText());
		oldText.append("\n").append(s);
		if (e != null) {
			LOGGER.info(s, e);
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			oldText.append(sw.toString());
		} else {
			LOGGER.info(s);
		}
		mksTextArea.setText(oldText.toString());
	}

	public DiffProvider getDiffProvider() {
		// todo
		return super.getDiffProvider();
	}

	public static boolean isLastCommandCancelled() {
		return MKSHelper.isLastCommandCancelled();
	}

	public void setChanges(Map<VirtualFile, FileStatus> statuses) {
		this.changedResourcesTableModel.setFiles(statuses);
		mksContentPanel.setSelectedIndex(CHANGES_TAB_INDEX);

		if (!this.mksToolWindow.isVisible()) {
			this.mksToolWindow.show(new Runnable() {
				public void run() {
					// nothing
				}
			});
		}
	}

	/**
	 * returns the module for
	 *
	 * @param child   the file we want to find the module
	 * @param project the current project
	 * @return the module if any, or null if none is found
	 */
	@Nullable
	public static Module findModule(@NotNull Project project, @NotNull VirtualFile child) {
		// implementation for IDEA 5.1.x
		// see http://www.intellij.net/forums/thread.jspa?messageID=3311171&#3311171
		LOGGER.debug("findModule(project=" + project.getName() + ",file=" + child.getPresentableName() + ")");
		return ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(child);
		//  for IDEA 6, could it also use same as 5.1.x ?
		//		Module[] projectModules = ModuleManager.getInstance(project).getModules();
		//		for (Module projectModule : projectModules) {
		//			if (projectModule.getModuleScope().contains(child) /*|| projectModule.get*/) {
		////                System.out.println("found module " + projectModule.getName() + " for " + child);
		//				return projectModule;
		//			}
		//			ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(projectModule);
		//			for (VirtualFile contentRoot : moduleRootManager.getContentRoots()) {
		//				if (child.getPath().startsWith(contentRoot.getPath())) {
		////                    System.out.println("" + child.getPath() + " is under module content root " + contentRoot.getPath());
		//					return projectModule;
		//				}
		//			}
		//		}
		//		LOGGER.info("could not find module for " + child);
		//		return null;
	}

	//	private class _FileStatusProvider extends DelayedFileStatusProvider {
	//		public _FileStatusProvider(Project project) {
	//			super(project);
	//		}
	//
	//		protected Map<VirtualFile, FileStatus> calcStatus(final Collection<VirtualFile> collection) {
	//			return ApplicationManager.getApplication().runReadAction(new Computable<Map<VirtualFile, FileStatus>>() {
	//				public Map<VirtualFile, FileStatus> compute() {
	//					Map<VirtualFile, FileStatus> ret = new HashMap<VirtualFile, FileStatus>();
	//					ArrayList<VcsException> dispatchErrors = new ArrayList<VcsException>();
	//					DispatchBySandboxCommand dispatchCommand = new DispatchBySandboxCommand(dispatchErrors,
	//							collection.toArray(new VirtualFile[collection.size()]));
	//					dispatchCommand.execute();
	//					for (VirtualFile virtualFile : dispatchCommand.notInSandboxFiles) {
	//						ret.put(virtualFile, FileStatus.UNKNOWN);
	//					}
	//					for (Map.Entry<TriclopsSiSandbox, ArrayList<VirtualFile>> entry : dispatchCommand.filesBySandbox
	//							.entrySet()) {
	//						TriclopsSiSandbox sandbox = entry.getKey();
	//						ArrayList<VirtualFile> files = entry.getValue();
	//						ArrayList<VcsException> getStatusErrors = new ArrayList<VcsException>();
	//						GetStatusesCommand getStatusesCommand = new GetStatusesCommand(MksVcs.this, getStatusErrors, sandbox, files);
	//						getStatusesCommand.execute();
	//						ret.putAll(getStatusesCommand.statuses);
	//					}
	//					return ret;
	//				}
	//			});
	//		}
	//	}

	private class _EditFileProvider implements EditFileProvider {
		public void editFiles(VirtualFile[] virtualFiles) throws VcsException {
			List<VcsException> errors = new ArrayList<VcsException>();
			DispatchBySandboxCommand dispatchCommand = new DispatchBySandboxCommand(errors, virtualFiles);
			dispatchCommand.execute();
			if (!dispatchCommand.errors.isEmpty()) {
				Messages.showErrorDialog("Unable to find the sandbox(es) for the file(s)", "Could Not Start checkout");
				return;
			}
			for (Map.Entry<TriclopsSiSandbox, ArrayList<VirtualFile>> entry : dispatchCommand.filesBySandbox.entrySet()) {
				TriclopsSiSandbox sandbox = entry.getKey();
				ArrayList<VirtualFile> files = entry.getValue();
				errors = new ArrayList<VcsException>();
				CheckoutFilesCommand command = new CheckoutFilesCommand(errors, sandbox, files);
				synchronized (MksVcs.this) {
					command.execute();
				}
				if (!command.errors.isEmpty()) {
					Messages.showErrorDialog(errors.get(0).getLocalizedMessage(), "Could Not Start checkout");
					return;
				}
			}
		}

		public String getRequestText() {
			return "Would you like to invoke 'CheckOut' command?";
		}
	}

	private static class CheckoutFilesCommand extends AbstractMKSCommand {
		private TriclopsSiSandbox sandbox;
		private ArrayList<VirtualFile> files;

		public CheckoutFilesCommand(List<VcsException> errors, TriclopsSiSandbox sandbox,
									ArrayList<VirtualFile> files) {
			super(errors);
			this.sandbox = sandbox;
			this.files = files;
		}

		public void execute() {
			try {
				TriclopsSiMembers members = queryMksMemberStatus(files, sandbox);
				MKSHelper.checkoutMembers(members, 0);
			} catch (TriclopsException e) {
				errors.add(new VcsException("unable to checkout" + "\n" + getMksErrorMessage()));
			}
		}

	}

	private final EditFileProvider editFileProvider = new _EditFileProvider();

	@Nullable
	public EditFileProvider getEditFileProvider() {
		return editFileProvider;
	}

	private class MyVirtualFileAdapter extends VirtualFileAdapter {
		public MyVirtualFileAdapter() {
			super();	//To change body of overridden methods use File | Settings | File Templates.
			debug("new MyVirtualFileAdapter");
		}

		public void propertyChanged(VirtualFilePropertyEvent event) {
			super.propertyChanged(event);	//To change body of overridden methods use File | Settings | File Templates.
			debug("propertyChanged[" + event + "]");
		}

		public void contentsChanged(VirtualFileEvent virtualFileEvent) {
			super.contentsChanged(virtualFileEvent);	//To change body of overridden methods use File | Settings | File Templates.
			debug("contentsChanged[" + virtualFileEvent + "]");
		}

		public void beforeContentsChange(VirtualFileEvent virtualFileEvent) {
			super.beforeContentsChange(virtualFileEvent);	//To change body of overridden methods use File | Settings | File Templates.
			debug("beforeContentsChange[" + virtualFileEvent + "]");
		}

		public void beforeFileDeletion(VirtualFileEvent virtualFileEvent) {
			super.beforeFileDeletion(virtualFileEvent);	//To change body of overridden methods use File | Settings | File Templates.
			debug("beforeFileDeletion[" + virtualFileEvent + "]");
		}

		public void fileCreated(VirtualFileEvent virtualFileEvent) {
			super.fileCreated(virtualFileEvent);	//To change body of overridden methods use File | Settings | File Templates.
			debug("fileCreated[" + virtualFileEvent + "]");
		}

		public void fileDeleted(VirtualFileEvent virtualFileEvent) {
			super.fileDeleted(virtualFileEvent);	//To change body of overridden methods use File | Settings | File Templates.
			debug("fileDeleted[" + virtualFileEvent + "]");
		}

		public void beforePropertyChange(VirtualFilePropertyEvent event) {
			super.beforePropertyChange(event);	//To change body of overridden methods use File | Settings | File Templates.
			debug("beforePropertyChange[" + event + "]");
			//			if (event.getPropertyName().equalsIgnoreCase(VirtualFile.PROP_NAME)) {
			//				final VirtualFile file = event.getFile();
			//				final VirtualFile parent = file.getParent();
			//				try {
			//					try {
			//						TriclopsSiSandbox sandbox = null;
			//						sandbox = MKSHelper.getSandbox(file);
			//						TriclopsSiMembers members = new TriclopsSiMembers(MksVcs.CLIENT, sandbox);
			//
			//						members.addMember(new TriclopsSiMember(file.getPresentableUrl()));
			//						members.addMember(new TriclopsSiMember(file.getParent().getPresentableUrl()+"\\"+event.getNewValue()));
			//
			//						members.renameMember(TriclopsSiMembers.SI_RENAME_MEMBER_CONFIRM);
			//					} catch (TriclopsException e) {
			//						debug("rename member [" + file.getPath() + "] to [" + event.getNewValue() + "] failed", e);
			//						throw new VcsException("rename member [" + file.getPath() + "] to [" + event.getNewValue() + "] failed");
			//					}
			//				} catch (VcsException e) {
			//					debug(e.getMessage(), e);
			//				}
			//			}
		}

		public void fileMoved(VirtualFileMoveEvent virtualFileMoveEvent) {
			super.fileMoved(virtualFileMoveEvent);	//To change body of overridden methods use File | Settings | File Templates.
			debug("fileMoved[" + virtualFileMoveEvent + "]");
		}

		public void beforeFileMovement(VirtualFileMoveEvent virtualFileMoveEvent) {
			super.beforeFileMovement(virtualFileMoveEvent);
			debug("beforeFileMovement[" + virtualFileMoveEvent + "]");
		}
	}

	public static Map<TriclopsSiSandbox, ArrayList<VirtualFile>> dispatchBySandbox(VirtualFile[] files) {
		ArrayList<VcsException> dispatchErrors = new ArrayList<VcsException>();
		DispatchBySandboxCommand dispatchCommand = new DispatchBySandboxCommand(dispatchErrors, files);
		dispatchCommand.execute();
		return dispatchCommand.filesBySandbox;
	}

	/**
	 * Executable class that fetches mks status for the passed in collection of Virtual Files.
	 * the files may belong to different sandboxes, the action firsts dispatches them by sandbox, then
	 * checks their status
	 */
	public static class CalcStatusComputable implements Computable<Map<VirtualFile, FileStatus>> {
		private MksVcs mksvcs;
		private final Collection<VirtualFile> collection;

		public CalcStatusComputable(MksVcs mksvcs, Collection<VirtualFile> collection) {
			this.mksvcs = mksvcs;
			this.collection = collection;
		}

		public Map<VirtualFile, FileStatus> compute() {
			Map<VirtualFile, FileStatus> ret = new HashMap<VirtualFile, FileStatus>();
			ArrayList<VcsException> dispatchErrors = new ArrayList<VcsException>();
			DispatchBySandboxCommand dispatchCommand = new DispatchBySandboxCommand(dispatchErrors,
				collection.toArray(new VirtualFile[collection.size()]));
			long deb = System.currentTimeMillis();
			dispatchCommand.execute();
			long end = System.currentTimeMillis();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("dispatched " + collection.size() + " in " + (end - deb));
			}
			for (VirtualFile virtualFile : dispatchCommand.notInSandboxFiles) {
				ret.put(virtualFile, FileStatus.UNKNOWN);
			}
			deb = System.currentTimeMillis();
			for (Map.Entry<TriclopsSiSandbox, ArrayList<VirtualFile>> entry : dispatchCommand.filesBySandbox
				.entrySet()) {
				TriclopsSiSandbox sandbox = entry.getKey();
				ArrayList<VirtualFile> files = entry.getValue();

				ArrayList<VcsException> getStatusErrors = new ArrayList<VcsException>();
				GetStatusesCommand getStatusesCommand = new GetStatusesCommand(CalcStatusComputable.this.mksvcs, getStatusErrors, sandbox, files);
				getStatusesCommand.execute();
				ret.putAll(getStatusesCommand.statuses);
			}
			end = System.currentTimeMillis();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("got statuses for " + collection.size() + " in " + (end - deb));
			}

			return ret;
		}
	}

/* todo shelved
	@Nullable
	public CommittedChangesProvider getCommittedChangesProvider() {
		return super.getCommittedChangesProvider();
	}

	@Nullable
	public ChangeProvider getChangeProvider() {
		return new MKSChangeProvider(this);
	}
*/

	/**
	 * pour IDEA 6
	 * /
	 * private class _FileStatusProvider implements FileStatusProvider {
	 * private final Project project;
	 * <p/>
	 * public _FileStatusProvider(Project project) {
	 * this.project = project;
	 * }
	 * <p/>
	 * <p/>
	 * public FileStatus getStatus(VirtualFile virtualFile) {
	 * ArrayList<VirtualFile> collection = new ArrayList<VirtualFile>();
	 * collection.add(virtualFile);
	 * <p/>
	 * Map<VirtualFile, FileStatus> map = ApplicationManager.getApplication().runReadAction(new CalcStatusComputable(MksVcs.this, collection));
	 * FileStatus fileStatus = map.get(virtualFile);
	 * LOGGER.info("status for : " + virtualFile + " = " + fileStatus);
	 * <p/>
	 * return fileStatus;
	 * /*			return fileStatus;
	 * if (!PerforceSettings.getSettings(myProject).ENABLED) return FileStatus.NOT_CHANGED;
	 * if (PerforceManager.getInstance(myProject).isUnderPerforceRoot(virtualFile)) {
	 * if (!virtualFile.isDirectory()) {
	 * final FStat fStat;
	 * try {
	 * final P4File p4File = P4File.create(virtualFile);
	 * fStat = p4File.getFstat(PerforceSettings.getSettings(myProject),
	 * PerforceConnectionManager.getInstance(myProject).getConnectionForFile(virtualFile), false);
	 * }
	 * catch (VcsException e) {
	 * return FileStatus.UNKNOWN;
	 * }
	 * <p/>
	 * return convertToFileStatus(fStat);
	 * }
	 * else {
	 * return FileStatus.NOT_CHANGED;
	 * }
	 * }
	 * else {
	 * return FileStatus.UNKNOWN;
	 * }
	 * /
	 * }
	 * / *
	 * protected Map<VirtualFile, FileStatus> calcStatus(final Collection<VirtualFile> collection) {
	 * return ApplicationManager.getApplication().runReadAction(new CalcStatusComputable(MksVcs.this, collection));
	 * }
	 * /
	 * }
	 */

//    private final FileStatusProvider fileStatusProvider;

//    public FileStatusProvider getFileStatusProvider() {
//        return fileStatusProvider;

	//		return LocalVcsServices.getInstance(myProject).getFileStatusProvider();
//    }
	public void projectOpened() {
		myVirtualFileAdapter = new MyVirtualFileAdapter();
		// todo desactivé
		//		VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileAdapter);
	}

	public void projectClosed() {
		if (myVirtualFileAdapter != null) {
			VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileAdapter);
		}
	}

	FileStatus getIdeaStatus(TriclopsSiMember member, VirtualFile virtualFile) {
		FileStatus status = FileStatus.UNKNOWN;
		if (virtualFile.isDirectory()) {
			status = FileStatus.NOT_CHANGED;
		} else if (member.isStatusKnown() && member.isStatusNotControlled()) {
			status = FileStatus.UNKNOWN;
		} else if (member.isStatusKnown() && member.isStatusControlled()) {
			if (member.isStatusNoWorkingFile()) {
				status = FileStatus.DELETED_FROM_FS;
			} else if (member.isStatusDifferent()) {
				status = FileStatus.MODIFIED;
			} else if (!member.isStatusDifferent()) {
				status = FileStatus.NOT_CHANGED;
			}
		}
		if (MksVcs.DEBUG) {
			debug("status " + member.getPath() + "==" + status);
		}
		return status;
	}


}
