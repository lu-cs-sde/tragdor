package tragdor.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tragdor.Tragdor;

public class WebServer extends Thread {

	private static String guessMimeType(String path) {
		switch (path.substring(path.lastIndexOf('.') + 1)) {
		case "html":
			return "text/html";
		case "js":
			return "text/javascript";
		case "css":
			return "text/css";
		case "svg":
			return "image/svg+xml";
		case "png":
			return "image/png";
		case "ico":
			return "image/x-icon";
		case "ttf":
			return "font/ttf";
		case "json":
			return "application/json";

		default:
			System.out.println("Don't know mime type of path " + path);
			return null;
		}
	}

	private final File reportFile;

	public WebServer(File reportFile) {
		this.reportFile = reportFile;
	}

	private void handleGetRequest(Socket socket, String data) throws IOException {
		final OutputStream out = socket.getOutputStream();

		final Matcher getReqPathMatcher = Pattern.compile("^GET (.*) HTTP").matcher(data);
		if (!getReqPathMatcher.find()) {
			System.err.println("Missing path for GET request");
			return;
		}

		final String[] pathAndQueries = getReqPathMatcher.group(1).split("[?&#]");
		String path = pathAndQueries[0];
		if (path.endsWith("/")) {
			path += "index.html";
		}

		final InputStream stream;
		Long contentLength = null;

		if (path.equals("/index.html") && reportFile.isDirectory()
				&& !Arrays.asList(pathAndQueries).stream().anyMatch(x -> x.startsWith("report="))) {
			// Show all json files in the directory
			StringBuilder ret = new StringBuilder();
			ret.append("<html><title>Tragdor</title><body style=\"padding: 0.5rem; border: 1px solid black; border-radius: 0.25rem;\"><h1>Select report file</h1>");
			File[] children = reportFile.listFiles();
			boolean anyFound = false;
			if (children != null) {
				for (File child : children) {
					if (!Pattern.compile("^[a-zA-Z0-9_]+.json$").matcher(child.getName()).matches()) {
						continue;
					}
					// No need to html escape name, regex above handles that
					if (!anyFound) {
						anyFound = true;
						ret.append("<ul>");
					}
					ret.append(String.format("<li><a href=\"?report=/reports.json/%s\">%s</a></li>", child.getName(),
							child.getName()));
				}
			}
			if (!anyFound) {
				ret.append(
						"<div>No files found with expected name structure. <code>serve</code> only works after generating report files. Please generate reports first, and/or make sure that the report path is either a report file, or path to the directory where a report file can be found.</div>");
			} else {
				ret.append("</ul>");
			}
			ret.append("</body></html>");

			final byte[] resultBytes = ret.toString().getBytes(StandardCharsets.UTF_8);
			stream = new ByteArrayInputStream(resultBytes);
			contentLength = (long) resultBytes.length;
		} else if (path.equals("/reports.json")) {
			stream = new FileInputStream(reportFile);
			contentLength = reportFile.length();
		} else if (path.startsWith("/reports.json/")) {
			// Referencing a file inside the reportFile directory
			if (!reportFile.isDirectory()) {
				// ??
				stream = null;
			} else {
				String fname = path.substring("/reports.json/".length());
				if (fname.contains("/") || fname.contains("\\") || fname.contains("..")) {
					stream = null;
				} else {
					final File contents = new File(reportFile, fname);
					if (!contents.exists()) {
						stream = null;
					} else {
						stream = new FileInputStream(contents);
						contentLength = contents.length();
					}
				}
			}
		} else {
			stream = openSourceFileStream(path);
		}
		if (stream == null) {
			System.out.println("Found no resource for path '" + path + "'");
			out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes("UTF-8"));
			return;
		}
		out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));

		String mimeType = guessMimeType(path);
		if (mimeType != null) {
			out.write(("Content-Type: " + mimeType + "\r\n").getBytes("UTF-8"));
		}
		if (contentLength != null) {
			out.write(("Content-Length: " + contentLength + "\r\n").getBytes("UTF-8"));
		}

		out.write("\r\n".getBytes("UTF-8"));

		final byte[] buf = new byte[512];
		int read;
		while ((read = stream.read(buf)) != -1) {
			out.write(buf, 0, read);
		}
		out.flush();
		stream.close();
	}

	private InputStream openSourceFileStream(String path) throws IOException {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		return Tragdor.class.getResourceAsStream("web/" + path);
	}

	private void handleRequest(Socket socket) throws IOException {
		final InputStream in = socket.getInputStream();
		final ByteArrayOutputStream headerBaos = new ByteArrayOutputStream();

		// Reading 1 byte at a time is quite bad for efficiency, but as long as we only
		// do it for the header then it is not _too_ painful
		// We don't want to ready any more bytes than absolutely necessary, so that when
		// it comes time to read the HTTP body, the remaining bytes are available in the
		// stream.
		int read;
		int divisorState = 0;
		readHeader: while ((read = in.read()) != -1) {
			switch (read) {
			case '\r': {
				if (divisorState == 0 || divisorState == 2) {
					++divisorState;
					continue;
				}
				break;
			}
			case '\n': {
				if (divisorState == 1) {
					++divisorState;
					continue;
				}
				if (divisorState == 3) {
					break readHeader;
				}
				break;
			}
			}

			switch (divisorState) {
			case 1:
				headerBaos.write('\r');
				break;
			case 2:
				headerBaos.write('\r');
				headerBaos.write('\n');
				break;
			case 3:
				headerBaos.write('\r');
				headerBaos.write('\n');
				headerBaos.write('\r');
				break;

			default:
				break;
			}
			divisorState = 0;
			headerBaos.write(read);
		}

		final String headers = new String(headerBaos.toByteArray(), StandardCharsets.UTF_8);
		final Matcher get = Pattern.compile("^GET").matcher(headers);
		if (get.find()) {
			handleGetRequest(socket, headers);
			return;
		}
		System.out.println("Not sure how to handle request " + headers);
	}

	private static Integer actualPort = null;

	public static int getPort() {
		if (actualPort != null) {
			return actualPort;
		}
		return getFallbackPort();
	}

	public static int getFallbackPort() {
		final String portOverride = System.getenv("PORT");
		if (portOverride != null) {
			try {
				final int parsed = Integer.parseInt(portOverride);
				return (parsed == 0 && actualPort != null) ? actualPort : parsed;
			} catch (NumberFormatException e) {
				System.out.println("Invalid web port override '" + portOverride + "', ignoring");
				e.printStackTrace();
			}
		}
		return 8000;
	}

	@Override
	public void run() {
		final int port = getPort();
		try (ServerSocket server = new ServerSocket(port, 0, null)) {
			// if port=0, then the port number is automatically allocated.
			// Use 'actualPort' to get the port that was really opened, as 0 is invalid.
			actualPort = server.getLocalPort();
			System.out.println("Started server on http://localhost:" + actualPort + "/");

			try {
				while (true) {
					Socket s = server.accept();
					new Thread(() -> {
						try {
							handleRequest(s);
						} catch (IOException e) {
							System.out.println("Error while handling request");
							e.printStackTrace();
						} finally {
							try {
								s.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}).start();
				}
			} catch (Throwable t) {
				t.printStackTrace();
				throw t;
			}
		} catch (IOException e) {
			e.printStackTrace();
			if (e.getMessage().contains("Address already in use")) {
				System.out.println("Stop the existing process running on port " + port
						+ ", or set the PORT environment variable to something else");
				System.exit(1);
			}
			throw new RuntimeException(e);
		}
	}
}
