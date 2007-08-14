package org.intellij.vcs.mks.sicommands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.intellij.vcs.mks.EncodingProvider;
import com.intellij.openapi.vcs.VcsException;

/**
 * @author Thibaut Fagart
 */
public class ListServers extends SiCLICommand {
	private static final String patternString = "([^@]+)@([^:]+):(\\d+).*";
	// when offline you have like "79310750@vhvhcl50.us.hsbc:7001 (offline)"
	public static final class MksServerInfo {
		public final String user;
		public final String host;
		public final String port;

		public MksServerInfo(final String user, final String host, final String port) {
			this.host = host;
			this.port = port;
			this.user = user;
		}

		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			MksServerInfo that = (MksServerInfo) o;

			if (host != null ? !host.equals(that.host) : that.host != null)
				return false;
			if (port != null ? !port.equals(that.port) : that.port != null)
				return false;

			return true;
		}

		public int hashCode() {
			int result;
			result = (host != null ? host.hashCode() : 0);
			result = 31 * result + (port != null ? port.hashCode() : 0);
			return result;
		}
	}

	public ArrayList<MksServerInfo> servers;

	public static final String COMMAND = "servers";

	public ListServers(List<VcsException> errors, EncodingProvider encodingProvider) {
        super(errors, encodingProvider, COMMAND);
    }

    @Override
    public void execute() {
	    Pattern pattern = Pattern.compile(patternString);
	    ArrayList<MksServerInfo> tempServers = new ArrayList<MksServerInfo>();
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
	            Matcher matcher = pattern.matcher(line);
	            if (matcher.matches()) {
//                if (line.contains("@") && line.contains(":")) {
//                    int arobaseIndex = line.indexOf('@');
		            String user = matcher.group(1);
//	                    line.substring(0, arobaseIndex);
                    String host = matcher.group(2);
//	                    line.substring(arobaseIndex + 1, line.indexOf(':'));
		            String port = matcher.group(3);
                    tempServers.add(new MksServerInfo(user, host,port));

                } else {
                    LOGGER.error("unexpected command output {" + line + "}, expected [user@host:port]");
                    //noinspection ThrowableInstanceNeverThrown
                    errors.add(new VcsException("unexpected line structure " + line));
                }
            }
            servers = tempServers;
        } catch (IOException e) {
            //noinspection ThrowableInstanceNeverThrown
            errors.add(new VcsException(e));
        }
    }
    @Override
    public String toString() {
        return "ListServers";
    }
	
}
