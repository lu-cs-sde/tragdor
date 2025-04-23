package tragdor.steps.step2;

import java.util.Random;

import codeprober.AstInfo;
import tragdor.config.UserConfig;
import tragdor.steps.step1.EstablishReferenceValues.ReferenceValues;

public abstract class CycleBasedSearch {

	public static class CycleSearchParams {
		protected final ReferenceValues refValues;
		protected final long searchBudgetMs;
		private AstInfo referenceValueAst;

		public CycleSearchParams(ReferenceValues refValues, long searchBudgetMs,
				AstInfo referenceValueAst) {
			this.refValues = refValues;
			this.searchBudgetMs = searchBudgetMs;
			this.referenceValueAst = referenceValueAst;
		}
	}

	protected final UserConfig config;
	protected final ReferenceValues refValues;
	protected final long searchBudgetMs;

	private long searchStartMs;
	protected final Random rng;
	protected final AstInfo referenceValueAst;

	public CycleBasedSearch(CycleSearchParams params) {
		this.config = params.refValues.getConfig();
		this.refValues = params.refValues;
		this.searchBudgetMs = params.searchBudgetMs;
		this.rng = new Random();
		this.referenceValueAst = params.referenceValueAst;
	}

	public void run() throws Exception {
		searchStartMs = System.currentTimeMillis();
		doRun();
	}

	protected abstract void doRun() throws Exception;

	protected boolean shouldLogDebugInfoForCycle(int cycleId) {
		return cycleId <= 10 || cycleId == 32 || (cycleId % 64) == 0;
	}

	protected boolean hasRunOverTimeBudget() {
		return (System.currentTimeMillis() - searchStartMs) >= searchBudgetMs;
	}
}
