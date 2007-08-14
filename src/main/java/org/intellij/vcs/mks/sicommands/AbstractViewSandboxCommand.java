package org.intellij.vcs.mks.sicommands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.MksMemberState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;

/**
 * @author Thibaut Fagart
 */
public abstract class AbstractViewSandboxCommand extends SiCLICommand {
	private final Logger LOGGER = Logger.getInstance(getClass().getName());

	private static final String COMMAND = "viewsandbox";
	private static final String revisionPattern = "([\\d\\.]+)?";
	private static final String changePackageIdPattern = "([\\d:]+)?";
	private static final String typePattern = "((?:sandbox)|(?:subsandbox)|(?:shared-subsandbox)|(?:shared-variant-subsandbox)|(?:shared-build-subsandbox)|(?:member)|(?:archived)|(?:variant-subsandbox))";
	private static final String unusedPattern = "([^\\s]+)?";
	private static final String namePattern = "(.+)";

	private static final String fieldsParam;
	protected static final String wholeLinePatternString;

	private static final String userPattern = "([^\\s]+)?";

	static {
		// --fields=workingrev,memberrev,workingcpid,deferred,pendingcpid,revsyncdelta,type,wfdelta,name
		fieldsParam = "--fields=workingrev,memberrev,workingcpid"
			+ ",deferred,pendingcpid" //unused
			+ ",type,locker"
			+ ",name";
		wholeLinePatternString = "^" + revisionPattern + " " + revisionPattern + " " + changePackageIdPattern
			+ " " + unusedPattern + " " + unusedPattern
			+ " " + typePattern
			+ " " + userPattern
			+ " " + namePattern + "$";

	}

	private static final int WORKING_REV_GROUP_IDX = 1;
	private static final int MEMBER_REV_GROUP_IDX = 2;
	private static final int WORKING_CPID_GROUP_IDX = 3;
	private static final int TYPE_GROUP_IDX = 6;
	private static final int LOCKER_GROUP_IDX = 7;
	private static final int NAME_GROUP_IDX = 8;
	protected final Map<String, MksMemberState> memberStates = new HashMap<String, MksMemberState>();
	protected final String mksUsername;

	protected AbstractViewSandboxCommand(final List<VcsException> errors, final EncodingProvider encodingProvider,
	                                     String mksUsername, final String filter, final String sandboxPath) {
		super(errors, encodingProvider, COMMAND, fieldsParam, "--recurse", filter, sandboxPath);
		this.mksUsername = mksUsername;
	}

	public void execute() {
		try {
			executeCommand();
		} catch (IOException e) {
			errors.add(new VcsException(e));
		}
//			System.out.println("pattern [" + wholeLinePatternString + "]");
		Pattern wholeLinePattern = Pattern.compile(wholeLinePatternString);
		BufferedReader reader = new BufferedReader(new StringReader(commandOutput));
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				try {
					Matcher matcher = wholeLinePattern.matcher(line);
					if (matcher.matches()) {
						String workingRev = matcher.group(WORKING_REV_GROUP_IDX);
						String memberRev = matcher.group(MEMBER_REV_GROUP_IDX);
						String workingCpid = matcher.group(WORKING_CPID_GROUP_IDX);
						String type = matcher.group(TYPE_GROUP_IDX);
						String locker = matcher.group(LOCKER_GROUP_IDX);
						String name = matcher.group(NAME_GROUP_IDX);
						if (isMember(type)) {
//							boolean isCheckedOut = mksUsername.equals(locker);
							MksMemberState memberState = null;
							memberState = createState(workingRev, memberRev, workingCpid, locker);
							setState(name, memberState);
						} else if (isSandbox(type)) {
							LOGGER.debug("ignoring sandbox " + name);
						} else {
							LOGGER.warn("unexpected type " + type + " for " + line);
						}
					} else {
						errors.add(new VcsException("unexpected line [" + line + "]"));
					}
				} catch (VcsException e) {
					// should not happen, VcsExceptions on ChangePackageId
					errors.add(new VcsException(e));

				}
			}
		} catch (IOException e) {
			// shouldnt happen :IO exceptions on stringReader.read
			errors.add(new VcsException(e));
		}

	}

	protected abstract MksMemberState createState(String workingRev, String memberRev, String workingCpid, final String locker) throws VcsException;

	private boolean isSandbox(final String type) {
		return type.contains("sandbox");
	}

	private void setState(final String name, final MksMemberState memberState) {
		memberStates.put(name, memberState);
	}

	private boolean isMember(final String type) {
		return "member".equals(type) || "archived".equals(type);
	}

	public Map<String, MksMemberState> getMemberStates() {
		return Collections.unmodifiableMap(memberStates);
	}

}
