package tragdor.contrib;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import tragdor.LocatedProp;

public class IntermediateStepReducer {

	// Assume current intermediate step list does work
	public static List<LocatedProp> reduce(List<LocatedProp> fullIntermediate,
			Predicate<List<LocatedProp>> testSublist) {
		final int numFull = fullIntermediate.size();
		switch (numFull) {
		case 0: {
			return null;
		}
		case 1: {
			return testSublist.test(Collections.emptyList()) ? Collections.emptyList() : null;
		}
		case 2:
		case 3:{
			for (int pos = 0; pos < numFull; ++pos) {
				List<LocatedProp> sub = fullIntermediate.subList(pos, pos + 1);
				if (testSublist.test(sub)) {
					return sub;
				}
			}
			return null;
		}

		}

		// Else, 3 or more. Divide and conquer
		int from = 0;
		int to = numFull;
//		while (to - from > 100) {
//			// First, divide with larger step sizes
//			final int stepSize = (to - from) / 30 /* arbitrary constant */;
//
//		}
		while (from < to) {

			final int cutoff = (to - from) / 2;

			if (cutoff > 0) {
				final int mid = from + cutoff;
				List<LocatedProp> firstHalf = fullIntermediate.subList(from, mid);
				if (testSublist.test(firstHalf)) {
					System.out.println("Reduced list to " + from +".." + mid);
					to = mid;
					continue;
				}

				List<LocatedProp> secondHalf = fullIntermediate.subList(mid, to);
				if (testSublist.test(secondHalf)) {
					System.out.println("Reduced list to " + mid +".." + to);
					from = mid;
					continue;
				}
			}
			break;

		}
		if (from != 0 || to != numFull) {
			final List<LocatedProp> reduced = fullIntermediate.subList(from, to);
			final List<LocatedProp> deeperReduction = reduce(reduced, testSublist);
			return deeperReduction != null ? deeperReduction : reduced;
		}
		return null;
	}
}
