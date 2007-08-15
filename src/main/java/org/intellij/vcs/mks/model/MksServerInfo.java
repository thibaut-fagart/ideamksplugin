package org.intellij.vcs.mks.model;

/**
 * Created by IntelliJ IDEA.
* User: Thibaut
* Date: 15 août 2007
* Time: 13:45:30
* To change this template use File | Settings | File Templates.
*/ // when offline you have like "79310750@vhvhcl50.us.hsbc:7001 (offline)"
public final class MksServerInfo {
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
