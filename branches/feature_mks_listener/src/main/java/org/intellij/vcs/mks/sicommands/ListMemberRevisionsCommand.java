package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.vcs.VcsException;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.realtime.MksSandboxInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListMemberRevisionsCommand extends SiCLICommand {
	public static final String COMMAND = "viewhistory";
	private static final String descriptionPattern = "(.*)";
	private static final int REVISION_IDX = 1;
	private static final int DATE_IDX = 2;
	private static final int AUTHOR_IDX = 3;
	private static final int CPID_IDX = 4;
	private static final int DESCRIPTION_IDX = 4;

	private static final String datePattern = "([^\\s+]*)";
	public static final String wholeLinePatternString =
			revisionPattern + " "
					+ datePattern + " "
					+ userPattern + " "
					+ changePackageIdPattern + " "
					+ descriptionPattern;

	public ListMemberRevisionsCommand(List<VcsException> errors, EncodingProvider encodingProvider, MksSandboxInfo sandbox, String member) {
		super(errors, encodingProvider, COMMAND,
				"--fields=revision,date,author,cpid,description",
				"--sandbox=" + sandbox.sandboxPath);
	}

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
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				Matcher matcher = wholeLinePattern.matcher(line);
				if (matcher.matches()) {
					String rev = matcher.group(REVISION_IDX);
					String date = matcher.group(DATE_IDX);
					String cpid = matcher.group(CPID_IDX);
					String author = matcher.group(AUTHOR_IDX);
					String description = matcher.group(DESCRIPTION_IDX);
				} else {
					//noinspection ThrowableInstanceNeverThrown
					errors.add(new VcsException("ViewSandbox : unexpected line [" + line + "]"));
				}


			}
		} catch (IOException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
		}
	}
}
