package tragdor.contrib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import codeprober.AstInfo;
import codeprober.protocol.data.RpcBodyLine;
import tragdor.EvaluatedValue;
import tragdor.LocatedProp;
import tragdor.PropEvaluation;
import tragdor.Tragdor;
import tragdor.contrib.ReproDb.ReproDbEntry;
import tragdor.report.ReportType;
import tragdor.report.impl.PropertyValueDiffInReferenceCompileReport;
import tragdor.report.impl.PropertyValueDiffReport;
import tragdor.steps.step1.EstablishReferenceValues.ReferenceValues;
import tragdor.util.Benchmark;

public class MinimizeRepros {

	public static class PerturbedValueSearchResult {
		public final List<LocatedProp> steps;
		public final EvaluatedValue fresh;
		public final EvaluatedValue perturbed;

		public PerturbedValueSearchResult(List<LocatedProp> steps, EvaluatedValue fresh, EvaluatedValue perturbed) {
			this.steps = steps;
			this.fresh = fresh;
			this.perturbed = perturbed;
		}
	}

	public static PerturbedValueSearchResult findIntermediatePertuberSteps(LocatedProp subject, ReproDb repDb) {
		System.out.println("== Minimizing issue " + subject.locator.result.type + " . " + subject.prop.name);

		// First, make sure that this isn't simply a flaky attribute
		final ReproDbEntry dbEnt = repDb.get(subject);
		final EvaluatedValue freshVal = dbEnt.getFreshValue();
		for (int flakyAttempt = 0; flakyAttempt < 5 /* arbitrary limit */; ++flakyAttempt) {
			final AstInfo ast = repDb.getConfig().reparse();
			final EvaluatedValue repeat = PropEvaluation.evaluateProp(ast, subject);

			if (!freshVal.equals(repeat)) {
				// It was flaky!
				return new PerturbedValueSearchResult(Collections.emptyList(), freshVal, repeat);
			}

		}

		int siblingSizeLimit = 4096;

		for (int distance = 1; distance < 8 /* very arbitrary limit */; ++distance) {

			final Set<LocatedProp> siblings = dbEnt.searchForSiblings(distance);
			System.out.println("At distance " + distance + ", found " + siblings.size() + " siblings to prop");
			if (siblings.size() > siblingSizeLimit /* arbitrary limit */) {
				System.out.println("Too many nodes in sibling-search neighborhood, exiting early");
				break;
			} else if (siblings.isEmpty()) {
				// Maybe some siblings further up
				continue;
			} else {
				if (siblings.size() == 1) {
					// Ideal situation, just one edge apart from subject itself. Test it
					final LocatedProp single = siblings.iterator().next();
					final EvaluatedValue oneOff = dbEnt.evalWithSingleIntermediateProp(single);
					if (!oneOff.equals(freshVal)) {
						System.out.println("Found one-off reproduction at distance " + distance);
						System.out.println("Perturbation prop: " + single);
						return new PerturbedValueSearchResult(Arrays.asList(single), freshVal, oneOff);
					}
				} else {
					// More than one..
					// TODO if too many siblings, abort. For example, if we find more siblings here
					// than the "diff.listIndex", then we should test whether the props
					// 0..diff.listIndex manages to reproduce the issue, in which case it is a
					// better avenue for searching.
					System.out.println("Going to try these siblings a few times");
					for (int time = 0; time < 5 /* arbitrary times */; ++time) {
						final List<LocatedProp> siblingEvalOrder = new ArrayList<>(siblings);
						Collections.shuffle(siblingEvalOrder);
//						final AstInfo ast = Dynsed.reparse();
//						for (LocatedProp sib : siblingEvalOrder) {
//							PropEvaluation.evaluateProp(ast, sib);
//						}
						final EvaluatedValue mutVal = dbEnt.evalWithIntermediateProps(siblingEvalOrder);
						if (!mutVal.equals(freshVal)) {
							System.out.println("Found reproduction at distance " + distance + "; w/ " + siblings.size()
									+ " siblings");

							System.out.println("Going to try reducing the list..");
							List<LocatedProp> reducedList = IntermediateStepReducer.reduce(siblingEvalOrder,
									dbEnt::producesDifferentValueWithIntermediates);
							final List<LocatedProp> reportList = reducedList != null ? reducedList : siblingEvalOrder;
							return new PerturbedValueSearchResult(reportList, freshVal,
									dbEnt.evalWithIntermediateProps(reportList));
						}
					}
				}
			}
		}
		return null;
	}

	public static void doit(ReferenceValues refVals, Map<LocatedProp, PricedReport> explainedIssues,
			Map<Long, List<ReferenceValueDiff>> detectedIssues) throws Exception {
//		int numZeroes = 0;
//		int numNonZero = 0;
//		for (DependencyGraphNode node : refVals.getDependencyGraphNodes()) {
//			if (node.incomingEdges.size() == 0) {
//				++numZeroes;
//			} else {
//				++numNonZero;
//			}
//		}
//		System.out.println("Dep graph incomings: 0=" + numZeroes + "; !0=" + numNonZero);
//		for (DependencyGraphNode node : refVals.getDependencyGraphNodes()) {
//			if (node.identity.prop.name.equals("bytecodes")) {
//				System.out.println("Exploring from bytecodes.. #out: " + node.outgoingEdges.size());
//				for (DependencyGraphNode out : node.outgoingEdges) {
//					System.out.println("out: " + out.identity.locator.result.type + " . " + out.identity.prop.name);
//				}
//			}
//		}

//		final Map<Long, List<ReferenceValueDiff>> allLoadedIssues = LoadIssues.load();
		final ReproDb repDb = new ReproDb(refVals, detectedIssues);
		final Map<Long, List<ReferenceValueDiff>> issuesToReproduce = LoadIssues.removeDuplicates(refVals,
				detectedIssues, explainedIssues);
//		final Map<LocatedProp, PricedReport> issueReproductions = new HashMap<>();
		final BiConsumer<LocatedProp, PricedReport> onFoundReproduction = (prop, repo) -> {
			PricedReport previousRepro = explainedIssues.get(prop);
			if (previousRepro != null && previousRepro.cost < repo.cost) {
				// Ignore, already have better repro
			} else {
				System.out.println("-> Achieved repro " + repo.cost + " for issue " + prop.locator.result.type + " . "
						+ prop.prop.name);
				explainedIssues.put(prop, repo);
			}
		};
		final Predicate<LocatedProp> hasAchievedMinimalReproduction = (prop) -> {
			final PricedReport prev = explainedIssues.get(prop);
			return prev != null && prev.cost <= 1;
		};

		int totalNumIssuesToMinimize = 0;
		for (Entry<Long, List<ReferenceValueDiff>> seededIssues : issuesToReproduce.entrySet()) {
			totalNumIssuesToMinimize += seededIssues.getValue().size();
		}
		System.out.println("Num issues: " + totalNumIssuesToMinimize);

		/**
		 * Overall goal is to produce "1-off" issue reproductions. I.e for each issue
		 * prop ("subject"), find a single other ("intermediate") prop that causes the
		 * issue prop to show a side effect.
		 * <p>
		 * In code terms, we want to produce a test case that looks like this:
		 *
		 * <pre>
		 * 	 Object freshVal = freshParse().eval("subject")
		 *
		 *   Ast ast = freshParse();
		 *   ast.eval("intermediate");
		 *   Object mutVal = ast.eval("subject");
		 *
		 *   assertEquals(freshVal, mutVal); // assertion fails
		 * </pre>
		 *
		 * We already know what "subject" is, it was found previously by
		 * {@link RandomOrder}. In the code below, we try to find "intermediate", or a
		 * list of intermediates.
		 */

		// Phase 1: try looking for "intermediates" in the dependency graph
		final int ftotalNumIssuesToMinimize = totalNumIssuesToMinimize;
		Benchmark.tickTockErrv("MinimizeRepro::Phase1", () -> {
			int issueReproductionIdx = -1;
			for (Entry<Long, List<ReferenceValueDiff>> seededIssues : issuesToReproduce.entrySet()) {
				final List<LocatedProp> evalOrder = refVals.getEvalOrder(seededIssues.getKey());

				for (ReferenceValueDiff diff : seededIssues.getValue()) {
					++issueReproductionIdx;
					final LocatedProp subject = evalOrder.get(diff.listIndex);
					System.out.println("== Minimizing issue " + issueReproductionIdx + "/" + ftotalNumIssuesToMinimize
							+ " : " + subject.locator.result.type + " . " + subject.prop.name);

					final ReproDbEntry dbEnt = repDb.get(subject);
					final EvaluatedValue freshVal = dbEnt.getFreshValue();

					int siblingSizeLimit = 4096; // , diff.listIndex + 1);
					if (!freshVal.equals(diff.nonReferenceValue)) {
						if (diff.listIndex < siblingSizeLimit) {
							siblingSizeLimit = diff.listIndex;
							System.out.println("Setting sibling neighborhood size limit to " + siblingSizeLimit
									+ ", since randomSearchVal found a diff at that length");
						}
					}

					for (int distance = 1; distance < 5
							/* arbitrary limit */ && !hasAchievedMinimalReproduction.test(subject); ++distance) {

						final Set<LocatedProp> siblings = dbEnt.searchForSiblings(distance);
						System.out.println(
								"At distance " + distance + ", found " + siblings.size() + " siblings to prop");
						if (siblings.size() > siblingSizeLimit /* arbitrary limit */) {
							System.out.println("Too many nodes in sibling-search neighborhood, exiting early");
							break;
						} else if (siblings.isEmpty()) {
							break;
						} else {
							if (siblings.size() == 1) {
								// Ideal situation, just one edge apart from subject itself. Test it
								final LocatedProp single = siblings.iterator().next();
								final EvaluatedValue oneOff = dbEnt.evalWithSingleIntermediateProp(single);
								if (!oneOff.equals(freshVal)) {
									System.out.println("Found one-off reproduction at distance " + distance);
									System.out.println("Perturbation prop: " + single);
									onFoundReproduction.accept(subject,
											new PricedReport(distance, subject, new PropertyValueDiffReport(subject,
													freshVal.value, Arrays.asList(single), oneOff.value)));
									break;
								}
							} else {
								// More than one..
								// TODO if too many siblings, abort. For example, if we find more siblings here
								// than the "diff.listIndex", then we should test whether the props
								// 0..diff.listIndex manages to reproduce the issue, in which case it is a
								// better avenue for searching.
								System.out.println("Going to try these siblings a few times");
								for (int time = 0; time < 5 /* arbitrary times */; ++time) {
									final List<LocatedProp> siblingEvalOrder = new ArrayList<>(siblings);
									Collections.shuffle(siblingEvalOrder);
									final EvaluatedValue mutVal = dbEnt.evalWithIntermediateProps(siblingEvalOrder);
									if (!mutVal.equals(freshVal)) {
										System.out.println("Found reproduction at distance " + distance + "; w/ "
												+ siblings.size() + " siblings");

										System.out.println("Going to try reducing the list..");
										List<LocatedProp> reducedList = IntermediateStepReducer.reduce(siblingEvalOrder,
												dbEnt::producesDifferentValueWithIntermediates);
										final List<LocatedProp> reportList = reducedList != null ? reducedList
												: siblingEvalOrder;
										onFoundReproduction.accept(subject,
												new PricedReport(reportList.size(), subject,
														new PropertyValueDiffReport(subject, freshVal.value, reportList,
																mutVal.value)));
										break;
									}
								}
							}
						}
					}
				}
			}
		});

		System.out.println("Done with phase 1, found reproductions for " + explainedIssues.size() + " / "
				+ totalNumIssuesToMinimize + ", of which "
				+ explainedIssues.values().stream().filter(x -> x.cost <= 1).count()
				+ " are perfect (flaky or one-off diffs)");

		// Phase 2: See if the
		Benchmark.tickTockErrv("MinimizeRepro::Phase2", () -> {
			for (Entry<Long, List<ReferenceValueDiff>> seededIssues : issuesToReproduce.entrySet()) {
				final List<LocatedProp> evalOrder = refVals.getEvalOrder(seededIssues.getKey());

				final List<ReferenceValueDiff> remainingIssuesToReproduce = seededIssues.getValue().stream()
						.filter(x -> !hasAchievedMinimalReproduction.test(evalOrder.get(x.listIndex)))
						.collect(Collectors.toList());
				if (remainingIssuesToReproduce.isEmpty()) {
					continue;
				}

				System.out.println("Phase 2 :: seed " + seededIssues.getKey() + " has " + seededIssues.getValue().size()
						+ " issues without perfect test cases. Searching along the original evaluation order");

				for (ReferenceValueDiff diff : remainingIssuesToReproduce) {
					final LocatedProp subject = evalOrder.get(diff.listIndex);

					System.out.println("Phase 2, working on " + subject);

					final ReproDbEntry dbEnt = repDb.get(subject);
//					final EvaluatedValue refVal = refVals.getReferenceValue(subject);
					final EvaluatedValue freshVal = dbEnt.getFreshValue();
					final List<LocatedProp> subList = diff.listIndex == 0 ? Collections.emptyList()
							: evalOrder.subList(0, diff.listIndex - 1);
					final EvaluatedValue mutVal = diff.nonReferenceValue; // dbEnt.evalWithIntermediateProps(subList);
					final boolean alreadyHasSomeKindOfReproduction = explainedIssues.containsKey(subject);

					if (!freshVal.equals(mutVal)) {
						if (alreadyHasSomeKindOfReproduction) {
							final int preCost = explainedIssues.get(subject).cost;
							if (preCost < diff.listIndex - 1) {
								System.out.println("Previously found reproduction is smaller for prop: " + subject);
								System.out.println("Prev cost: " + preCost + ", new cost: " + diff.listIndex);
								// Could still try to minimize it..

								List<LocatedProp> reducedList = IntermediateStepReducer.reduce(subList,
										dbEnt::producesDifferentValueWithIntermediates);

								if (reducedList != null) {
									if (reducedList.size() < preCost) {
										System.out.println("Managed to create a smaller reproduction!");
										onFoundReproduction.accept(subject,
												new PricedReport(reducedList.size(), subject,
														new PropertyValueDiffReport(subject, freshVal.value,
																reducedList, mutVal.value)));
									}
								}
								continue;
							}
						}
						final List<LocatedProp> reducedList = IntermediateStepReducer.reduce(subList,
								dbEnt::producesDifferentValueWithIntermediates);

						final List<LocatedProp> reportList = reducedList != null ? reducedList : subList;
						if (reportList.size() < 4096) {
							onFoundReproduction.accept(subject, new PricedReport(reportList.size(), subject,
									new PropertyValueDiffReport(subject, freshVal.value, reportList, mutVal.value)));
							System.out.println("Found phase 2 issue!");
							System.out.println(" Fresh: " + freshVal);
							System.out.println("   Mut: " + mutVal);
						} else {
							System.out.println(
									"Phase 2 reproduction attempt found a reproduction with length" + reportList.size()
											+ ". Unfortunately, this is o-too large to report. Fallback to default "
											+ ReportType.PROPERTY_VALUE_DIFF_IN_REFERENCE_RUN + " report");
						}
					} else if (!alreadyHasSomeKindOfReproduction) {
						System.out.println("Fresh->FoundDiff are equal, but not equal to the reference value");
						System.out.println("This is likely the hardest case to reproduce");
					}

				}
			}
		});

		// Phase 3, generate low-quality reports for everythings without reproductions
		for (Entry<Long, List<ReferenceValueDiff>> seededIssues : issuesToReproduce.entrySet()) {
			final List<LocatedProp> evalOrder = refVals.getEvalOrder(seededIssues.getKey());

			final List<ReferenceValueDiff> remainingIssuesToReproduce = seededIssues.getValue().stream()
					.filter(x -> !hasAchievedMinimalReproduction.test(evalOrder.get(x.listIndex)))
					.collect(Collectors.toList());

			for (ReferenceValueDiff diff : remainingIssuesToReproduce) {
				final LocatedProp subject = evalOrder.get(diff.listIndex);
				final RpcBodyLine randomSearchVal = diff.nonReferenceValue.value;
				final RpcBodyLine freshVal = repDb.get(subject).getFreshValue().value;
//				if (subject.locator.result.type.endsWith("ArrayTypeAccess") && subject.prop.name.equals("succ")) {
//					System.out.println("TIME!!");
//					final RpcBodyLine fresherVal = repDb.get(subject).getFreshValueWithoutCache().value;
//
//					System.out.println("TIME!!..?");
//					System.out.println("randSearchVal: " + randomSearchVal.toJSON().toString());
//					System.out.println("     freshVal: " + freshVal.toJSON().toString());
//					System.out.println("   fresherVal: " + fresherVal.toJSON().toString());
//					System.out.println("Getting even fresher val..");
//					ASTProvider.purgeCache();
//					final EvaluatedValue freshest = repDb.get(subject).getFreshValueWithoutCache();
//					System.out.println("    Freshest: " + freshest.toJSON().toString());
//
//				}
				onFoundReproduction.accept(subject,
						new PricedReport(Integer.MAX_VALUE, subject,
								new PropertyValueDiffInReferenceCompileReport(subject,
										refVals.getReferenceValue(subject).value, randomSearchVal, // TODO fresh ->
																									// nonReferenceValue
																									// here, update UI
																									// and report labels
										freshVal)));
			}
		}

		final Map<Integer, Integer> reproductionSizeToNumberOfInstances = new HashMap<>();
		for (Entry<LocatedProp, PricedReport> ent : explainedIssues.entrySet()) {
			final int cost = ent.getValue().cost;
			final Integer prev = reproductionSizeToNumberOfInstances.get(cost);
			reproductionSizeToNumberOfInstances.put(cost, (prev == null ? 0 : prev) + 1);
			Tragdor.report(ent.getValue().report);
		}
		Tragdor.saveReports();

		System.out.println("Reproduction sizes mapped to their number of occurrences:");
		final List<Entry<Integer, Integer>> repSizeEnts = new ArrayList<>(
				reproductionSizeToNumberOfInstances.entrySet());
		Collections.sort(repSizeEnts, (a, b) -> a.getKey() - b.getKey());
		for (Entry<Integer, Integer> ent : repSizeEnts) {
			System.out.printf("%4d -> %d%n", ent.getKey(), ent.getValue());
		}
	}

}
