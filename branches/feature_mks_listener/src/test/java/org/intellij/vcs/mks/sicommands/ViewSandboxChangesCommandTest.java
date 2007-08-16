package org.intellij.vcs.mks.sicommands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.model.MksMemberState;
import com.intellij.openapi.vcs.VcsException;
import junit.framework.TestCase;

/**
 * @author Thibaut Fagart
 */
public class ViewSandboxChangesCommandTest extends TestCase {
	private static final String ENCODING = "IBM437";
	private ViewSandboxChangesCommand command;

	protected void setUp() throws Exception {
		super.setUp();
		List<VcsException> errors = new ArrayList<VcsException>();

		String sandboxPath = "c:\\Documents and Settings\\A6253567.HBEU\\sandboxes\\J2EE\\HJF-Core\\project.pj";
		command = createCommand(errors, sandboxPath);
		command.execute();
	}

	public void testModifiedWithoutCheckout() {
		String modifiedWithoutCheckOutFile = "c:\\Documents and Settings\\A6253567.HBEU\\sandboxes\\J2EE\\HJF-Core\\unittestsrc\\log4j.properties";
		MksMemberState memberState = command.getMemberStates().get(modifiedWithoutCheckOutFile);
		assertNotNull("missing state", memberState);
		assertTrue("bad modifiedWithoutCheckout status", memberState.modifiedWithoutCheckout);
		assertFalse("bad checkout status", memberState.checkedout);

	}

	public void testCheckedOutWithChangePackage() {
		String checkedOutFile = "c:\\Documents and Settings\\A6253567.HBEU\\sandboxes\\J2EE\\HJF-Core\\unittestsrc\\com\\hsbc\\hbfr\\ccf\\at\\util\\PerfLogUtilsTestCase.java";

		MksMemberState memberState = command.getMemberStates().get(checkedOutFile);
		assertNotNull("missing state", memberState);
		assertNotNull("missing CP id", memberState.workingChangePackageId);
		assertEquals("bad CP id","2875:1", memberState.workingChangePackageId);
		assertTrue("bad checkedout status", memberState.checkedout);
		assertFalse("bad modifiedWithoutCheckout status", memberState.modifiedWithoutCheckout);

	}

	public void testCheckedOutInAnotherSandboxBySameUser() {
		String checkedOutInAnotherSandboxFile = "c:\\Documents and Settings\\A6253567.HBEU\\sandboxes\\J2EE\\HJF-Core\\pom.xml";

		MksMemberState memberState = command.getMemberStates().get(checkedOutInAnotherSandboxFile);
		assertNotNull("missing state", memberState);
		assertFalse("bad checkedout status", memberState.checkedout);
		assertFalse("bad modifiedWithoutCheckout status", memberState.modifiedWithoutCheckout);
	}

	public void testLocallyDeleted() {
		String locallyDeletedFile = "c:\\Documents and Settings\\A6253567.HBEU\\sandboxes\\J2EE\\HJF-Core\\build\\HJF-Core-debug.jar";

		MksMemberState memberState = command.getMemberStates().get(locallyDeletedFile);
		assertNotNull("missing state", memberState);
		assertFalse("bad checkedout status", memberState.checkedout);
		assertTrue("bad modifiedWithoutCheckout status", memberState.modifiedWithoutCheckout);

	}
	public void testLocallyModifiedAndCheckedOut() {
//        List < VcsException > errors = new ArrayList<VcsException>();
//
//        String sandboxPath = "c:\\Documents and Settings\\A6253567.HBEU\\sandboxes\\J2EE\\HJF-Core\\project.pj";
//        ViewSandboxChangesCommand command = createCommand(errors, sandboxPath);
//        command.execute();

		Map<String, MksMemberState> states = command.getMemberStates();
		for (Map.Entry<String, MksMemberState> entry : states.entrySet()) {
			MksMemberState state = entry.getValue();
			assertNotNull("no member revision", state.memberRevision);
			assertNotNull("no working revision", state.workingRevision);
		}
		assertFalse("errors found", command.foundError());
	}

	private ViewSandboxChangesCommand createCommand(final List<VcsException> errors, final String sandboxPath) {
		EncodingProvider encodingProvider = new EncodingProvider() {
			public String getMksSiEncoding(final String command) {
				return ENCODING;
			}
		};
		return new ViewSandboxChangesCommand(errors, encodingProvider, "e9310750", sandboxPath) {
			@Override
			protected String executeCommand() throws IOException {
				commandOutput = loadResourceWithEncoding("viewsandbox/changedOrLocked.txt", ENCODING);
				return "viewsandbox/changedOrLocked.txt";
			}
		};
	}

	private String loadResourceWithEncoding(final String path, final String encoding) throws IOException {
		URL resource = getClass().getResource(path);
		InputStream inputStream = resource.openStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, encoding));
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		String line = null;
		while ((line = reader.readLine()) != null) {
			pw.println(line);
		}
		pw.flush();
		return sw.toString();
	}

	public void testEncoding() throws IOException {
		for (String encoding : Charset.availableCharsets().keySet()) {
			if (testFileUsing("viewsandbox/sample1.txt", encoding, "Working file 1 082 bytes larger")) {
				System.out.println("encoding " + encoding + " OK");
			}
		}

	}

	private boolean testFileUsing(final String resourceName, final String encoding, final String expected) throws IOException {
		URL testFile = getClass().getResource(resourceName);
		InputStream inputStream = testFile.openStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, encoding));
		try {
			String line = null;
			boolean found = false;
			int i = 1;
			while (!found && (line = reader.readLine()) != null) {
				found = line.contains(expected);
				if (found) {
					System.out.println("line " + i + " contains [" + expected + "]");
				}
				i++;
			}
			return found;
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			}
		}

	}
}