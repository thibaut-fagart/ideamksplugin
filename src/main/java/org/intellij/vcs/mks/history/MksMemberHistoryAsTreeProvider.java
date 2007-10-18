package org.intellij.vcs.mks.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.HistoryAsTreeProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.util.TreeItem;
import org.intellij.vcs.mks.MksRevisionNumber;

import java.util.*;

public class MksMemberHistoryAsTreeProvider implements HistoryAsTreeProvider {
	private final Logger LOGGER = Logger.getInstance(getClass().getName());

	public MksMemberHistoryAsTreeProvider() {
	}

	/**
	 * It seems we should only return roots
	 *
	 * @param allRevisions all the revisions to be viewed as a tree (will become node/treeitems)
	 * @return a list of the roots, usually the only root (aka revision 1.1)
	 */
	public List<TreeItem<VcsFileRevision>> createTreeOn(List<VcsFileRevision> allRevisions) {
		Map<MksRevisionNumber, TreeItem<VcsFileRevision>> treeItemMap = new HashMap<MksRevisionNumber, TreeItem<VcsFileRevision>>();
		Map<MksRevisionNumber, MksVcsFileRevision> revisionsByRevNumber = new HashMap<MksRevisionNumber, MksVcsFileRevision>();
		for (VcsFileRevision revision : allRevisions) {
			MksVcsFileRevision mksRevision = (MksVcsFileRevision) revision;
			revisionsByRevNumber.put((MksRevisionNumber) mksRevision.getRevisionNumber(), mksRevision);
		}
		// first order the revisions, so we can simply process the list and be sure
		// parent revisions have been processed first
		List<MksRevisionNumber> orderedRevisions = new ArrayList<MksRevisionNumber>(revisionsByRevNumber.keySet());
		List<TreeItem<VcsFileRevision>> result = new ArrayList<TreeItem<VcsFileRevision>>(orderedRevisions.size());
		Collections.sort(orderedRevisions);

		for (MksRevisionNumber revisionNumber : orderedRevisions) {
			MksVcsFileRevision revision = revisionsByRevNumber.get(revisionNumber);
			TreeItem<VcsFileRevision> treeItem = new TreeItem<VcsFileRevision>(revision);
			treeItemMap.put(revisionNumber, treeItem);
			result.add(treeItem);
			// now look for parents and set parent/child relationships
			String parentRevString = ((MksRevisionNumber) revision.getRevisionNumber()).getParentRevision();

			try {
				if (parentRevString != null && !"".equals(parentRevString)) {
					final TreeItem<VcsFileRevision> parentItem;
					parentItem = treeItemMap.get(new MksRevisionNumber(parentRevString));
					if (parentItem == null) {
						LOGGER.warn("missing parent revision " + parentRevString + " for " + revision.getMyFile() + " !! ");
					} else {
//					parentItem.addChild(treeItem);
						// we want to keep newer revisions on top, thus reverse the order
						parentItem.addChild(treeItem);
//						treeItem.addChild(parentItem);
						// remove children so they don't appear multiple times
						if (result.contains(treeItem)) {
							result.remove(treeItem);
						}
					}
				}
			} catch (VcsException e) {

				LOGGER.error("should not happen", e);
			} catch (RuntimeException e) {
				LOGGER.error(e);
				throw e;
			}


		}
		return result;

	}

}