package org.intellij.vcs.mks.realtime;

import mks.integrations.common.TriclopsSiSandbox;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Thibaut Fagart
*/
public final class MksSandboxInfo implements Comparable<MksSandboxInfo> {
	public final String sandboxPath;
	public final String hostAndPort;
	final VirtualFile sandboxPjFile;
	TriclopsSiSandbox siSandbox;

	public MksSandboxInfo(final String sandboxPath, final String hostAndPort, final VirtualFile sandboxPjFile) {
		this.sandboxPjFile = sandboxPjFile;
		this.hostAndPort = hostAndPort;
		this.sandboxPath = sandboxPath;
	}

	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MksSandboxInfo that = (MksSandboxInfo) o;

		if (!hostAndPort.equals(that.hostAndPort)) return false;
		if (!sandboxPath.equals(that.sandboxPath)) return false;

		return true;
	}

	public int hashCode() {
		int result;
		result = sandboxPath.hashCode();
		result = 31 * result + hostAndPort.hashCode();
		return result;
	}

	public int compareTo(final MksSandboxInfo sandboxInfo) {
		return sandboxPath.compareTo((sandboxInfo == null) ? null : sandboxInfo.sandboxPath);
	}

	@Override
	public String toString() {
		return "MksSandbox["+sandboxPath+","+hostAndPort+"]";
	}
}
