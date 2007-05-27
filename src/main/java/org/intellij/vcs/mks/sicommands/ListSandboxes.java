package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.vcs.VcsException;
import mks.integrations.common.TriclopsException;
import mks.integrations.common.TriclopsSiSandbox;
import org.intellij.vcs.mks.MKSHelper;
import org.intellij.vcs.mks.MksVcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Thibaut Fagart
 */
public class ListSandboxes extends SiCLICommand {
    private static final String LINE_SEPARATOR = " -> ";
    public ArrayList<TriclopsSiSandbox> sandboxes;

    public ListSandboxes(List<VcsException> errors, MksVcs mksvcs) {
        super(errors, mksvcs, "sandboxes", "--displaySubs");
    }

    @Override
    public void execute() {
        ArrayList<TriclopsSiSandbox> tempSandboxes = new ArrayList<TriclopsSiSandbox>();
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
                String[] parts = line.split(LINE_SEPARATOR);
                if (parts.length < 2) {
                    LOGGER.error("unexpected command output {" + line + "}, expected 2 parts separated by [" + LINE_SEPARATOR + "]", "");
                    //noinspection ThrowableInstanceNeverThrown
                    errors.add(new VcsException("unexpected line structure " + line));
                } else {
                    try {
                        TriclopsSiSandbox sandbox = MKSHelper.createSandbox(parts[0]);
                        tempSandboxes.add(sandbox);
                    } catch (TriclopsException e) {
                        //noinspection ThrowableInstanceNeverThrown
                        errors.add(new VcsException("error validating sandbox " + parts[0]));
                    }
                }
            }
            sandboxes = tempSandboxes;
        } catch (IOException e) {
            //noinspection ThrowableInstanceNeverThrown
            errors.add(new VcsException(e));
        }

    }
}
