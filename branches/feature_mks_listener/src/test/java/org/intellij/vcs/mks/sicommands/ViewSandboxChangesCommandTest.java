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
import org.intellij.vcs.mks.MksMemberState;
import com.intellij.openapi.vcs.VcsException;
import junit.framework.TestCase;

/**
 * @author Thibaut Fagart
 */
public class ViewSandboxChangesCommandTest extends TestCase {
    private static final String ENCODING = "IBM437";

    public void testLocallyModifiedAndCheckedOut() {
        List < VcsException > errors = new ArrayList<VcsException>();

        String sandboxPath = null;
        ViewSandboxChangesCommand command = createCommand(errors, sandboxPath);
        command.execute();
        Map<String, MksMemberState> states = command.getMemberStates();
        for (Map.Entry<String, MksMemberState> entry : states.entrySet()) {
            MksMemberState state = entry.getValue();
            assertTrue("should only see modified files", state.modifiedWithoutCheckout || state.checkedout);
            assertNotNull("no member revision", state.memberRevision);
            assertNotNull("no working revision", state.workingRevision);
        }
        assertTrue("errors found", errors.isEmpty());
        String checkedOutFile = "c:\\Documents and Settings\\A6253567.HBEU\\sandboxes\\J2EE\\HJF-Core\\unittestsrc\\com\\hsbc\\hbfr\\ccf\\at\\util\\PerfLogUtilsTestCase.java";
        String modifiedWithoutCheckOutFile = "c:\\Documents and Settings\\A6253567.HBEU\\sandboxes\\J2EE\\HJF-Core\\unittestsrc\\log4j.properties";
        assertTrue("missing checked out file",states.get(checkedOutFile).checkedout);
        assertTrue("missing modified without checkout file", states.get(modifiedWithoutCheckOutFile).modifiedWithoutCheckout);
        assertFalse("modified without checkout viewed as checkout", states.get(modifiedWithoutCheckOutFile).checkedout);
        assertNotNull(states.get(checkedOutFile).workingChangePackageId);
        assertEquals("2875:1", states.get(checkedOutFile).workingChangePackageId);
    }

    private ViewSandboxChangesCommand createCommand(final List<VcsException> errors, final String sandboxPath) {
        EncodingProvider encodingProvider= new EncodingProvider() {
            public String getMksSiEncoding(final String command) {
                return ENCODING;
            }
        };
	    return new ViewSandboxChangesCommand(errors, encodingProvider, "e9310750",sandboxPath) {
	        @Override
	        protected String executeCommand() throws IOException {
	            commandOutput = loadResourceWithEncoding("viewsandbox/sample2.txt", ENCODING);
	            return "viewsandbox/sample2.txt";
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
        while ((line = reader.readLine())!= null) {
            pw.println(line);
        }
        pw.flush();
        return sw.toString();
    }

    public void testEncoding() throws IOException {
        for (String encoding : Charset.availableCharsets().keySet()) {
            if (testFileUsing("viewsandbox/sample1.txt",encoding,"Working file 1 082 bytes larger")) {
                System.out.println("encoding "+encoding +" OK");
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
            while(!found && (line = reader.readLine())!= null) {
                found = line.contains(expected);
                if (found) {
                    System.out.println("line "+i +" contains ["+expected+"]");
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