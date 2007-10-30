package org.intellij.vcs.mks.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AsyncStreamBuffer {
	private InputStream in;

	private IOException cause;
	private byte[] buf;

	private Thread reader = new Thread() {
		private boolean active;

		@Override
		public void run() {
			active = true;
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();

				int b;
				byte[] buffer = new byte[2048];
				int readBytes;

				while (active && (readBytes = in.read(buffer)) != -1) {
					out.write(buffer, 0, readBytes);
				}

				buf = out.toByteArray();

			} catch (IOException e) {
				cause = e;
			}
		}

		@Override
		public void interrupt() {
			active = false;
			super.interrupt();
		}
	};

	public AsyncStreamBuffer(InputStream in) {
		this.in = in;
		reader.start();
	}

	public boolean isOpen() {
		return reader != null && reader.isAlive();
	}

	protected void waitFor() throws IOException {
		if (isOpen()) {
			//noinspection EmptyCatchBlock
			try {
				reader.join();

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public int size() throws IOException {
		return get().length;
	}

	public byte[] get() throws IOException {
		waitFor();

		if (cause != null) {
			throw cause;
		}

		return buf;
	}

	public InputStream openStream() throws IOException {
		return new ByteArrayInputStream(get());
	}

	public void close() throws IOException {
		if (isOpen()) {
			reader.interrupt();

			waitFor();
		}
		reader = null;
	}
}
