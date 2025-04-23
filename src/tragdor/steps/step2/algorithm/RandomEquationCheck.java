package tragdor.steps.step2.algorithm;

import java.util.List;

import tragdor.LocatedProp;
import tragdor.Tragdor;
import tragdor.steps.step2.CycleBasedRandomPropSearch;

public class RandomEquationCheck extends CycleBasedRandomPropSearch {

	public RandomEquationCheck(CycleSearchParams params) {
		super(params);

		// Remove tracing from the AST state, should improve performance
		Tragdor.resetAstState(referenceValueAst.ast.underlyingAstNode);
	}

	@Override
	protected List<LocatedProp> getCycleProps() {
		return refValues.getEvalOrder(rng.nextLong());
	}

	@Override
	protected CheckStyle getCheckStyle() {
		return CheckStyle.KEEP_AST_AND_SPOTCHECK;
	}
}
