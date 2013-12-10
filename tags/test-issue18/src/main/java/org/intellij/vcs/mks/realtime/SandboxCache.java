package org.intellij.vcs.mks.realtime;

import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.util.Set;

/**
 * @author Thibaut Fagart
 */
public interface SandboxCache extends ModuleRootListener {

	/**
	 * @param virtualFile the file that needs a sandbox
	 * @return the sandbox this file belongs to, or null if it isn't inside a
	 *         sandbox. this is the "closest" sandbox, eg, the first one found while crawling up the
	 *         file system
	 */
	@Nullable
	MksSandboxInfo getSandboxInfo(@NotNull VirtualFile virtualFile);

	/**
	 * @param virtualFile the file
	 * @return true if the given file is the fileSystem resource embodying a sandbox (commonly "project.pj")
	 */
	boolean isSandboxProject(@NotNull VirtualFile virtualFile);


	void clear();

	/**
	 * @param sandboxPath
	 * @param serverHostAndPort
	 * @param mksProject
	 * @param devPath		   null if the sandbox is on the trunk
	 */
	void addSandboxPath(@NotNull String sandboxPath, @NotNull final String serverHostAndPort, @NotNull String mksProject, @Nullable String devPath);

	// for mks monitoring
	void dumpStateOn(@NotNull PrintWriter pw);

	/**
	 * returns all in project sandboxes that either contain the given directory,
	 * or are contained by it
	 *
	 * @param directory a directory for which we want to find the relevant sandboxes
	 * @return a non null collection of sandboxes whose content intersect the directory
	 */
	@NotNull
	Set<MksSandboxInfo> getSandboxesIntersecting(@NotNull VirtualFile directory);

	/**
	 * @param file the file to test for sandbox inclusion
	 * @return true if the given file belongs to a sandbox (which would be the one returned by findsandbox).
	 *         This is false if file has only been locally created (eg not added to mks)
	 */
	boolean isPartOfSandbox(@NotNull VirtualFile file);

	/**
	 * called when the project is unloaded, should clean up the cache
	 */
	void release();
}
