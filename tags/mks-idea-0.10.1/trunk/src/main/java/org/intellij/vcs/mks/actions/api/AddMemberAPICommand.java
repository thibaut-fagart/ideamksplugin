package org.intellij.vcs.mks.actions.api;

import com.intellij.openapi.vcs.AbstractVcs;
import com.mks.api.CmdRunner;
import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import org.intellij.vcs.mks.MKSAPIHelper;
import org.intellij.vcs.mks.MksBundle;
import org.intellij.vcs.mks.realtime.MksSandboxInfo;
import org.jetbrains.annotations.NotNull;

public class AddMemberAPICommand extends AbstractMultipleTargetAPICommand  {
	@NotNull
	public String getActionName(@NotNull AbstractVcs vcs) {
		return MksBundle.message("action.addmembers");
	}

    @Override
    protected void perform(@NotNull MksSandboxInfo sandbox, String[] members) throws APIException {
        final CmdRunner runner =  MKSAPIHelper.getInstance().getSession().createCmdRunner();
        Command command = new Command(Command.SI);
        command.setCommandName("add");
        command.addOption(new Option("gui"));
        command.addOption(new Option("cwd", sandbox.getSandboxDir().getCanonicalPath()));
        for (int i = 0; i < members.length; i++) {
            String member = members[i];
            command.addSelection(member);
        }

        runner.execute(command);
    }
}