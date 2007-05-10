package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.vcs.VcsException;
import org.intellij.vcs.mks.MksChangePackage;
import org.intellij.vcs.mks.MksChangePackageEntry;
import org.intellij.vcs.mks.MksVcs;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Thibaut Fagart
 */
public class ListChangePackageEntries extends SiCLICommand {
	public List<MksChangePackageEntry> changePackageEntries;
	private MksChangePackage mksChangePackage;
	@NonNls
	public static final String COMMAND = "viewcp";

	public ListChangePackageEntries(List<VcsException> errors, MksVcs mksvcs, MksChangePackage mksChangePackage) {
		super(errors, mksvcs, COMMAND, mksChangePackage.getId());
		this.mksChangePackage = mksChangePackage;

	}

	@Override
	public void execute() {
		ArrayList<MksChangePackageEntry> tempEntries = new ArrayList<MksChangePackageEntry>();
		try {
			super.executeCommand();
			String[] lines = commandOutput.split("\n");
//			System.out.println("skipping first 2 lines ");
//			System.out.println(lines[0]);
//			System.out.println(lines[1]);
			int start = 0;
			while (shoudIgnore(lines[start])) {
				// skipping connecting/reconnecting lines
				start++;
			}
			// skipping the first 2 lines which display change package information
			String[] strings = lines[start].split("\t");
			if (strings.length != 2 || !mksChangePackage.getId().trim().equals(strings[0].trim())
				|| !mksChangePackage.getDescription().trim().equals(strings[1].trim())) {
				LOGGER.error("unexpected si viewcp output {" + commandOutput + "}, expected line " + start + "{" + lines[start] + "} to be " +
					"{" + mksChangePackage.getId() + "\t" + mksChangePackage.getDescription() + "\t}");
				errors.add(new VcsException("unexpected si viewcp output"));
				return;
			}
			strings = lines[start + 1].split("\t");
			if (strings.length != 3 || !mksChangePackage.getOwner().trim().equals(strings[0].trim())
				|| !mksChangePackage.getState().trim().equals(strings[2].trim())) {
				LOGGER.error("unexpected si viewcp output {" + commandOutput + "}, expected line " + (start + 1) + "{" + lines[start + 1] + "} to be" +
					"{" + mksChangePackage.getOwner() + "\tmksCP.creationDate\t" + mksChangePackage.getState() + "}");
				errors.add(new VcsException("unexpected si viewcp output"));
				return;
			}
			// line 2 : owner, create date, state
			for (int i = start + 2; i < lines.length; i++) {
				String line = lines[i];
				if (!shoudIgnore(line)) {
					String[] parts = line.split("\t");
//				System.out.println("ListChangePackageEntries " + Arrays.asList(parts));
					tempEntries.add(new MksChangePackageEntry(parts[0], parts[2], parts[3], parts[4]));
				}
			}
			changePackageEntries = tempEntries;
		} catch (IOException e) {
			errors.add(new VcsException(e));
		}
	}


}
