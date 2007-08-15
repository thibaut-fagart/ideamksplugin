package org.intellij.vcs.mks.realtime;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.sicommands.ListSandboxes;

/**
 * @author Thibaut Fagart
 */
public class SandboxListSynchronizer extends AbstractMKSSynchronizer {
	private static final String LINE_SEPARATOR = " -> ";
	private static final String patternString = "(.+) -> (.+) \\((.+)\\)";
	private final SandboxCache sandboxCache;
	private final Pattern pattern;

	public SandboxListSynchronizer(EncodingProvider encodingProvider, SandboxCache sandboxCache) {
		super(ListSandboxes.COMMAND, encodingProvider, "--displaySubs");
		this.sandboxCache = sandboxCache;
		pattern = Pattern.compile(patternString);
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
				Matcher matcher = pattern.matcher(line);
//				String[] parts = line.split(LINE_SEPARATOR);
				if (!matcher.matches()) {
					LOGGER.error("unexpected command output {" + line + "}, expected 2 parts separated by [" + LINE_SEPARATOR + "]", "");
					// ignoring line
				} else {
					String sandboxPath = matcher.group(1);
					String projectPath = matcher.group(2);
					String serverHostAndPort = matcher.group(3);
//					System.out.println("adding ["+filePath+"]");
					sandboxCache.addSandboxPath(sandboxPath, serverHostAndPort);
				}
			}
		} catch (Exception e) {
			LOGGER.error("error parsing mks synchronizer output ["+line+"], skipping that line",e);
		}
	}

	public String getDescription() {
		return "sandbox list listnener";
	}
}
