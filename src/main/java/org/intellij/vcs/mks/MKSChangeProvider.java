package org.intellij.vcs.mks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.vcsUtil.VcsUtil;
import mks.integrations.common.TriclopsSiMember;
import mks.integrations.common.TriclopsSiSandbox;
import org.intellij.vcs.mks.sicommands.ListChangePackageEntries;
import org.intellij.vcs.mks.sicommands.ListChangePackages;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Thibaut Fagart
 */
class MKSChangeProvider implements ChangeProvider {
	private final Logger LOGGER = Logger.getInstance(getClass().getName());
	private final MksVcs mksvcs;

	public MKSChangeProvider(MksVcs mksvcs) {
		this.mksvcs = mksvcs;
	}

	public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress) throws VcsException {
		ArrayList<VcsException> errors = new ArrayList<VcsException>();
		try {
//			System.out.println("dirtyScope " + dirtyScope);
//			System.out.println("getDirtyFiles " + dirtyScope.getDirtyFiles());
//			System.out.println("getAffectedContentRoots " + dirtyScope.getAffectedContentRoots());
//			System.out.println("getRecursivelyDirtyDirectories " + dirtyScope.getRecursivelyDirtyDirectories());
			// todo dispatch changes by change package
			// need to find, for each changepackageentry,the associated file => we only have its project (.pj) => need to find the sandbox
			ListChangePackages listCpsAction = new ListChangePackages(errors, mksvcs);
			listCpsAction.execute();
			for (final MksChangePackage changePackage : listCpsAction.changePackages) {
				ListChangePackageEntries listEntries = new ListChangePackageEntries(errors, mksvcs, changePackage);
				listEntries.execute();
				changePackage.setEntries(listEntries.changePackageEntries);
			}
			List<MksChangePackage> changePackages = listCpsAction.changePackages;
			if (progress != null) {
				progress.setIndeterminate(true);
				progress.setText("Collecting files to query ...");
			}
			final Collection<VirtualFile> filesTocheck = new ArrayList<VirtualFile>();

			dirtyScope.iterate(new Processor<FilePath>() {
				public boolean process(FilePath filePath) {
					filesTocheck.add(filePath.getVirtualFile());
					return true;
				}
			});
			if (progress != null) {
				progress.setText("Dispatching files by sandbox");
			}
			Map<TriclopsSiSandbox, ArrayList<VirtualFile>> filesBySandbox = MksVcs.dispatchBySandbox(filesTocheck.toArray(new VirtualFile[filesTocheck.size()]));

			int numberOfFilesToProcess = getNumberOfFiles(filesBySandbox);
			int numberOfProcessedFiles = 0;
			if (progress != null) {
				progress.setFraction(0);
				progress.setText("Querying status");
			}
			for (Map.Entry<TriclopsSiSandbox, ArrayList<VirtualFile>> entry : filesBySandbox.entrySet()) {
				TriclopsSiSandbox sandbox = entry.getKey();
				ArrayList<VirtualFile> sandboxFiles = entry.getValue();
				MksQueryMemberStatusCommand command = new MksQueryMemberStatusCommand(errors, sandbox, sandboxFiles);
				command.execute();
				for (int i = 0, max = command.triclopsSiMembers.getNumMembers(); i < max; i++) {

					TriclopsSiMember triclopsSiMember = command.triclopsSiMembers.getMember(i);
					processMember(sandbox, triclopsSiMember, sandboxFiles.get(i), builder, changePackages);
					if (progress != null) {
						progress.setFraction(((double) numberOfProcessedFiles++) / numberOfFilesToProcess);
					}
				}
			}
			// todo : status unknown for directories with no known children
		} catch (RuntimeException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			throw e;
		}
		if (!errors.isEmpty()) {
			mksvcs.showErrors(errors, "ChangeProvider");
		}


	}

	private void processMember(TriclopsSiSandbox sandbox, TriclopsSiMember triclopsSiMember, VirtualFile virtualFile, ChangelistBuilder builder, List<MksChangePackage> changePackages) throws VcsException {
		FilePath filePath = VcsUtil.getFilePath(virtualFile.getPath());
		if (virtualFile.isDirectory()) {
			// todo  status = FileStatus.NOT_CHANGED;
		} else if (triclopsSiMember.isStatusKnown() && triclopsSiMember.isStatusNotControlled()) {
			LOGGER.debug("UNKNOWN " + virtualFile);
			builder.processUnversionedFile(virtualFile);
//				status = FileStatus.UNKNOWN;
		} else if (triclopsSiMember.isStatusKnown() && triclopsSiMember.isStatusControlled()) {
			MksChangePackage changePackage = findChangePackage(filePath, triclopsSiMember, sandbox, changePackages);
			if (triclopsSiMember.isStatusNoWorkingFile()) {
				// todo : find the change packages for each change
				LOGGER.debug("LOCALLY DELETED " + virtualFile);
				builder.processLocallyDeletedFile(filePath);
//					status = FileStatus.DELETED_FROM_FS;
			} else if (triclopsSiMember.isStatusDifferent()) {
				LOGGER.debug("MODIFIED " + virtualFile);
				// todo : find the change packages for each change
				Change change = new Change(new MksContentRevision(mksvcs, filePath, getRevision(triclopsSiMember)), CurrentContentRevision.create(filePath), getStatus(triclopsSiMember, virtualFile));
				if (changePackage != null) {
					ChangeListManager changeListManager = ChangeListManager.getInstance(mksvcs.getProject());
					LocalChangeList changeList = changeListManager.findChangeList(createChangeListName(changePackage));
					if (changeList == null) {
						changeList = changeListManager.addChangeList(createChangeListName(changePackage), "");
					}
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

	private String createChangeListName(MksChangePackage changePackage) {
		return "MKS CP " + changePackage.getId() + "," + changePackage.getDescription();
	}

	private MksChangePackage findChangePackage(FilePath filePath, TriclopsSiMember siMember, TriclopsSiSandbox sandbox, List<MksChangePackage> changeLists) throws VcsException {
		String sandboxPath = sandbox.getSandboxProject();
		String mksRevision = getRevision(siMember).asString();
		File filePathFile = filePath.getIOFile();
		ArrayList<MksChangePackage> candidates = new ArrayList<MksChangePackage>();
		for (MksChangePackage changeList : changeLists) {
			for (MksChangePackageEntry changePackageEntry : changeList.getEntries()) {
//				System.out.println(">"+ sandboxPath);
//				System.out.println("<"+ changePackageEntry.getProject());
				if (sandboxPath.equals(changePackageEntry.getProject())) {
					File memberFile = new File(sandbox.getPath().substring(0, sandbox.getPath().lastIndexOf("project.pj")), changePackageEntry.getMember());
					if (memberFile.equals(filePathFile)) {
//						System.out.println("found a candidate, checking states and revisions (member.revision=" + mksRevision + ",cpRevision=" + changePackageEntry.getRevision() + ")");
						if (changePackageEntry.isLocked() && changePackageEntry.getRevision().equals(mksRevision)) {
							candidates.add(changeList);
						}
					}
				}
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}
		if (candidates.size() > 1) {
			throw new VcsException("MORE THAN ONE CHANGELIST FOR A CHANGE " + filePath + ", " + candidates);
		}
		MksChangePackage changeList = candidates.get(0);
//		System.out.println("found changelist : " + changeList + " for " + filePath);
		return changeList;
	}

	private FileStatus getStatus(TriclopsSiMember triclopsSiMember, VirtualFile virtualFile) {
		return mksvcs.getIdeaStatus(triclopsSiMember, virtualFile);
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
}
