package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.vcs.VcsException;
import org.intellij.vcs.mks.AbstractMKSCommand;
import org.intellij.vcs.mks.MksVcs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.List;

/**
 * @author Thibaut Fagart
 */
public abstract class SiCLICommand extends AbstractMKSCommand {
	private MksVcs mksvcs;
	private String command;
	private String[] args;
	protected String commandOutput;

	public SiCLICommand(List<VcsException> errors, MksVcs mksvcs, String command, String... args) {
		super(errors);
		this.mksvcs = mksvcs;
		this.command = command;
		this.args = args;
	}

	protected void executeCommand() throws IOException {
		String[] processArgs = new String[args.length + 2];
		processArgs[0] = "si";
		processArgs[1] = command;
		System.arraycopy(args, 0, processArgs, 2, args.length);
		ProcessBuilder builder = new ProcessBuilder(processArgs);
		LOGGER.debug("command " + builder.command());
		builder.redirectErrorStream(true);
		Process process = builder.start();
		InputStream is = process.getInputStream();
		InputStreamReader reader = new InputStreamReader(is, mksvcs.getMksSiEncoding(command));
		StringWriter sw;
		try {
			char[] buffer = new char[512];
			int readChars;
			sw = new StringWriter();
			while ((readChars = reader.read(buffer)) != -1) {
				sw.write(new String(buffer, 0, readChars));
			}
		} finally {
			reader.close();
		}
		commandOutput = sw.toString();

	}

	protected boolean shoudIgnore(String line) {
		return line.startsWith("Reconnecting") || line.startsWith("Connecting");
	}
}
