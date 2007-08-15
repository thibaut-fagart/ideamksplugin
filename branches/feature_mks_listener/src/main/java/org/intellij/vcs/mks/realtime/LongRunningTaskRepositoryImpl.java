package org.intellij.vcs.mks.realtime;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;

import java.util.*;

/**
 * Keeps track of all long running tasks, and allows monitoring/start/stop of them. <br/>
 * Stops all tasks when project is closed
 * 
 */
public class LongRunningTaskRepositoryImpl extends AbstractProjectComponent implements LongRunningTaskRepository, Collection<LongRunningTask> {
	private final List<LongRunningTask> tasks = new ArrayList<LongRunningTask>();
	public LongRunningTaskRepositoryImpl(Project project) {
		super(project);
	}

	public void projectClosed() {
		// stop all tasks
		clear();
	}

	public void projectOpened() {
	}

	public void disposeComponent() {
	}

	public void initComponent() {
	}

	public boolean add(LongRunningTask longRunningTask) {
		return tasks.add(longRunningTask);
	}

	public boolean addAll(Collection<? extends LongRunningTask> longRunningTasks) {
		return tasks.addAll(longRunningTasks);
	}

	public void clear() {
		synchronized (tasks) {
			for (LongRunningTask task : tasks) {
				task.stop();
			}
			tasks.clear();
		}
	}

	public boolean containsAll(Collection<?> objects) {
		return tasks.containsAll(objects);
	}

	public boolean contains(Object o) {
		return tasks.contains(o);
	}

	public boolean isEmpty() {
		return tasks.isEmpty();
	}

	public Iterator<LongRunningTask> iterator() {
		return Collections.unmodifiableList(tasks).iterator();
	}

	public boolean remove(Object o) {
		synchronized (tasks) {
			int i = tasks.indexOf(o);
			if (i>=0) {
				LongRunningTask task = tasks.get(i);
				task.stop();
				return tasks.remove(task);
			}
			return false;
		}
	}

	public boolean removeAll(Collection<?> objects) {
		boolean changed = false;
		for (Object object : objects) {
			changed |= remove(object);
		}
		return changed;
	}

	public boolean retainAll(Collection<?> objects) {
		synchronized (tasks) {
			ArrayList <LongRunningTask>temp = new ArrayList<LongRunningTask>(tasks);
			boolean changed = false ;
			for (LongRunningTask task : temp) {
				if (!objects.contains(task)) {
					changed |= tasks.remove(task);
				}
			}
			return changed;
		}
	}

	public int size() {
		return tasks.size();
	}

	public Object[] toArray() {
		return tasks.toArray();
	}

	public <T> T[] toArray(T[] ts) {
		return tasks.toArray(ts);
	}
}
