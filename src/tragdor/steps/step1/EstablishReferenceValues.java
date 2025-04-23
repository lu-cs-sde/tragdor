package tragdor.steps.step1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONException;

import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.TALStep;
import tragdor.EvaluatedValue;
import tragdor.LocatedProp;
import tragdor.PropGathering;
import tragdor.Tragdor;
import tragdor.config.EntryPointConfig;
import tragdor.config.UserConfig;
import tragdor.util.Benchmark;

public class EstablishReferenceValues {

	public static ReferenceValues doit(UserConfig config) throws Exception {
		System.out.println("Establishing reference values..");
		final ReferenceValues pags = Benchmark.tickTockErr("gatherAllProps", () -> gatherAllPropsCached(config, true));

		System.out.println("Gathered " + pags.referenceValues.size() + " values..");

		return pags;
	}

	public static ReferenceValues gatherAllPropsCached(UserConfig config, boolean collectTraceValues)
			throws JSONException, IOException {
		final NodeLocator rootLocator = new NodeLocator(new TALStep("<ROOT>", null, 0, 0, 0), Collections.emptyList());

		final HashMap<LocatedProp, EvaluatedValue> values = new HashMap<>();
		PropGathering pg = new PropGathering(values, collectTraceValues, config);

		final boolean[] anyEntryPointThrewAnExceptionPtr = new boolean[] { false };
		final Map<LocatedProp, DependencyGraphNode> depGraphEntries = //
				Benchmark.tickTock("evaluateTraced",
						() -> pg.evaluateTracedWithFullDependencyGraph(rootLocator, (info, tb) -> {
							for (EntryPointConfig entry : config.getEntryPoints()) {
								System.out.println("Gather from " + entry);
								pg.gatherWithoutTracingOrFlushing(info, entry.predicate, 0, Integer.MAX_VALUE,
										entry.limit, entry.property, tb, anyEntryPointThrewAnExceptionPtr);
							}
						}));

		System.out.println("Gathered " + values.size() + " props..");
		if (Tragdor.verbose) {
			Benchmark.tickTock("dumpGraphStatistics", () -> {
				dumpGraphStatistics(depGraphEntries.values());
				return null;
			});
		}
		return new ReferenceValues(config, values, depGraphEntries, anyEntryPointThrewAnExceptionPtr[0]);
	}

	private static void dumpGraphStatistics(Collection<DependencyGraphNode> graph) {
		System.out.println("Number of nodes in dependency graph: " + graph.size());
		System.out.println("Num leaf nodes: "
				+ graph.stream().map(x -> x.outgoingEdges.size()).reduce(0, (a, b) -> a + (b == 0 ? 1 : 0)));
		if (graph.isEmpty()) {
			return;
		}
		final List<Integer> outgoingEdgeCount = graph.stream().map(x -> x.outgoingEdges.size())
				.collect(Collectors.toList());
		Collections.sort(outgoingEdgeCount);
		System.out.println("Min outgoing edge count: " + outgoingEdgeCount.get(0));
		System.out.println("Median outgoing edge count: " + outgoingEdgeCount.get(outgoingEdgeCount.size() / 2));
		System.out.println("Average edge count: "
				+ (outgoingEdgeCount.stream().reduce(0, (a, b) -> a + b) / outgoingEdgeCount.size()));
		System.out.println("Max outgoing edge count: " + outgoingEdgeCount.get(outgoingEdgeCount.size() - 1));

		final List<Integer> incomingEdgeCount = graph.stream().map(x -> x.incomingEdges.size())
				.collect(Collectors.toList());
		Collections.sort(incomingEdgeCount);
		System.out.println("Min incoming edge count: " + incomingEdgeCount.get(0));
		System.out.println("Median incoming edge count: " + incomingEdgeCount.get(incomingEdgeCount.size() / 2));
		System.out.println("Average edge count: "
				+ (incomingEdgeCount.stream().reduce(0, (a, b) -> a + b) / incomingEdgeCount.size()));
		System.out.println("Max incoming edge count: " + incomingEdgeCount.get(incomingEdgeCount.size() - 1));
	}

	public static class ReferenceValues {

		private final UserConfig config;
		private final Map<LocatedProp, EvaluatedValue> referenceValues;
		private final List<LocatedProp> referenceEvalOrder;
		private final Map<LocatedProp, DependencyGraphNode> dependencyGraphNodes;
		private final Map<Long, List<LocatedProp>> evalOrderCache = new HashMap<>();
		public final boolean anyEntryPropThrewAnException;

		private List<DependencyGraphNode> dependencyGraphNodesCache;

		public ReferenceValues(UserConfig config, Map<LocatedProp, EvaluatedValue> values,
				Map<LocatedProp, DependencyGraphNode> dependencyGraphNodes, boolean anyEntryPropThrewAnException) {
			this.config = config;
			this.referenceValues = values;
			this.referenceEvalOrder = new ArrayList<>(values.keySet());
			this.dependencyGraphNodes = dependencyGraphNodes;
			this.anyEntryPropThrewAnException = anyEntryPropThrewAnException;
		}

		public UserConfig getConfig() {
			return config;
		}

		public List<DependencyGraphNode> getDependencyGraphNodes() {
			return new ArrayList<>(dependencyGraphNodes.values());
		}

		public List<DependencyGraphNode> getDependencyGraphRoots() {
			if (dependencyGraphNodesCache == null) {
				final Set<String> entryPointNames = config.getEntryPoints().stream().map(x -> x.property)
						.collect(Collectors.toSet());
				final boolean allowAny = entryPointNames.contains("*");

				dependencyGraphNodesCache = dependencyGraphNodes.values().stream() //
						.filter(x -> x.incomingEdges.isEmpty()
								&& (allowAny || entryPointNames.contains(x.identity.prop.name))) //
						.collect(Collectors.toList());
			}
			return dependencyGraphNodesCache;

		}

		public DependencyGraphNode getDependencyNodeForProp(LocatedProp prop) {
			return dependencyGraphNodes.get(prop);
		}

		public List<LocatedProp> getUnshuffledEvalOrder() {
			return referenceEvalOrder;
		}

		public List<LocatedProp> getEvalOrder(long seed) {
			final List<LocatedProp> cached = evalOrderCache.get(seed);
			if (cached != null) {
				return cached;
			}
			final List<LocatedProp> cpy = new ArrayList<>(referenceEvalOrder);
			Collections.shuffle(cpy, new Random(seed));
			evalOrderCache.put(seed, cpy);
			return cpy;
		}

		public EvaluatedValue getReferenceValue(LocatedProp prop) {
			return referenceValues.get(prop);
		}
	}
}
