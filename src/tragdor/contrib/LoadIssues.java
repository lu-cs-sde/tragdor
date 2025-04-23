package tragdor.contrib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import codeprober.protocol.data.Property;
import tragdor.LocatedProp;
import tragdor.steps.step1.EstablishReferenceValues.ReferenceValues;

public class LoadIssues {

	public static Map<Long, List<ReferenceValueDiff>> removeDuplicates(ReferenceValues refValues,
			Map<Long, List<ReferenceValueDiff>> src, Map<LocatedProp, PricedReport> alreadyFoundIssues) {
		final Function<LocatedProp, String> generateKey = lp -> lp.locator.result.type + " . " + lp.prop.name;
		final Set<String> uniqProps = new HashSet<>();

		// Begin by going through what has already been found with perfect reproductions
		// (=flaky props) and mark as complete, to prevent us from searching through it
		// again.
		alreadyFoundIssues.values().forEach(rep -> {
			if (rep.cost == 0) {
				uniqProps.add(generateKey.apply(rep.relatedProp));
			}
		});
		final Map<Property, SeededDiff> propToBestDiff =  new HashMap<>();

		for (Entry<Long, List<ReferenceValueDiff>> ent : src.entrySet()) {

			final long seed = ent.getKey();
			final List<LocatedProp> evalOrder = refValues.getEvalOrder(seed);
			for (ReferenceValueDiff diff : ent.getValue()) {
				final LocatedProp prop = evalOrder.get(diff.listIndex);


				SeededDiff prev = propToBestDiff.get(prop.prop);
				if (prev == null || diff.listIndex < prev.diff.listIndex) {
					propToBestDiff.put(prop.prop, new SeededDiff(seed, diff));
				}
			}
		}
		Map<Long, List<ReferenceValueDiff>> ret = new HashMap<>();
		for (SeededDiff diff : propToBestDiff.values()) {
			final long seed = diff.seed;
			final List<LocatedProp> evalOrder = refValues.getEvalOrder(seed);

			final LocatedProp lprop = evalOrder.get(diff.diff.listIndex);
			if (!uniqProps.add(generateKey.apply(lprop))) {
				continue;
			}
			List<ReferenceValueDiff> list = ret.get(seed);
			if (list == null) {
				list = new ArrayList<>();
				ret.put(seed, list);
			}
			list.add(diff.diff);
		}

		return ret;
	}

	private static class SeededDiff {
		public final long seed;
		public final ReferenceValueDiff diff;

		public SeededDiff(long seed, ReferenceValueDiff diff) {
			this.seed = seed;
			this.diff = diff;
		}
	}
}
