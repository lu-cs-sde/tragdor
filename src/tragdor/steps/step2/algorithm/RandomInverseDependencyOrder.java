package tragdor.steps.step2.algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import tragdor.LocatedProp;
import tragdor.steps.step1.DependencyGraphNode;
import tragdor.steps.step2.CycleBasedRandomPropSearch;

public class RandomInverseDependencyOrder extends CycleBasedRandomPropSearch {

	private List<DependencyGraphNode> leafNodeList;

	public RandomInverseDependencyOrder(CycleSearchParams params) {
		super(params);
	}

	@Override
	protected void doRun() throws Exception {
		leafNodeList = new ArrayList<>();
		for (DependencyGraphNode node : refValues.getDependencyGraphNodes()) {
			if (node.outgoingEdges.isEmpty()) {
				leafNodeList.add(node);
			}
		}
		if (leafNodeList.isEmpty()) {
			System.err.println("No leaf nodes in the dependency graph");
			return;
		}

		super.doRun();
	}

	@Override
	protected List<LocatedProp> getCycleProps() {
		Map<LocatedProp, DependencyGraphNode> freshNodeCache = new HashMap<>();
		final Function<LocatedProp, DependencyGraphNode> createFreshNode = lp -> {
			final DependencyGraphNode cached = freshNodeCache.get(lp);
			if (cached != null) {
				return cached;
			}
			final DependencyGraphNode fresh = new DependencyGraphNode(lp);
			freshNodeCache.put(lp, fresh);
			return fresh;

		};
		List<DependencyGraphNode> allnodes = new ArrayList<>();
		for (DependencyGraphNode node : refValues.getDependencyGraphNodes()) {
			allnodes.add(node.copy(createFreshNode));
		}

		// Begin Kahn's algorithm, modified to work in reverse
		// See https://en.wikipedia.org/wiki/Topological_sorting#Kahn's_algorithm

		// L ← Empty list that will contain the sorted elements
		final List<LocatedProp> L = new ArrayList<>();
		// S ← Set of all nodes with no incoming edge
		final List<DependencyGraphNode> S = new ArrayList<>();
		for (DependencyGraphNode node : allnodes) {
			if (node.outgoingEdges.isEmpty()) {
				S.add(node);
			}
		}

		// while S is not empty do
		while (!S.isEmpty()) {
			// remove a node n from S
			final DependencyGraphNode n = S.remove(rng.nextInt(S.size()));
			// add n to L
			L.add(n.identity);
			// for each node m with an edge e from n to m do
			for (DependencyGraphNode m : n.incomingEdges) {
				// remove edge e from the graph
				m.outgoingEdges.remove(n);
				// if m has no other incoming edges then
				if (m.outgoingEdges.isEmpty()) {
					// insert m into S
					S.add(m);
				}
			}
			n.incomingEdges.clear();
		}

		// if graph has edges then
		for (DependencyGraphNode node : allnodes) {
			if (!node.incomingEdges.isEmpty() || !node.outgoingEdges.isEmpty()) {
				// return error (graph has at least one cycle)
				System.err.println("Graph has at least one cycle, involving " + node.identity.prop.name);
				System.err.println("Incoming: "
						+ node.incomingEdges.stream().map(x -> x.identity.prop.name).collect(Collectors.toList()));
				System.err.println("Outgoing: "
						+ node.outgoingEdges.stream().map(x -> x.identity.prop.name).collect(Collectors.toList()));
				// Instead of exiting, ignore this cycle. In our testing, there are quite few
				// cycles and it doesn't seem to have an adverse effect on the results
//				Tragdor.exit(1);
			}
		}
		// else
		// return L (a topologically sorted order)
		return L;
	}

}
