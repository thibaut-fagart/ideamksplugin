package org.intellij.vcs.mks.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.history.HistoryAsTreeProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.util.TreeItem;
import org.intellij.vcs.mks.MksRevisionNumber;

import java.util.*;

public class MksMemberHistoryAsTreeProvider implements HistoryAsTreeProvider {
	private final Logger LOGGER = Logger.getInstance(getClass().getName());

	public MksMemberHistoryAsTreeProvider() {
	}

	public List<TreeItem<VcsFileRevision>> createTreeOn(List<VcsFileRevision> allRevisions) {
		Map<String, TreeItem<VcsFileRevision>> treeItemMap = new HashMap<String, TreeItem<VcsFileRevision>>();
		Map<String, MksVcsFileRevision> revisionsByRevNumber = new HashMap<String, MksVcsFileRevision>();
		for (VcsFileRevision revision : allRevisions) {
			MksVcsFileRevision mksRevision = (MksVcsFileRevision) revision;
			final String revString = mksRevision.getRevisionNumber().asString();
			revisionsByRevNumber.put(revString, mksRevision);
		}
		// first order the revisions, so we can simply process the list and be sure
		// parent revisions have been processed first
		List<String> orderedRevisions = new ArrayList<String>(revisionsByRevNumber.keySet());
		List<TreeItem<VcsFileRevision>> result = new ArrayList<TreeItem<VcsFileRevision>>(orderedRevisions.size());
		Collections.sort(orderedRevisions);

		for (String revString : orderedRevisions) {
			MksVcsFileRevision revision = revisionsByRevNumber.get(revString);
			TreeItem<VcsFileRevision> treeItem = new TreeItem<VcsFileRevision>(revision);
			treeItemMap.put(revString, treeItem);
			result.add(treeItem);
			// now look for parents and set parent/child relationships
			String parentRevString = ((MksRevisionNumber) revision.getRevisionNumber()).getParentRevision();
			if (parentRevString != null) {
				final TreeItem<VcsFileRevision> parentItem = treeItemMap.get(parentRevString);
				if (parentItem == null) {
					LOGGER.warn("missing parent revision " + parentRevString + " for " + revision.getMyFile() + " !! ");
				} else {
					parentItem.addChild(treeItem);
				}
			}

		}
		return result;

	}

}
