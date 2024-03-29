package org.intellij.vcs.mks.sicommands.api;

import com.intellij.openapi.vcs.VcsException;
import com.mks.api.Command;
import com.mks.api.response.*;
import org.intellij.vcs.mks.MksCLIConfiguration;
import org.intellij.vcs.mks.model.MksServerInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ListServersAPI extends SiAPICommand {
    public static final String COMMAND = "servers";
	public ArrayList<MksServerInfo> servers = new ArrayList<MksServerInfo>();

	public ListServersAPI(@NotNull List<VcsException> errors, @NotNull MksCLIConfiguration mksCLIConfiguration) {
        super(errors, COMMAND, mksCLIConfiguration);
    }

	protected void handleResponse(Response response) throws APIException {
		ArrayList<MksServerInfo> tempServers = new ArrayList<MksServerInfo>();

		final WorkItemIterator workItems = response.getWorkItems();
		while (workItems.hasNext()) {
			final WorkItem workItem = workItems.next();

			String username = workItem.getField("username").getValueAsString();
			String hostname = workItem.getField("hostname").getValueAsString();
			String port = workItem.getField("portnumber").getValueAsString();
			MksServerInfo server = new MksServerInfo(username, hostname, port);
			tempServers.add(server);
		}
		servers = tempServers;
	}

	protected Command createAPICommand() {
		Command command = new Command(Command.SI);
		command.setCommandName("servers");
		return command;
	}
}

