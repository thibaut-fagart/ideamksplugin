package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.intellij.vcs.mks.AbstractMKSCommand;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.MksRevisionNumber;
import org.intellij.vcs.mks.io.AsyncStreamBuffer;
import org.intellij.vcs.mks.model.MksMemberState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Date;
import java.util.List;

/**
 * @author Thibaut Fagart
 */
public abstract class SiCLICommand extends AbstractMKSCommand implements Runnable {
	protected final EncodingProvider encodingProvider;
	private String command;
	private String[] args;
	protected String commandOutput;
	private File workingDir;
	protected static final String DEFERRED_ADD = "deferred-add";
	protected static final String DEFERRED_DROP = "deferred-drop";
	protected static final String revisionPattern = "(\\d+(?:\\.\\d+)*)?";
	protected static final String changePackageIdPattern = "(\\d+:\\d+)?";
	protected static final String deferredPattern = "(deferred)?";
	protected static final String unusedPattern = "([^\\s]+)?";
	protected static final String namePattern = "(.+)";
	protected static final String sandboxPattern = namePattern + "?";
	protected static final String userPattern = "([^\\s]+)?";
	private String commandString;
	protected static final String DEFERRED = "deferred";

	public SiCLICommand(@NotNull List<VcsException> errors, @NotNull EncodingProvider encodingProvider, @NotNull String command, String... args) {
		super(errors);
		this.encodingProvider = encodingProvider;
		this.command = command;
		this.args = args;
	}

	public void setWorkingDir(File aDir) {
		workingDir = aDir;
	}

	protected String executeCommand() throws IOException {
		String[] processArgs = new String[args.length + 3];
		processArgs[0] = "si";
		processArgs[1] = command;
		processArgs[2] = "--batch";
		System.arraycopy(args, 0, processArgs, 3, args.length);
		ProcessBuilder builder = new ProcessBuilder(processArgs);
		if (workingDir != null) {
			builder.directory(workingDir);
		}
		StringBuffer buf = new StringBuffer();
		for (String s : builder.command()) {
			boolean surroundWithQuotes = s.indexOf(' ') >= 0;
			if (surroundWithQuotes) {
				buf.append("\"");
			}
			buf.append(s);
			if (surroundWithQuotes) {
				buf.append("\"");
			}
			buf.append(" ");
		}
		long start = System.currentTimeMillis();
		commandString = buf.toString();
		LOGGER.warn("executing " + buf.toString());
		builder.redirectErrorStream(false);
		Process process = builder.start();
		AsyncStreamBuffer stderr =
				new AsyncStreamBuffer(process.getErrorStream());

		InputStream is = process.getInputStream();
		Reader reader = new BufferedReader(new InputStreamReader(is, encodingProvider.getMksSiEncoding(command)));
		StringWriter sw;
		try {
			char[] buffer = new char[2048];
			int readChars;
			sw = new StringWriter();
			while ((readChars = reader.read(buffer)) != -1) {
				sw.write(new String(buffer, 0, readChars));
			}
		} finally {
			reader.close();
			try {
				final int exitValue = process.exitValue();
				final String errorOutput = new String(stderr.get(), encodingProvider.getMksSiEncoding(command));
				if (!"".equals(errorOutput)) {
					if (exitValue == 0) {
						LOGGER.warn("command [" + this + "] wrote to stderr " + errorOutput);
					} else {
						LOGGER.error("return code " + exitValue + " for command " + this
								+ ", stdErr=" + errorOutput);
					}
				}
			} catch (IllegalThreadStateException e) {
				process.destroy();
			}
			LOGGER.debug(toString() + " finished in " + (System.currentTimeMillis() - start + " ms"));
		}
		commandOutput = sw.toString();
		return buf.toString();
	}

	protected boolean shoudIgnore(String line) {
		return line.startsWith("Reconnecting") || line.startsWith("Connecting");
	}

	public void run() {
		execute();
	}

	/**
	 * @param revision the revision number as obtained from the MKS CLI
	 * @return VcsRevisionNumber.NULL if no revision is applicable or a valid
	 *         MksRevisionNumber
	 * @throws com.intellij.openapi.vcs.VcsException
	 *          if the revision doesn't follow MKS conventions \d+(\.\d+)*
	 */
	@Nullable
	protected VcsRevisionNumber createRevision(final String revision) throws VcsException {
		return MksRevisionNumber.createRevision(revision);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + commandString + "]";
	}

	protected boolean isDeferred(String deferred) {
		return DEFERRED.equals(deferred);
	}

	protected MksMemberState createDeferredState(String workingRev, String memberRev, String workingCpid, String type) throws VcsException {
		return createDeferredState(workingRev, memberRev, workingCpid, type, null);
	}

	protected MksMemberState createDeferredState(String workingRev, String memberRev, String workingCpid, String type, Date memberTimestamp) throws VcsException {
		if (DEFERRED_ADD.equals(type)) {
			return new MksMemberState((MksRevisionNumber.createRevision(workingRev)), null, workingCpid,
					MksMemberState.Status.ADDED, memberTimestamp);
		} else if (DEFERRED_DROP.equals(type)) {
			return new MksMemberState(null, (MksRevisionNumber.createRevision(memberRev)), workingCpid,
					MksMemberState.Status.DROPPED, memberTimestamp);
		} else /*if (DEFERRED_CHECKIN.equals(type)) */ {
			LOGGER.warn(this + " : deferred operation (" + type + ") not supported at moment, returning 'unknown'");
			return new MksMemberState((MksRevisionNumber.createRevision(workingRev)), (MksRevisionNumber.createRevision(memberRev)), workingCpid,
					MksMemberState.Status.UNKNOWN, memberTimestamp);
		}
	}
}
