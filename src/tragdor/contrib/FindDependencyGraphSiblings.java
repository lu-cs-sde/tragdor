package tragdor.contrib;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tragdor.LocatedProp;
import tragdor.steps.step1.DependencyGraphNode;

public class FindDependencyGraphSiblings {

	public static Set<LocatedProp> getSiblings(DependencyGraphNode origin, int searchDistance) {
		final Set<LocatedProp> siblings = new HashSet<>();
		final Map<LocatedProp, Integer> upSearches = new HashMap<>();
		final Map<LocatedProp, Integer> downSearches = new HashMap<>();
		gatherSiblingEdges(siblings, upSearches, downSearches, origin, searchDistance, searchDistance);
		siblings.remove(origin.identity);
		return siblings;
	}

	private static void gatherSiblingEdges(Set<LocatedProp> dst, Map<LocatedProp, Integer> upSearchVisited,
			Map<LocatedProp, Integer> downSearchVisited, DependencyGraphNode node, int upSearchLevel,
			int downSearchLevel) {
		if (upSearchLevel > 0) {
			if (node.incomingEdges.isEmpty()) {
				// Add the node itself instead
				dst.add(node.identity);
			}
			for (DependencyGraphNode back : node.incomingEdges) {
				final Integer prevVisit = upSearchVisited.get(back.identity);
				if (prevVisit != null && prevVisit > upSearchLevel) {
					// Already went this way, with more travel budget. No need to go here again
					continue;
				}
				upSearchVisited.put(back.identity, upSearchLevel);
				gatherSiblingEdges(dst, upSearchVisited, downSearchVisited, back, upSearchLevel - 1, downSearchLevel);
			}
		} else if (downSearchLevel > 0) {
			if (node.outgoingEdges.isEmpty()) {
				// It is a leaf node, even if it isn't the "correct depth down"
				// Add it!
				dst.add(node.identity);
			} else {
				for (DependencyGraphNode fwd : node.outgoingEdges) {
					final Integer prevVisit = downSearchVisited.get(fwd.identity);
					if (prevVisit != null && prevVisit > downSearchLevel) {
						// Already went this way, with more travel budget. No need to go here again
						continue;
					}
					downSearchVisited.put(fwd.identity, downSearchLevel);
					gatherSiblingEdges(dst, upSearchVisited, downSearchVisited, fwd, upSearchLevel,
							downSearchLevel - 1);
				}
			}
		} else {
			dst.add(node.identity);
		}
	}
}
