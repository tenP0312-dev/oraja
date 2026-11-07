package bms.player.beatoraja.skin.lua;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.IoLib;
import org.luaj.vm2.lib.jse.JseOsLib;

/**
 * Restricted os/io libraries for Lua skins, ported from beatoraja
 * (SkinLuaAccessor.SafeOsLib / RestrictedIoLib).
 *
 * Skins keep os.time/os.date/os.clock and io access inside their own skin
 * directory, but cannot run programs, read environment variables, delete or
 * rename files, follow symbolic links or leave the skin directory.
 */
final class SkinLuaRestrictedLibs {

	private SkinLuaRestrictedLibs() {
	}

	static final class SafeOsLib extends JseOsLib {
		@Override
		public LuaValue call(LuaValue modname, LuaValue env) {
			LuaValue os = super.call(modname, env);
			os.set("execute", LuaValue.NIL);
			os.set("exit", LuaValue.NIL);
			os.set("getenv", LuaValue.NIL);
			os.set("remove", LuaValue.NIL);
			os.set("rename", LuaValue.NIL);
			os.set("tmpname", LuaValue.NIL);
			return os;
		}
	}

	static final class RestrictedIoLib extends IoLib {
		private Path root;

		void setRoot(Path root) {
			this.root = root;
		}

		@Override
		protected File wrapStdin() {
			return new MemoryFile(new byte[0]);
		}

		@Override
		protected File wrapStdout() {
			return new MemoryFile();
		}

		@Override
		protected File wrapStderr() {
			return new MemoryFile();
		}

		@Override
		protected File openFile(String filename, boolean readMode, boolean appendMode, boolean updateMode, boolean binaryMode) throws IOException {
			Path path = resolve(filename);
			if (readMode && !Files.isRegularFile(path)) {
				throw new IOException("Lua file not found: " + filename);
			}
			if (readMode && !updateMode) {
				return new RestrictedFile(new RandomAccessFile(path.toFile(), "r"));
			}

			if (!readMode) {
				Path parent = path.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
			}

			RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
			if (appendMode) {
				file.seek(file.length());
			} else if (!readMode) {
				file.setLength(0);
			}
			return new RestrictedFile(file);
		}

		@Override
		protected File tmpFile() throws IOException {
			Path root = requireRoot();
			return new RestrictedFile(new RandomAccessFile(Files.createTempFile(root, "lua-", ".tmp").toFile(), "rw"));
		}

		@Override
		protected File openProgram(String prog, String mode) throws IOException {
			throw new IOException("Lua io.popen is not allowed");
		}

		private Path resolve(String filename) throws IOException {
			Path root = requireRoot();
			Path path = Path.of(filename);
			Path resolved;
			if (path.isAbsolute()) {
				resolved = path.normalize();
			} else {
				Path workingDirectoryPath = path.toAbsolutePath().normalize();
				resolved = workingDirectoryPath.startsWith(root)
						? workingDirectoryPath
						: root.resolve(path).toAbsolutePath().normalize();
			}
			if (!resolved.startsWith(root)) {
				throw new IOException("Lua skin file access denied: " + filename);
			}
			// Check components even when the final file does not exist (write/append).
			Path component = root;
			for (Path name : root.relativize(resolved)) {
				component = component.resolve(name);
				if (Files.isSymbolicLink(component)) {
					throw new IOException("Lua skin symbolic link access denied: " + filename);
				}
			}
			return resolved;
		}

		private Path requireRoot() throws IOException {
			if (root == null) {
				throw new IOException("Lua skin directory is not specified");
			}
			if (Files.isSymbolicLink(root)) {
				throw new IOException("Lua skin symbolic link access denied: " + root);
			}
			return root;
		}

		private class RestrictedFile extends File {
			private final RandomAccessFile file;
			private boolean closed;

			private RestrictedFile(RandomAccessFile file) {
				this.file = file;
			}

			@Override
			public void write(LuaString string) throws IOException {
				checkClosed();
				file.write(string.m_bytes, string.m_offset, string.m_length);
			}

			@Override
			public void flush() throws IOException {
				checkClosed();
				file.getFD().sync();
			}

			@Override
			public boolean isstdfile() {
				return false;
			}

			@Override
			public void close() throws IOException {
				closed = true;
				file.close();
			}

			@Override
			public boolean isclosed() {
				return closed;
			}

			@Override
			public int seek(String option, int bytecount) throws IOException {
				checkClosed();
				long base = switch (option) {
					case "set" -> 0;
					case "cur" -> file.getFilePointer();
					case "end" -> file.length();
					default -> throw new IOException("invalid seek option: " + option);
				};
				long position = Math.max(0, base + bytecount);
				file.seek(position);
				return (int) Math.min(position, Integer.MAX_VALUE);
			}

			@Override
			public void setvbuf(String mode, int size) {
			}

			@Override
			public int remaining() throws IOException {
				checkClosed();
				return (int) Math.min(Math.max(0, file.length() - file.getFilePointer()), Integer.MAX_VALUE);
			}

			@Override
			public int peek() throws IOException, EOFException {
				checkClosed();
				long position = file.getFilePointer();
				int value = file.read();
				file.seek(position);
				if (value < 0) {
					throw new EOFException();
				}
				return value;
			}

			@Override
			public int read() throws IOException, EOFException {
				checkClosed();
				int value = file.read();
				if (value < 0) {
					throw new EOFException();
				}
				return value;
			}

			@Override
			public int read(byte[] bytes, int offset, int length) throws IOException {
				checkClosed();
				return file.read(bytes, offset, length);
			}

			private void checkClosed() throws IOException {
				if (closed) {
					throw new IOException("file is closed");
				}
			}
		}

		private class MemoryFile extends File {
			private byte[] data;
			private int pos;
			private boolean closed;
			private final ByteArrayOutputStream output;

			private MemoryFile() {
				this.data = new byte[0];
				this.output = new ByteArrayOutputStream();
			}

			private MemoryFile(byte[] data) {
				this.data = data;
				this.output = null;
			}

			@Override
			public void write(LuaString string) throws IOException {
				checkClosed();
				if (output != null) {
					output.write(string.m_bytes, string.m_offset, string.m_length);
				}
			}

			@Override
			public void flush() throws IOException {
				checkClosed();
			}

			@Override
			public boolean isstdfile() {
				return true;
			}

			@Override
			public void close() {
				closed = true;
			}

			@Override
			public boolean isclosed() {
				return closed;
			}

			@Override
			public int seek(String option, int bytecount) throws IOException {
				checkClosed();
				int base = switch (option) {
					case "set" -> 0;
					case "cur" -> pos;
					case "end" -> data.length;
					default -> throw new IOException("invalid seek option: " + option);
				};
				pos = Math.max(0, Math.min(data.length, base + bytecount));
				return pos;
			}

			@Override
			public void setvbuf(String mode, int size) {
			}

			@Override
			public int remaining() throws IOException {
				checkClosed();
				return Math.max(0, data.length - pos);
			}

			@Override
			public int peek() throws IOException, EOFException {
				checkClosed();
				if (pos >= data.length) {
					throw new EOFException();
				}
				return data[pos] & 0xff;
			}

			@Override
			public int read() throws IOException, EOFException {
				checkClosed();
				if (pos >= data.length) {
					throw new EOFException();
				}
				return data[pos++] & 0xff;
			}

			@Override
			public int read(byte[] bytes, int offset, int length) throws IOException {
				checkClosed();
				if (pos >= data.length) {
					return -1;
				}
				int readLength = Math.min(length, data.length - pos);
				System.arraycopy(data, pos, bytes, offset, readLength);
				pos += readLength;
				return readLength;
			}

			private void checkClosed() throws IOException {
				if (closed) {
					throw new IOException("file is closed");
				}
			}
		}
	}
}
