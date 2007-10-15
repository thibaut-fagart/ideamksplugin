package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.vcsUtil.VcsUtil;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.model.MksMemberRevisionInfo;
import org.intellij.vcs.mks.realtime.MksSandboxInfo;
import org.jetbrains.annotations.NonNls;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This command uses tabs to separate fields ...
 */
public class ListMemberRevisionsCommand extends SiCLICommand {
	public static final String COMMAND = "viewhistory";
	private static final String descriptionPattern = "(.*)";
	private static final int REVISION_IDX = 1;
	private static final int DATE_IDX = 2;
	private static final int AUTHOR_IDX = 3;
	private static final int CPID_IDX = 4;
	private static final int DESCRIPTION_IDX = 5;

	private static final String datePattern = "([^\\t+]*)";
	public static final String wholeLinePatternString =
			revisionPattern + "\\t"
					+ datePattern + "\\t"
					+ userPattern + "\\t"
					+ changePackageIdPattern + "\\t"
					+ descriptionPattern;
	private List<MksMemberRevisionInfo> revisionsInfo = new ArrayList<MksMemberRevisionInfo>();
	@NonNls
	private static final String DATE_PATTERN = "MMM dd, yyyy - hh:mm a";
	private final DateFormat format = new SimpleDateFormat(DATE_PATTERN);
	private final String member;

	public ListMemberRevisionsCommand(List<VcsException> errors, EncodingProvider encodingProvider, MksSandboxInfo sandbox, String member) {
		super(errors, encodingProvider, COMMAND,
				"--fields=revision,date,author,cpid,description",
//				"--sandbox=" + sandbox.sandboxPath,
				member);
		this.member = member;
	}

	@Override
	public void execute() {
		try {
			executeCommand();
		} catch (IOException e) {
			//noinspection ThrowableInstanceNeverThrown
			errors.add(new VcsException(e));
			return;
		}

//			System.out.println("pattern [" + wholeLinePatternString + "]");
		Pattern wholeLinePattern = Pattern.compile(wholeLinePatternString);
		BufferedReader reader = new BufferedReader(new StringReader(commandOutput));
		try {
			String firstLine = reader.readLine();
			if (firstLine == null || !VcsUtil.getFilePath(member).equals(VcsUtil.getFilePath(firstLine))) {
				LOGGER.info("unexpected command output " + commandOutput + ", first line is expected to be " + member);
//				throw new VcsException("unexpected command output "+commandOutput+", first line is expected to be "+member);

			} else {
				String line;
				while ((line = reader.readLine()) != null) {
					Matcher matcher = wholeLinePattern.matcher(line);
					try {
						if (matcher.matches()) {
							String rev = matcher.group(REVISION_IDX);
							String date = matcher.group(DATE_IDX);
							String cpid = matcher.group(CPID_IDX);
							String author = matcher.group(AUTHOR_IDX);
							String description = matcher.group(DESCRIPTION_IDX);
							MksMemberRevisionInfo info = new MksMemberRevisionInfo();
							info.setRevision(createRevision(rev));
							info.setDate(parseDate(date));
							info.setCPID(cpid);
							info.setAuthor(author);
							info.setDescription(description);
							revisionsInfo.add(info);
						} else {
							//noinspection ThrowableInstanceNeverThrown
							errors.add(new VcsException("ViewSandbox : unexpected line [" + line + "]"));
						}
					} catch (VcsException e) {
						errors.add(e);
					}
				}
			}
		} catch (IOException e) {
			errors.add(new VcsException(e));
		}
	}

	private Date parseDate(String date) throws VcsException {
//		String temp = "oct. 11, 2007 - 8:34 PM";
		try {
			return format.parse(date);
		} catch (ParseException e) {
			throw new VcsException("unknown date format (expected [" + DATE_PATTERN + "]) " + date);
		}
//		new SimpleDateFormat(MMM dd, yyyy - hh:mm a").format(new Date())
//		throw new UnsupportedOperationException("Method parseDate not yet implemented");
	}

	public List<MksMemberRevisionInfo> getRevisionsInfo() {
		return revisionsInfo;
	}
}
