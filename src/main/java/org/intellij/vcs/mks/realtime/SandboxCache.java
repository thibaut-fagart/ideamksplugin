package org.intellij.vcs.mks.realtime;

import java.io.PrintWriter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ModuleRootListener;
import mks.integrations.common.TriclopsSiSandbox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Thibaut Fagart
 */
public interface SandboxCache extends ModuleRootListener {
	/**
	 * @param virtualFile the file that needs a sandbox
	 * @return the sandbox this file belongs to, or null if it isn't inside a sandbox
	 */
	@Nullable
	TriclopsSiSandbox findSandbox(@NotNull VirtualFile virtualFile);

	/**
	 * @param virtualFile the file
	 * @return true if the given file is the fileSystem resource embodying a sandbox (commonly "project.pj")
	 */
	boolean isSandboxProject(@NotNull VirtualFile virtualFile);


	void clear();

	void addSandboxPath(@NotNull String sandboxPath);

    // for mks monitoring
    void dumpStateOn(PrintWriter pw);
}
