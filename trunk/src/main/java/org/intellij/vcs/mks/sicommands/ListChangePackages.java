package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import org.intellij.vcs.mks.MksChangePackage;
import org.intellij.vcs.mks.MksVcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Thibaut Fagart
 */
public class ListChangePackages extends SiCLICommand {
	public List<MksChangePackage> changePackages;
	private final Logger LOGGER = Logger.getInstance(getClass().getName());

	public ListChangePackages(List<VcsException> errors, MksVcs mksvcs) {
		super(errors, mksvcs, "viewcps");
	}

	public void execute() {
		ArrayList<MksChangePackage> tempChangePackages = new ArrayList<MksChangePackage>();
		try {
			executeCommand();
			String[] lines = commandOutput.split("\n");
			int start = 0;
			while (shoudIgnore(lines[start])) {
				// skipping connecting/reconnecting lines
				start++;
			}
			for (int i = start, max = lines.length; i < max; i++) {
				String line = lines[i];
				String[] parts = line.split("\t");
				if (parts.length < 4) {
					LOGGER.error("unexepcted command output {" + line + "}, expected 4 parts separated by \\t", "");
					errors.add(new VcsException("unexpected line structure " + line));
				} else {
					tempChangePackages.add(new MksChangePackage(parts[0], parts[1], parts[2], parts[3]));
				}
			}
			changePackages = tempChangePackages;
		} catch (IOException e) {
			errors.add(new VcsException(e));
		}
	}
}
