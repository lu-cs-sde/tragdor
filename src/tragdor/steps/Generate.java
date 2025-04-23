package tragdor.steps;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

import codeprober.locator.CreateLocator;
import tragdor.Tragdor;
import tragdor.config.ToolConfig;
import tragdor.config.UserConfig;
import tragdor.steps.step1.EstablishReferenceValues;
import tragdor.steps.step1.EstablishReferenceValues.ReferenceValues;
import tragdor.steps.step2.CycleBasedSearch.CycleSearchParams;
import tragdor.steps.step2.algorithm.RandomEquationCheck;
import tragdor.steps.step2.algorithm.RandomInverseDependencyOrder;
import tragdor.steps.step2.algorithm.RandomOrder;
import tragdor.steps.step2.algorithm.UserOrder;
import tragdor.util.Benchmark;
import tragdor.util.Exit;

public class Generate {

	public static void doit(UserConfig config) throws Exception {
		long totalNumMillisPerSearch = (long) (1000.0 * Double.parseDouble( //
				System.getProperty("tragdor.search_budget_sec", //
						System.getProperty("search_budget_sec", //
								config.getTragdorConfig().optString("search_budget_sec", "60")))));

		final List<ToolConfig> toolCfgs = config.getToolConfigs();
		int toolCfgFrom = 0;
		int toolCfgTo = toolCfgs.size();
		if (toolCfgs.size() > 1) {
			Integer workerId = Tragdor.getWorkerId();
			if (workerId != null) {
				Integer numWorkers = Tragdor.getTotalNumWorkers();
				if (numWorkers == null) {
					System.err.println("Missing 'numWorkers' when 'workerId' is set");
					Exit.exit(1);
				}
				final int sliceSize = Math.max(toolCfgs.size() / numWorkers, 1);
				toolCfgFrom = (workerId * sliceSize);
				/* If last worker, take the rest of the work */
				toolCfgTo = (workerId.intValue() == (numWorkers - 1)) //
						? toolCfgs.size()
						: Math.min(toolCfgFrom + sliceSize, toolCfgs.size());
				;
				System.out.println("Worker " + workerId + " is taking work [" + toolCfgFrom + ".." + toolCfgTo
						+ "] from " + toolCfgs.size());
				totalNumMillisPerSearch *= numWorkers;
			}
		}

		for (int toolIdx = toolCfgFrom; toolIdx < toolCfgTo; ++toolIdx) {
			config.setActiveConfigIndex(toolIdx);
			CreateLocator.identityLocatorCache = new IdentityHashMap<>();

			if (toolCfgs.size() > 1) {
				System.out.println("Running tool # " + (toolIdx + 1) + " / " + toolCfgs.size() + " , args: "
						+ Arrays.toString(toolCfgs.get(toolIdx).args));
			}
			final ReferenceValues refVals;

			try {
				refVals = Benchmark.tickTockErr("EstablishReferenceValues",
						() -> EstablishReferenceValues.doit(config));
			} catch (Exception e) {
				System.err.println("Failed estabishing reference values for " + toolCfgs.get(toolIdx));
				System.out.println("Continuing to next tool..");
				continue;
			}

			if (refVals.anyEntryPropThrewAnException) {
				System.out.println(
						"Not performing search with this tool config - entrypoint attribute threw an exception");
				continue;
			}

			final long numMillisPerSearch = totalNumMillisPerSearch / toolCfgs.size();

			final CycleSearchParams params = new CycleSearchParams(refVals, numMillisPerSearch,
					config.getMostRecentParseResult());
			switch (config.getSearchAlgorithm()) {
			case "global_random_order": // Old name, fall through
			case "random_order": {
				Benchmark.tickTockErrv(RandomOrder.class.getSimpleName(), () -> new RandomOrder(params).run());
				break;
			}
			case "global_user_order": // Old name, fall-through
			case "user_order": {
				Benchmark.tickTockErrv(UserOrder.class.getSimpleName(),
						() -> UserOrder.doit(refVals, config, numMillisPerSearch));
				break;
			}
			case "spotcheck_random_order": // Old name, fall-through
			case "rec":
			case "random_equation_check": {
				Benchmark.tickTockErrv(RandomEquationCheck.class.getSimpleName(),
						() -> new RandomEquationCheck(params).run());

				break;
			}
			case "depgraphoutgoing_random_order": // Old name, fall-through
			case "rido":
			case "random_inverse_dependency_order": {
				Benchmark.tickTockErrv(RandomInverseDependencyOrder.class.getSimpleName(),
						() -> new RandomInverseDependencyOrder(params).run());
				break;
			}

			default: {
				System.out.println("Unexpected value for 'search_algorithm'");
				throw new Error("Bad search_algorithm");
			}

			}
			Tragdor.saveReports();
		}
	}
}
