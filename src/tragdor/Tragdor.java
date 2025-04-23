package tragdor;

import static tragdor.util.Benchmark.tickTock;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import codeprober.AstInfo;
import codeprober.locator.CreateLocator;
import codeprober.locator.CreateLocator.LocatorMergeMethod;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.create.EncodeResponseValue;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.util.ASTProvider;
import tragdor.config.UserConfig;
import tragdor.report.Report;
import tragdor.steps.Explain;
import tragdor.steps.Generate;
import tragdor.util.Benchmark;
import tragdor.util.WebServer;

public class Tragdor {

	private static UserConfig config;

	public static final Set<Report> reports = new HashSet<>();

	private static final long startNanos = System.nanoTime();

	public static boolean shouldExcludeReport(String nodeType, String attrType) {
		return config != null && config.shouldExcludeReport(nodeType, attrType);
	}

	public static boolean report(Report rep) {
		if (config == null) {
			return false;
		}
		final String relatedNode = rep.getRelatedNodeType();
		final String relatedAttr = rep.getRelatedAttrName();
		if ((relatedNode != null || relatedAttr != null) && shouldExcludeReport(relatedNode, relatedAttr)) {
			return false;
		}
		if (reports.add(rep)) {
			if (config.getToolConfigs().size() > 1) {
				rep.setToolConfigIndex(config.getActiveToolConfigIdx());
			}
			rep.setDiscoveryTimeMs((System.nanoTime() - startNanos) / 1_000_000L);
			if (!quiet) {
				System.out.println("!! " + rep.getType() + ": " + rep.getMessage());
				final JSONObject dets = rep.getDetails();
				System.out.println("Details:");
				for (String prop : dets.keySet()) {
					System.out.printf("  \"%s\": %s%n", prop, dets.get(prop) + "");
				}
			}
			return true;
		}

		return false;
	}

	private static final boolean quiet = "true".equals(System.getProperty("tragdor.quiet"));
	public static final boolean verbose = "true".equals(System.getProperty("tragdor.verbose"));

	public static File getConfigFile() {
		final String configPath = System.getProperty("tragdor.config");
		if (configPath == null) {
			throw new RuntimeException("Missing property 'tragdor.config'");
		}
		final File configFile = new File(configPath);
		if (!configFile.exists()) {
			throw new RuntimeException("Config file '" + configFile + "' does not exist");
		}
		return configFile;
	}

	public static void resetAstState(Object astNodePtr) {

		// State resetting code below stolen from codeprober.toolglue.AstStateResetter
		// Even though this is a fresh parse, we must reset global states that may or
		// may not eixst. In JastAdd, this is done via '.state().reset()'
		try {
			final Method stateAccessor = astNodePtr.getClass().getMethod("resetState");
			final String retName = stateAccessor.getReturnType().getName();
//			// Some guard rails to avoid accidentally calling state().reset() on non-JastAdd
//			// tools.
			if (retName.endsWith(".ASTState") || retName.endsWith("$ASTState")) {
				stateAccessor.invoke(astNodePtr);
			} else {
				System.out.println("No resetState available...");
			}
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException e) {
			// Don't print anything, this is optional functionality, not available in all
			// tools.
			System.err.println("Failed resetting AST state");
			e.printStackTrace();
		}
	}

	private static final Set<String> incomparableWarnings = new HashSet<>();

	static void warnIncomparable(Class<?> cls) {
		if (incomparableWarnings.add(cls.getName())) {
			System.err.println(
					"WARN: Type " + cls.getName() + " neither implements equals nor toString, is difficult to compare");
		}
	}

	private static void printUsage() {
		System.out.println("Usage: java -jar tragdor.jar (generate|explain|serve) ...");
		System.out.println("Each verb has a unique set of additional arguments.");

		System.out.println("");
		System.out.println("== Generate: java -jar tragdor.jar generate path/to/config.json");
		System.out.println("    -> Detect issue symptoms and write them to reports.json");
		System.out.println("Optional system properties:");
		System.out.println("  -Dtragdor.search_budget_sec=X    # Set search time budget (seconds)");
		System.out.println("  -Dtragdor.cfg.tool.jar=X         # Path to tool jar, overrides value in config");
		System.out.println(
				"  -Dtragdor.cfg.tool.args=X        # Args to tool jar, overrides value in config. Treated as a comma-separated list");
		System.out.println(
				"  -Dtragdor.cfg.search_algorithm=X # Set which search algorithm to use, overrides value in config");
		System.out.println("                                   # Should be one of the following:");
		System.out.println("                                   #     'random_order'");
		System.out.println("                                   #     'rido' (or 'random_inverse_dependency_order')");
		System.out.println("                                   #     'rec' (or 'random_equation_check')");
		System.out.println("                                   #     'user_order'");
		System.out.println(
				"  -Dtragdor.cfg.repeat_passess=X   # Take X passes over all input files, overrides value in config.");
		System.out.println(
				"  -Dignore_circular_attribute_dependencies=false  # Include circular values during dependency graph construction (excluded by default)");

		System.out.println("");
		System.out.println("== Explain: java -jar tragdor.jar explain path/to/reports.json [attr1] [attr2] [..]");
		System.out.println("    -> Generate explanations for the symptoms in reports.json");
		System.out.println("Optional system properties:");
		System.out.println("  -Dtragdor.test.dst=X             # Set output directory for generating JUnit tests. Defaults to null");
		System.out.println("Optional arguments:");
		System.out.println(
				"   attr1, attr2, ...               # Attribute instance names to filter the explanation process,");
		System.out
				.println("                                   # for example 'Expr.type' or 'TypeDecl.uniqueId'. If not");
		System.out.println("                                   # specified, then all symptoms are explained.");

		System.out.println("");
		System.out.println("== Serve: java -jar tragdor.jar serve path/to/reports.json");
		System.out.println("    -> Start a web server for browsing the contents of a report");
		System.out.println("Optional environment variables:");
		System.out.println("  PORT                             # Port to host content on, defaults to 8000.");
		System.out.println("                                   # Set to 0 to automatically pick a port");
	}

	public static void main(String[] args) throws Exception {
		if (args.length <= 1 && System.getProperty("tragdor.config") == null) {
			printUsage();
			System.exit(1);
		}
		boolean shouldAutoSave = true;
		switch (args[0]) {
		case "generate":
			System.setProperty("tragdor.config", args[1]);
			break;
		case "explain": {
			shouldAutoSave = false;
			if (System.getProperty("tragdor.config") == null) {
				final byte[] reportsBytes = Files.readAllBytes(new File(args[1]).toPath());
				try (ByteArrayInputStream bais = new ByteArrayInputStream(
						new JSONObject(new String(reportsBytes, 0, reportsBytes.length, StandardCharsets.UTF_8))
								.getJSONObject("config").toString().getBytes(StandardCharsets.UTF_8))) {
					config = UserConfig.parse(bais);
				}
			}
			break;
		}
		case "serve": {
			final File reportFile = new File(args[1]);
			if (!reportFile.exists()) {
				System.err.println("Report file does not exist");
				return;
			}
			new WebServer(reportFile).start();
			return;
		}
		default: {
			System.err.println("Unknown verb '" + args[0] + "'");
			printUsage();
			System.exit(1);
		}
		}
		if (config == null) {
			config = UserConfig.parse(getConfigFile());
		}
		System.out.println("Starting " + Tragdor.class.getSimpleName() + " w/ args: " + Arrays.toString(args));
		CreateLocator.setMergeMethod(LocatorMergeMethod.SKIP);
		EncodeResponseValue.shouldSortSetAndMapContents = true;
		EncodeResponseValue.defaultToStringOverride = (value, dst) -> {
			dst.add(RpcBodyLine.fromPlain(value.getClass().getName() + "@x"));
		};
		EncodeResponseValue.failedCreatingLocatorOverride = (node, out) -> {
			out.add(RpcBodyLine.fromStderr("Failed to determine " + node + "'s position in the AST"));
		};

		final int concurrencySpec = Integer.parseInt(System.getProperty("concurrent", "-1"));
		if (concurrencySpec > 1) {
			if (!"generate".equals(args[0])) {
				System.err.println("Can only use 'concurrent' with 'generate'");
				System.exit(1);
			}
			System.out.println("Going to spawn " + concurrencySpec + " concurrent instances");

			List<String> copyProps = new ArrayList<>();
			final Consumer<String> addCopyProp = (key) -> {
				copyProps.add(String.format("-D%s=%s", key, System.getProperty(key)));
			};
			for (Object rawKey : System.getProperties().keySet()) {
				final String key = String.valueOf(rawKey);
				if (key.startsWith("tragdor")) {
					addCopyProp.accept(key);
				} else {
					switch (key) {
					case "auto_stop_random_search":
					case "search_budget_sec":
						addCopyProp.accept(key);
					}
				}
			}

			List<Process> workerProcesses = new ArrayList<>();
			for (int workerId = 0; workerId < concurrencySpec; ++workerId) {
				final List<String> cmd = new ArrayList<>();
				cmd.add("java");
				cmd.addAll(copyProps);
				cmd.add("-DworkerId=" + workerId);
				cmd.add("-DnumWorkers=" + concurrencySpec);
				cmd.add("-jar");
				try {
					cmd.add(new File(Tragdor.class.getProtectionDomain().getCodeSource().getLocation().toURI())
							.getPath());
				} catch (URISyntaxException e) {
					e.printStackTrace();
					throw new IOException(e);
				}
				cmd.addAll(Arrays.asList(args));

				workerProcesses.add(new ProcessBuilder() //
						.command(cmd) //
						.redirectOutput(new File("worker_" + workerId + ".out"))
						.redirectError(new File("worker_" + workerId + ".err")).start());

			}

			final Benchmark.TickTockErrv reportMerger = () -> {
				final JSONArray mergedReportObjects = new JSONArray();
				for (int i = 0; i < concurrencySpec; ++i) {
					final File reportFile = new File(String.format("reports_%d.json", i));
					if (reportFile.exists()) {
						final byte[] bytes = Files.readAllBytes(reportFile.toPath());
						final JSONArray arr = new JSONObject(new String(bytes, 0, bytes.length, StandardCharsets.UTF_8))
								.getJSONArray("reports");
						for (int j = 0; j < arr.length(); ++j) {
							mergedReportObjects.put(arr.getJSONObject(j));
						}
					}
				}
				saveReports("reports.json", mergedReportObjects);
			};

			System.out.println("Waiting for worker processes..");
			for (Process p : workerProcesses) {
				p.waitFor();
			}
			System.out.println("Merging reports..");
			reportMerger.run();
			return;
		}

		config.setActiveConfigIndex(0);

		doMain(args);

		if (shouldAutoSave) {
			saveReports();
		}
		Benchmark.report(args);
	}

	private static void doMain(String[] args) throws Exception {
		final Set<String> alreadyWarnedFaultyNodes = new HashSet<>();
		EncodeResponseValue.faultyNodeLocatorInspector = node -> {
			if (!alreadyWarnedFaultyNodes.add(node.getClass().getName())) {
				return;
			}
			System.err.println("Faulty node: " + node);
			Thread.dumpStack();
		};
		ASTProvider.printDebugInfo = false;
		Benchmark.tickTockErr(args[0], () -> {
			switch (args[0]) {

			case "generate": {
				Generate.doit(config);
				break;
			}

			case "explain": {
				if (args.length < 2) {
					System.err
							.println("Usage: java -jar tragdor.jar explain path/to/reports.json [NodeName.AttrName]+");
					System.err.println("Example: java -jar tragdor.jar reports.json Expr.type TypeDecl.uniqueId");
					System.err
							.println("Specify zero NodeName.AttrName instances to explain *all* entries in the report");
					System.exit(1);
				}

				Explain.doit(config, args[1], Arrays.copyOfRange(args, 2, args.length));
				break;

			}

			default: {
				System.err.println("Unknown arg '" + args[0] + "'");
				break;
			}
			}
			return null;
		});
	}

	private static boolean warnedMissingFlushTreeCache = false;

	public static void flushTreeCache(AstInfo info) {
		if (CreateLocator.identityLocatorCache != null) {
			CreateLocator.identityLocatorCache.clear();
		}
		tickTock("flushTreeCache", () -> {
			try {
				Reflect.invoke0(info.ast.underlyingAstNode, "flushTreeCache");
			} catch (InvokeProblem e) {
				if (!warnedMissingFlushTreeCache) {
					warnedMissingFlushTreeCache = true;
					System.err.println("flushTreeCache not supported in AST");
					e.getCause().printStackTrace();
				}
			}
			return null;
		});

	}

	private static int lastSavedReportsSize = -1;

	public static void saveReports() throws IOException, JSONException {
		final int newReportsSize = reports.size();
		if (newReportsSize == lastSavedReportsSize) {
			// No need to save, nothing has changed since last time
			return;
		}
		lastSavedReportsSize = newReportsSize;

		final Integer workerId = getWorkerId();
		if (workerId == null) {
			saveReports("reports.json");
		} else {
			saveReports(String.format("reports_%d.json", workerId));
		}
	}

	public static Integer getWorkerId() {
		final int workerId = Integer.parseInt(System.getProperty("workerId", "-1"));
		return workerId >= 0 ? workerId : null;
	}

	public static Integer getTotalNumWorkers() {
		final int numWorkers = Integer.parseInt(System.getProperty("numWorkers", "-1"));
		return numWorkers >= 0 ? numWorkers : null;
	}

	private static void saveReports(String fileName) throws IOException, JSONException {
		saveReports(fileName, new JSONArray(reports.stream().map(x -> x.toJSON()).collect(Collectors.toList())));
	}

	public static void saveReports(String fileName, JSONArray reports) throws IOException, JSONException {
		final JSONObject reportObj = new JSONObject()//
				.put("config", config.getRootConfig())//
				.put("reports", reports) //
				.put("uptimeMs", (System.nanoTime() - startNanos) / 1_000_000L);
		saveReportsAsIs(fileName, reportObj);
		System.out.printf("Saved '%s' with %d report(s)%n", fileName, reports.length());
	}
	public static void saveReportsAsIs(String fileName, JSONObject reportObj) throws IOException, JSONException {
		final File tmpFile = new File(fileName + ".tmp"); // TO avoid writing semi-correct files if process is killed
															// mid-write
		Files.write(tmpFile.toPath(), reportObj//
				.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
		tmpFile.renameTo(new File(fileName));
	}
}
