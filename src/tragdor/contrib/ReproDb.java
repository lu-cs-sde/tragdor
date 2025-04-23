package tragdor.contrib;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import codeprober.AstInfo;
import codeprober.locator.CreateLocator;
import codeprober.util.ASTProvider;
import tragdor.EvaluatedValue;
import tragdor.LocatedProp;
import tragdor.PropEvaluation;
import tragdor.config.UserConfig;
import tragdor.steps.step1.DependencyGraphNode;
import tragdor.steps.step1.EstablishReferenceValues.ReferenceValues;

public class ReproDb {

	private final ReferenceValues rvas;
	private final Map<LocatedProp, ReproDbEntry> entries = new HashMap<>();

	private final Map<LocatedProp, ReferenceValueDiff> rvDiffs = new HashMap<>();

	public ReproDb(ReferenceValues rvas, Map<Long, List<ReferenceValueDiff>> loadedIssues) {
		this.rvas = rvas;

		for (Entry<Long, List<ReferenceValueDiff>> ent : loadedIssues.entrySet()) {

			final List<LocatedProp> order = rvas.getEvalOrder(ent.getKey());
			for (ReferenceValueDiff diff : ent.getValue()) {
				rvDiffs.put(order.get(diff.listIndex), diff);
			}
		}
	}

	public UserConfig getConfig() {
		return rvas.getConfig();
	}

	public ReproDbEntry get(LocatedProp key) {
		final ReproDbEntry cached = entries.get(key);
		if (cached != null) {
			return cached;
		}

		final ReproDbEntry fresh = new ReproDbEntry(key);
		entries.put(key, fresh);
		return fresh;
	}

	public class ReproDbEntry {

		private final LocatedProp subject;

		private EvaluatedValue freshValueCache;

		public ReproDbEntry(LocatedProp subject) {
			this.subject = subject;
		}

		public ReferenceValueDiff getRelatedDiff() {
			return rvDiffs.get(subject);
		}

		public EvaluatedValue getFreshValue() {
			if (freshValueCache == null) {
				if ("true".equals(System.getProperty("purge_before_fresh_value"))) {
					ASTProvider.purgeCache();
					if (CreateLocator.identityLocatorCache != null) {
						CreateLocator.identityLocatorCache.clear();
					}
				}
				freshValueCache = PropEvaluation.evaluateProp(getConfig().reparse(), subject);
			}
			return freshValueCache;
		}

		public EvaluatedValue getFreshValueWithoutCache() {
			return PropEvaluation.evaluateProp(getConfig().reparse(), subject);
		}

		public EvaluatedValue evalWithSingleIntermediateProp(LocatedProp intermediate) {
			final AstInfo ast = getConfig().reparse();
			PropEvaluation.evaluateProp(ast, intermediate);
			return PropEvaluation.evaluateProp(ast, subject);
		}

		public EvaluatedValue evalWithIntermediateProps(List<LocatedProp> intermediates) {
			if (intermediates.size() == 1) {
				return evalWithSingleIntermediateProp(intermediates.get(0));
			}
			final AstInfo ast = getConfig().reparse();
			for (LocatedProp lp : intermediates) {
				if (CreateLocator.identityLocatorCache != null) {
					CreateLocator.identityLocatorCache.clear();
				}
				PropEvaluation.evaluateProp(ast, lp);
			}
			return PropEvaluation.evaluateProp(ast, subject);

		}

		public boolean freshValueIsEqualToDiffValue() {
			final ReferenceValueDiff diff = getRelatedDiff();
			return diff != null && diff.nonReferenceValue.equals(getFreshValue());
		}

		public Set<LocatedProp> searchForSiblings(int distance) {
			if (distance <= 0) {
				throw new IllegalArgumentException("Distance must be positive");
			}
			final DependencyGraphNode depNode = rvas.getDependencyNodeForProp(subject);
			if (depNode == null) {
				System.err.println("Missing dependency graph node for " + subject);
				return Collections.emptySet();
			}
			return FindDependencyGraphSiblings.getSiblings(depNode, distance);
		}

		public boolean producesDifferentValueWithIntermediates(List<LocatedProp> intermediateSteps) {
			final EvaluatedValue freshVal = getFreshValue();
			final EvaluatedValue mutVal = evalWithIntermediateProps(intermediateSteps);
			return !mutVal.equals(freshVal);
		}
	}
}
