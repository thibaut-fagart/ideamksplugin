package org.intellij.vcs.mks.realtime;

import java.util.Date;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.sicommands.ListSandboxes;

/**
 * @author Thibaut Fagart
 */
public class SandboxListSynchronizer extends AbstractMKSSynchronizer {
	private static final String LINE_SEPARATOR = " -> ";
	private final SandboxCache sandboxCache;

    public SandboxListSynchronizer(EncodingProvider encodingProvider, SandboxCache sandboxCache) {
		super(ListSandboxes.COMMAND, encodingProvider, "--displaySubs");
		this.sandboxCache = sandboxCache;
	}

	@Override
	protected void handleLine(String line) {
		try {
			if (shoudIgnore(line)) return;
			if (line.startsWith("-----")) {
				// detection of a new update
				LOGGER.info("update notification : "+line);
                sandboxCache.clear();
			} else {
				String[] parts = line.split(LINE_SEPARATOR);
				if (parts.length < 2) {
					LOGGER.error("unexpected command output {" + line + "}, expected 2 parts separated by [" + LINE_SEPARATOR + "]", "");
					// ignoring line
				} else {
					String filePath = parts[0];
//					System.out.println("adding ["+filePath+"]");
					sandboxCache.addSandboxPath(filePath);
				}
			}
		} catch (Exception e) {
			LOGGER.error("error parsing mks synchronizer output ["+line+"], skipping that line",e);
		}
	}
}
