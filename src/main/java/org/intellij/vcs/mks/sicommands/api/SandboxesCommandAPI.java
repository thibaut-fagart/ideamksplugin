package org.intellij.vcs.mks.sicommands.api;

import com.intellij.openapi.vcs.VcsException;
import com.mks.api.Command;
import com.mks.api.response.*;
import org.intellij.vcs.mks.MksCLIConfiguration;
import org.intellij.vcs.mks.sicommands.SandboxInfo;
import org.jetbrains.annotations.NotNull;

import java.io.StringWriter;
import java.util.*;

/**
 * Returns a list of all sandboxes registered on the system, including subsandboxes
 */
public class SandboxesCommandAPI extends SiAPICommand {
    public final ArrayList<SandboxInfo> result = new ArrayList<SandboxInfo>();

    public SandboxesCommandAPI(@NotNull List<VcsException> errors, @NotNull MksCLIConfiguration mksCLIConfiguration) {
        super(errors, "sandboxes", mksCLIConfiguration);
    }

	@Override
	protected Command createAPICommand() {
		return new Command();
	}

	@Override
	protected Response executeCommand(Command command) throws APIException {
		return getAPIHelper().getSICommands().getSandboxes(true);
	}

	protected void handleResponse(Response response) throws APIException {
		ArrayList<SandboxInfo> tempSandboxes = new ArrayList<SandboxInfo>();

		final SubRoutineIterator routineIterator = response.getSubRoutines();
		while (routineIterator.hasNext()) {
			final SubRoutine subRoutine = routineIterator.next();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("routine " + subRoutine);
			}
		}
		Map<String, SandboxInfo> sandboxesByPath = new HashMap<String, SandboxInfo>();
		final WorkItemIterator workItems = response.getWorkItems();
		while (workItems.hasNext()) {
			final WorkItem sandboxWorkItem = workItems.next();
			String sandboxName = sandboxWorkItem.getField("sandboxName").getValueAsString();
			String server = sandboxWorkItem.getField("server").getValueAsString();

			String projectName = sandboxWorkItem.getField("projectName").getValueAsString();
			String buildRevision = sandboxWorkItem.getField("buildRevision").getValueAsString();
			String devPath = sandboxWorkItem.getField("developmentPath").getValueAsString();
			boolean isSubsandbox = sandboxWorkItem.getField("isSubsandbox").getBoolean();
			SandboxInfo sandbox;
			if (!isSubsandbox) {
				sandbox = new SandboxInfo(sandboxName, server, projectName, buildRevision, devPath);
			} else {
				String parentSandbox = sandboxWorkItem.getField("parentSandbox").getValueAsString();
				sandbox = new SandboxInfo(sandboxesByPath.get(parentSandbox), sandboxName, projectName, buildRevision, devPath);
			}
			sandboxesByPath.put(sandboxName, sandbox);
			tempSandboxes.add(sandbox);
		}
		sandboxesByPath.clear();
		result.addAll(tempSandboxes);
	}

	private void debug(WorkItem workItem) {
		StringWriter writer = new StringWriter();
        for (Iterator it = workItem.getFields(); it.hasNext(); ) {
            Field field = (Field) it.next();
            writer.append("\t").append(field.getName()).append("[").append(field.getDataType()).append("]").append(" : ").append(field.getValueAsString());
        }
        LOGGER.debug(writer.toString());
    }
}
