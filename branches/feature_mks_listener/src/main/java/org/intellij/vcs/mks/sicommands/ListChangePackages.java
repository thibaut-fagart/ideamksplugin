package org.intellij.vcs.mks.sicommands;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsException;
import org.intellij.vcs.mks.EncodingProvider;
import org.intellij.vcs.mks.model.MksChangePackage;
import org.intellij.vcs.mks.model.MksServerInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Thibaut Fagart
 */
public class ListChangePackages extends SiCLICommand {
	public List<MksChangePackage> changePackages;
	@NonNls
	public static final String COMMAND = "viewcps";
	public static final int ID = 0;
	public static final int USER = 1;
	public static final int STATE = 2;
	public static final int SUMMARY = 3;
	public static final String ARGS = "--fields=id,user,state,summary";
	public final MksServerInfo serverInfo;

	public ListChangePackages(List<VcsException> errors, EncodingProvider encodingProvider, final MksServerInfo server) {
		super(errors, encodingProvider, COMMAND, (server == null) ? new String[]{ARGS} : new String[]{ARGS, "--hostname", server.host});
		serverInfo = server;
	}

	@Override
	public void execute() {
		ArrayList<MksChangePackage> tempChangePackages = new ArrayList<MksChangePackage>();
		try {
			String command = executeCommand();
			String[] lines = commandOutput.split("\n");
			int start = 0;
			while (start < lines.length && shouldIgnore(lines[start])) {
				// skipping connecting/reconnecting lines
				start++;
			}
			for (int i = start, max = lines.length; i < max; i++) {
				String line = lines[i];
				String[] parts = line.split("\t");
				if (parts.length < 4) {
					String errrorMessage = "unexpected command output {" + line + "}, expected 4 parts separated by \\t, while executing " + command;
					LOGGER.error(errrorMessage, "");
					//noinspection ThrowableInstanceNeverThrown
					errors.add(new VcsException(errrorMessage));
				} else {
					tempChangePackages.add(new MksChangePackage(serverInfo.host, parts[ID], parts[USER], parts[STATE], parts[SUMMARY]));
				}
			}
			changePackages = tempChangePackages;
		} catch (IOException e) {
			//noinspection ThrowableInstanceNeverThrown
			errors.add(new VcsException(e));
		}
	}

	@Override
	protected void handleErrorOutput(String errorOutput) {
		super.handleErrorOutput(errorOutput);

		if (exitValue == 128 && errorOutput.contains("(it may be down)")) {
			class MyDialog extends DialogWrapper {
				MyDialog() {
					super(false);
					init();
				}

				@Override
				@Nullable
				protected JComponent createCenterPanel() {
					//					JPanel panel = new JPanel(new BorderLayout());
					//					panel.add(
					return new JLabel("Is " + serverInfo.host + ":" + serverInfo.port + " a source integrity server ?\n" +
							"It does not seem to accept si commands.\n" +
							"(Answer yes if it is only momentarily down");
					//					);
					//					return panel;
				}

				@Override
				protected Action[] createActions() {
					Action[] actions = new Action[2];
					actions[0] = new AbstractAction("Yes") {
						public void actionPerformed(ActionEvent e) {
							serverInfo.isSIServer = true;
							close(1);
						}
					};
					actions[1] = new AbstractAction("No") {
						public void actionPerformed(ActionEvent e) {
							serverInfo.isSIServer = false;
							close(1);
						}
					};
					return actions;
				}
			}

			final MyDialog dialog = new MyDialog();
			try {
				SwingUtilities.invokeAndWait(new Runnable() {
					public void run() {
						dialog.show();
					}

				});
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (InvocationTargetException e) {
				LOGGER.warn(e);
				final Throwable o = e.getTargetException();
				//noinspection ThrowableInstanceNeverThrown
				errors.add(o instanceof VcsException ? (VcsException) o : new VcsException(o));
			}


		}
	}

	@Override
	protected boolean shouldIgnore(String line) {
		return super.shouldIgnore(line) || line.trim().length() == 0;
	}

	@Override
	public String toString() {
		return "ListChangePackages[" + serverInfo + "]";
	}
}
