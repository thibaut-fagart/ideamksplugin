package org.intellij.vcs.mks.realtime;

import java.io.PrintWriter;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.vfs.VirtualFile;
import mks.integrations.common.TriclopsSiSandbox;

/**
 * @author Thibaut Fagart
 */
public interface SandboxCache extends ModuleRootListener {
	/**
	 * @deprecated use {@link #getSandboxInfo}
	 * @param virtualFile the file that needs a sandbox
	 * @return the sandbox this file belongs to, or null if it isn't inside a sandbox
	 */
	@Nullable
	TriclopsSiSandbox findSandbox(@NotNull VirtualFile virtualFile);

	/**
	 * @param virtualFile the file that needs a sandbox
	 * @return the sandbox this file belongs to, or null if it isn't inside a
	 *         sandbox
	 */
	MksSandboxInfo getSandboxInfo(@NotNull VirtualFile virtualFile);

	/**
	 * @param virtualFile the file
	 * @return true if the given file is the fileSystem resource embodying a sandbox (commonly "project.pj")
	 */
	boolean isSandboxProject(@NotNull VirtualFile virtualFile);


	void clear();

	void addSandboxPath(@NotNull String sandboxPath, final String serverHostAndPort, String mksProject, String devPath);

    // for mks monitoring
    void dumpStateOn(PrintWriter pw);

	/**
	 * returns all in project sandboxes that either contain the given directory,
	 * or are contained by it
	 *  
	 * @param directory
	 * @return
	 */
	Set<MksSandboxInfo> getSandboxesIntersecting(VirtualFile directory);

	/**
	 *
	 * @return true if the given file belongs to a sandbox (which would be the one returned by findsandbox).
	 * This is false if file has only been locally created (eg not added to mks) 
	 */
	boolean isPartOfSandbox(VirtualFile file);
}