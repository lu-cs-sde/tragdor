package tragdor.steps;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.locator.CreateLocator;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.Property;
import tragdor.LocatedProp;
import tragdor.Tragdor;
import tragdor.config.UserConfig;
import tragdor.contrib.MinimizeRepros;
import tragdor.contrib.MinimizeRepros.PerturbedValueSearchResult;
import tragdor.contrib.ReproDb;
import tragdor.report.ReportType;
import tragdor.steps.step1.EstablishReferenceValues;
import tragdor.steps.step1.EstablishReferenceValues.ReferenceValues;
import tragdor.util.LocatorStepToStr;

public class Explain {

	public static void doit(UserConfig config, String srcReportFile, String... requestedExplanations) throws Exception {
		final byte[] reportsBytes = Files.readAllBytes(new File(srcReportFile).toPath());
		final JSONObject rootReportObj = new JSONObject(
				new String(reportsBytes, 0, reportsBytes.length, StandardCharsets.UTF_8));
		final JSONArray reportsContents = rootReportObj.getJSONArray("reports");

		final Map<String, Function<JSONObject, LocatedProp>> nodeAttrExtractors = new HashMap<>();
		nodeAttrExtractors.put(ReportType.PROPERTY_VALUE_DIFF_IN_REFERENCE_RUN.name(), obj -> {
			final JSONObject dets = obj.getJSONObject("details");
			return new LocatedProp(NodeLocator.fromJSON(dets.getJSONObject("subject")),
					Property.fromJSON(dets.getJSONObject("property")));
		});
		nodeAttrExtractors.put(ReportType.NON_IDEMPOTENT_PROPERTY_EQUATION.name(), obj -> {
			final JSONObject dets = obj.getJSONObject("details");
			return new LocatedProp(NodeLocator.fromJSON(dets.getJSONObject("subject")),
					Property.fromJSON(dets.getJSONObject("property")));
		});
		nodeAttrExtractors.put(ReportType.NON_IDEMPOTENT_PROPERTY_EQUATION_AFTER_RESET.name(),
				nodeAttrExtractors.get(ReportType.NON_IDEMPOTENT_PROPERTY_EQUATION.name()));

		final Set<String> alreadyExplainedKeys = new HashSet<>();
		final Set<String> attemptedExplainedKeys = new HashSet<>();
		final List<Runnable> dumpPerturberResults = new ArrayList<>();
		ReferenceValues lastRefVals = null;
		int lastToolIdx = -1;
		ReproDb lastRepDb = null;

		System.out.println("Num reports: " + reportsContents.length() + "; req explanations: "
				+ Arrays.toString(requestedExplanations));
		for (int repIdx = 0; repIdx < reportsContents.length(); ++repIdx) {
			final JSONObject rep = reportsContents.getJSONObject(repIdx);

			final String type = rep.getString("type");
			final Function<JSONObject, LocatedProp> extractor = nodeAttrExtractors.get(type);
			if (extractor == null) {
				// Not an issue type that needs explanation
				continue;
			}

			final LocatedProp subject = extractor.apply(rep);
			final String key = subject.locator.result.type + "." + subject.prop.name;
			if (alreadyExplainedKeys.contains(key)) {
				continue;
			}
			boolean shouldAttemptExplanation = requestedExplanations.length == 0;
			for (int j = 0; j < requestedExplanations.length; ++j) {
				if (key.endsWith(requestedExplanations[j])) {
					shouldAttemptExplanation = true;
				}
			}
			if (!shouldAttemptExplanation) {
				continue;
			}
			attemptedExplainedKeys.add(key);

			System.out.println("Searching for explanation for " + type + " -> " + key);
			ReferenceValues newRefVals = null;
			ReproDb newRepDb = null;
			if (lastRefVals != null) {
				if (lastToolIdx == -1 || lastToolIdx == rep.optInt("toolIdx", 0)) {
					newRefVals = lastRefVals;
					newRepDb = lastRepDb;
				}
			}
			if (newRefVals == null) {
				config.setActiveConfigIndex(rep.has("toolIdx") ? rep.getInt("toolIdx") : 0);
				System.out.println("Establishing dependency graph..");
				CreateLocator.identityLocatorCache = new IdentityHashMap<>();
				newRefVals = EstablishReferenceValues.doit(config);
				newRepDb = new ReproDb(newRefVals, new HashMap<>());
				lastRefVals = newRefVals;
				lastToolIdx = config.getActiveToolConfigIdx();
				lastRepDb = newRepDb;
			}
			CreateLocator.identityLocatorCache = null;

			System.out.println("Now, actual search time");
			final PerturbedValueSearchResult perturber = MinimizeRepros.findIntermediatePertuberSteps(subject,
					newRepDb);
			if (perturber == null) {
				System.out.println("Failed producing perturber list for this instance of " + key
						+ ", trying next one (if any next one exists)");
				continue;
			} else {
				alreadyExplainedKeys.add(key);
				final Runnable explainer = () -> {
					System.out.println("Found " + perturber.steps.size() + " perturbation step(s) for " + key);
					for (int i = 0; i < perturber.steps.size(); ++i) {
						final LocatedProp per = perturber.steps.get(i);
						final String perType = per.locator.result.type;
						final String[] typeParts = perType.split("\\.");

						System.out.printf("[%7d]: %s.%s at %s%n", i,
								typeParts.length == 0 ? perType : typeParts[typeParts.length - 1],
								per.prop.name + ((per.prop.args == null || per.prop.args.isEmpty()) ? ""
										: ("(" + per.prop.args.stream().map(x -> LocatorStepToStr.propArgToStr(x))
												.collect(Collectors.joining(", ")) + ")")),
								LocatorStepToStr.stepsToStr(per.locator.steps));
					}
					final String perType = subject.locator.result.type;
					final String[] typeParts = perType.split("\\.");
					System.out.printf("[subject]: %s.%s at %s%n",
							typeParts.length == 0 ? perType : typeParts[typeParts.length - 1],
							subject.prop.name + ((subject.prop.args == null || subject.prop.args.isEmpty()) ? ""
									: ("(" + subject.prop.args.stream().map(x -> LocatorStepToStr.propArgToStr(x))
											.collect(Collectors.joining(", ")) + ")")),
							LocatorStepToStr.stepsToStr(subject.locator.steps));

					System.out.println("    Fresh Value: " + perturber.fresh);
					System.out.println("Perturbed Value: " + perturber.perturbed);
				};
				dumpPerturberResults.add(explainer);
				System.out.println("Found " + perturber.steps.size() + " perturbation step(s)");

				rep.getJSONObject("details").put("intermediateSteps",
						new JSONArray(perturber.steps.stream().map(x -> x.toJSON()).collect(Collectors.toList())));

				String testDst = System.getProperty("tragdor.test.dst");
				if (testDst != null) {
					final File tgtDir = new File(testDst);
					tgtDir.mkdirs();
					Files.write(new File(tgtDir, "config.json").toPath(),
							config.getRootConfig().toString().getBytes(StandardCharsets.UTF_8),
							StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
					genTest(tgtDir, lastToolIdx, subject, perturber.steps);
				}
			}
		}
		for (String key : attemptedExplainedKeys) {
			if (!alreadyExplainedKeys.contains(key)) {
				System.out.println("Could not explain " + key);
			}
		}
		for (Runnable r : dumpPerturberResults) {
			System.out.println();
			r.run();
		}
		System.out.printf("Found explanations for %d of %d attempted attribute instances%n",
				alreadyExplainedKeys.size(), attemptedExplainedKeys.size());

		Tragdor.saveReportsAsIs(srcReportFile, rootReportObj);
		System.out.println("Updated " + srcReportFile);
	}

	private static void genTest(File file, int activeToolIdx, LocatedProp subject, List<LocatedProp> steps)
			throws IOException {
		if (file.isFile()) {
			System.err.println("Test gen target dir is a file");
		}
		file.mkdirs();

		final String[] typeParts = subject.locator.result.type.split("\\.");
		final String testName = String.format("%s_%s",
				typeParts.length == 0 ? subject.locator.result.type : typeParts[typeParts.length - 1], //
				subject.prop.name);
		final File tgtFile = new File(file, testName + ".java");

		TestGenerator gen = new TestGenerator();
		gen.generate(testName, activeToolIdx, subject, steps);
		Files.write(tgtFile.toPath(), gen.sb.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}

	private static class TestGenerator {
		final StringBuilder sb = new StringBuilder();

		void addl(String line) {
			sb.append(line + "\n");
		}

		void addf(String fmt, Object... args) {
			sb.append(String.format(fmt, args));
		}

		private static String escapeString(String src) {
			return src.replace("\"", "\\\"");
		}

		private String json(JSONObject obj) {
			return String.format("new JSONObject(\"%s\")", escapeString(obj.toString()));
		}

		void generate(String testName, int activeToolIdx, LocatedProp subject, List<LocatedProp> steps) {
			addl("package tragdor;");
			addl("");
			addl("import tragdor.config.UserConfig;");
			addl("import tragdor.EvaluatedValue;");
			addl("import tragdor.PropEvaluation;");
			addl("import tragdor.LocatedProp;");
			addl("import codeprober.AstInfo;");
			addl("import org.json.JSONObject;");
			addl("import org.junit.Test;");
			addl("import java.io.IOException;");
			addl("");
			addl("import static org.junit.Assert.assertEquals;");
			addl("");
			addf("public class %s {%n", testName);
			addl("");
			addl("  @Test");
			addl("  public void run() throws IOException {");
			addl("    final java.io.ByteArrayOutputStream configLoader = new java.io.ByteArrayOutputStream();");
			addl("    final byte[] buf = new byte[512];");
			addl("    int readBytes;");
			addl("    try (java.io.InputStream src = getClass().getResourceAsStream(\"config.json\")) {");
			addl("      while ((readBytes = src.read(buf)) != -1) { configLoader.write(buf, 0, readBytes); }");
			addl("    }");
			addl("    final byte[] configBytes = configLoader.toByteArray();");
			addl("    UserConfig cfg = new UserConfig(new JSONObject(new String(configBytes, 0, configBytes.length, java.nio.charset.StandardCharsets.UTF_8)));");
//			addf("    UserConfig cfg = new UserConfig(%s);%n", json(config.getRootConfig()));
			if (activeToolIdx > 0) {
				addf("    cfg.setActiveConfigIndex(%d);%n", activeToolIdx);
			}
			addf("    LocatedProp subject = LocatedProp.fromJSON(%s);%n", json(subject.toJSON()));
			addl("    // Establish reference value on a fresh AST");
			addl("    EvaluatedValue reference = PropEvaluation.evaluateProp(cfg.reparse(), subject);");
			if (steps.isEmpty()) {
				// Repeatedly
				addl("    // Repeatedly re-invoke to detect flakyness");
				addl("    for (int repeat = 0; repeat < 16; ++repeat) {");
				addl("      EvaluatedValue reeval = PropEvaluation.evaluateProp(cfg.reparse(), subject);");
				addf("      assertEquals(\"Failed test for %s\", reference, reeval);%n", testName);
				addl("    }");
				addl("    // It passed 16 iterations. This is unfortunately no guarantee of non-flakyness,");
				addl("    // but it is very likely to be OK. Increase '16' to get higher confidence.");
			} else {
				addl("    AstInfo info = cfg.reparse();");
				addl("    // Apply intermediate steps");
				for (LocatedProp intermediate : steps) {
					addf("    PropEvaluation.evaluateProp(info, LocatedProp.fromJSON(%s));%n",
							json(intermediate.toJSON()));
				}
				addl("    // Re-evaluate on mutated AST");
				addl("    EvaluatedValue reeval = PropEvaluation.evaluateProp(info, subject);");
				addf("    assertEquals(\"Failed Test for %s\", reference, reeval);%n", testName);
			}
			addl("  }");
			addl("");
			addl("}");
		}
	}
}
