package tragdor.steps.step2.algorithm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import codeprober.util.ASTProvider;
import tragdor.EvaluatedValue;
import tragdor.LocatedProp;
import tragdor.PropEvaluation;
import tragdor.Tragdor;
import tragdor.config.UserConfig;
import tragdor.report.impl.FlakyPropertyInReferenceRunReport;
import tragdor.report.impl.FlakyPropertyReport;
import tragdor.steps.step1.DependencyGraphNode;
import tragdor.steps.step1.EstablishReferenceValues;
import tragdor.steps.step1.EstablishReferenceValues.ReferenceValues;

public class UserOrder {

	private static final boolean shouldResetGlobalState = !"false"
			.equals(System.getProperty("tragdor.user_order.reset_global_state", "true"));

	public static void doit(ReferenceValues refVals, UserConfig config, long timeoutMs) throws Exception {
		final Set<String> alreadyReportedPropIds = new HashSet<>();

		final List<DependencyGraphNode> referenceGraphRoots = refVals.getDependencyGraphRoots();

		final long startMs = System.currentTimeMillis();

		final Set<LocatedProp> foundFlakyIssues = new HashSet<>();
		final BiConsumer<LocatedProp, EvaluatedValue> onFlakyIssueFound = (prop, repeatVal) -> {
			if (foundFlakyIssues.contains(prop)) {
				return;
			}
			final EvaluatedValue refVal = refVals.getReferenceValue(prop);
			final EvaluatedValue freshVal = PropEvaluation.evaluateProp(config.reparse(), prop);

			// First, check if it is fresh-flaky, or global flaky
			for (int checkRunId = 0; checkRunId < 10; ++checkRunId) {
				final EvaluatedValue cmpVal = PropEvaluation.evaluateProp(config.reparse(), prop);
				if (!freshVal.equals(cmpVal)) {
					// It is flaky in isolation, not just during the full run
					final FlakyPropertyReport rep = new FlakyPropertyReport(prop, refVal.value, repeatVal.value);
					if (Tragdor.report(rep)) {
						System.out.println("Found fresh-flaky prop " + prop);
						System.out.println(" First val: " + refVal);
						System.out.println("Second val: " + repeatVal);
						alreadyReportedPropIds.add(prop.locator.result.type + " + " + prop.prop.name);
						foundFlakyIssues.add(prop);
					}
					return;
				}
			}

			final FlakyPropertyReport rep = new FlakyPropertyInReferenceRunReport(prop, refVal.value, repeatVal.value);
			if (Tragdor.report(rep)) {
				System.out.println("Found reference-run-flaky prop " + prop);
				System.out.println(" First val: " + refVal);
				System.out.println("Second val: " + repeatVal);
				alreadyReportedPropIds.add(prop.locator.result.type + " + " + prop.prop.name);
				foundFlakyIssues.add(prop);
			}
		};

		int numPerformedComparisons = 0;
		for (int repeatRunId = 0; true; ++repeatRunId) {
			if (shouldResetGlobalState) {
				// It is likely that the user is not interested in static field side effects,
				// they want to know if a "normal compile" is deterministic, and "normal
				// compiles" almost exclusively run once and then close the VM.
				// Therefore, purgeCache
				ASTProvider.purgeCache();
			}

			System.out.println("Doing repeat run " + repeatRunId);
			final ReferenceValues repeatVals = EstablishReferenceValues.doit(config);
			final List<DependencyGraphNode> repeatGraphRoots = refVals.getDependencyGraphRoots();

			System.out.println("Comparing initial<->repeat, run " + repeatRunId);
			if (repeatVals.getUnshuffledEvalOrder().size() != refVals.getUnshuffledEvalOrder().size()) {
				System.err.println(
						"Different amount of properties invoked! Reference: " + refVals.getUnshuffledEvalOrder().size()
								+ "; repeat: " + repeatVals.getUnshuffledEvalOrder().size());
			}

			if (referenceGraphRoots.size() != repeatGraphRoots.size()) {
				System.err.println("Different amount of root dependency graph nodes! Reference: "
						+ referenceGraphRoots.size() + "; repeat: " + repeatGraphRoots.size());
			}

			System.out.println("Comparing all props w/ each other instead..");
			for (LocatedProp prop : repeatVals.getUnshuffledEvalOrder()) {
				++numPerformedComparisons;
				final EvaluatedValue repeatVal = repeatVals.getReferenceValue(prop);
				final EvaluatedValue refVal = refVals.getReferenceValue(prop);
				if (refVal == null) {
					System.err.println("Prop only invoked in repeat run, not in reference val: " + prop);
				} else if (!repeatVal.equals(refVal)) {
					onFlakyIssueFound.accept(prop, repeatVal);

				}
			}

			if (System.currentTimeMillis() - startMs > timeoutMs) {
				System.out.println("Has run for long enough to exceed timeout of " + timeoutMs + "ms. Had time for "
						+ numPerformedComparisons + " comparisons");
				break;
			}
		}
	}
}
