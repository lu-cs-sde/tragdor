package tragdor.steps.step2.algorithm;

import java.util.List;

import tragdor.LocatedProp;
import tragdor.steps.step2.CycleBasedRandomPropSearch;

public class RandomOrder extends CycleBasedRandomPropSearch {

	public RandomOrder(CycleSearchParams params) {
		super(params);
	}

	@Override
	protected List<LocatedProp> getCycleProps() {
		return refValues.getEvalOrder(rng.nextLong());
	}
}
