package tragdor.steps.step2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import codeprober.AstInfo;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.CreateLocator;
import codeprober.locator.MethodKindDetector;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.TALStep;
import codeprober.requesthandler.EvaluatePropertyHandler;
import tragdor.EvaluatedValue;
import tragdor.LocatedProp;
import tragdor.PropEvaluation;
import tragdor.Tragdor;
import tragdor.report.impl.ExceptionThrownReport;
import tragdor.report.impl.FailedFindingNodeInNewAstReport;
import tragdor.report.impl.NonIdempotentPropertyEquation;
import tragdor.report.impl.NonIdempotentPropertyEquationAfterReset;
import tragdor.report.impl.PropertyValueDiffInReferenceCompileReport;

public abstract class CycleBasedRandomPropSearch extends CycleBasedSearch {

	public enum CheckStyle {

		/**
		 * Parse a fresh AST every cycle. Then, invoke each property "normally", and
		 * compare with the reference value.
		 */
		FRESH_PARSE_AND_INVOKE_PUBLIC_FACING,

		/**
		 * Keep the same AST forever, prefer running the underlying "_compute" method if
		 * available. If not available: invoke the "_reset" method first (if available)
		 * and then the normal method.
		 * <p>
		 * Whether invoking compute or normal, compare the result with the reference
		 * value.
		 */
		KEEP_AST_AND_SPOTCHECK,
	}

	final Set<String> alreadyReportedPropIds = new HashSet<>();
	protected int cycleId;
	final Map<LocatedProp, SpotcheckInfo> transformToComputeMethodCache = new HashMap<>();

	public CycleBasedRandomPropSearch(CycleSearchParams params) {
		super(params);
	}

	protected CheckStyle getCheckStyle() {
		return CheckStyle.FRESH_PARSE_AND_INVOKE_PUBLIC_FACING;
	}

	@Override
	protected void doRun() throws Exception {
		cycleId = 0;

		final CheckStyle checkStyle = getCheckStyle();
		long nextAutoSave = System.currentTimeMillis() + 30_000L;

		while (true) {
			++cycleId;

			final long cycleStart = System.currentTimeMillis();
			final boolean debugCycle = shouldLogDebugInfoForCycle(cycleId);
			if (debugCycle) {
				System.out.println("Performing comparisons, cycle: " + cycleId);
			}
			AstInfo freshCycleAst = null;

			if (hasRunOverTimeBudget()) {
				break;
			}

			final List<LocatedProp> props = getCycleProps();
			Collections.shuffle(props, rng);
			boolean didResetFirst = false;

			for (LocatedProp lp : props) {
				final EvaluatedValue reference = refValues.getReferenceValue(lp);
				if (reference == null) {
					System.out.println("What the heck, missing reference for " + lp);
					continue;
				}

				EvaluatedValue newVal;
				switch (checkStyle) {
				default: // Fall-through
				case FRESH_PARSE_AND_INVOKE_PUBLIC_FACING: {
					if (freshCycleAst == null) {
						freshCycleAst = config.reparse();
					}
					newVal = PropEvaluation.evaluateProp(freshCycleAst, lp);
					break;
				}

				case KEEP_AST_AND_SPOTCHECK: {
					final SpotcheckInfo spotInfo = transformToComputeMethod(referenceValueAst, lp);
					if (spotInfo == null) {
//							System.out.println("Missing compute method for " + prop.prop.name);
						continue;
					}
					final LocatedProp sprop = spotInfo.computeProp;
					final ResolvedNode foundNode = PropEvaluation.applyLocatorWithUncachedRetry(referenceValueAst,
							sprop.locator);
					if (foundNode == null) {
						System.out.println("Cannot spot-check prop, node locator failed");
						Tragdor.report(new FailedFindingNodeInNewAstReport(sprop.locator));
						continue;
					}

					final Class<?> clazz = referenceValueAst.loadAstClass.apply(sprop.locator.result.type);
					if (spotInfo.resetMthName != null) {
						didResetFirst = true;
						final Object unode = foundNode.node.underlyingAstNode;
						final Method mth = clazz.getDeclaredMethod(spotInfo.resetMthName);
						mth.setAccessible(true);
						mth.invoke(unode);
					}

					final int numArgs = sprop.prop.args == null ? 0 : sprop.prop.args.size();
					final Class<?>[] argTypes = new Class<?>[numArgs];
					final Object[] argValues = new Object[numArgs];
					for (int j = 0; j < numArgs; ++j) {
						final PropertyArg arg = sprop.prop.args.get(j);
						argTypes[j] = EvaluatePropertyHandler.getValueType(referenceValueAst, arg);
						argValues[j] = EvaluatePropertyHandler.unpackAttrValue(referenceValueAst, arg, msg -> {
						}).unpacked;
					}

					final Method mth = clazz.getDeclaredMethod(sprop.prop.name, argTypes);
					mth.setAccessible(true);
					final Object computeRes;
					try {
						computeRes = mth.invoke(foundNode.node.underlyingAstNode, argValues);
					} catch (InvocationTargetException e) {
						System.out.println("Invocation problem when running " + sprop);
						e.printStackTrace();
						Tragdor.report(new ExceptionThrownReport(sprop, e.getCause()));
						continue;
					}

					newVal = PropEvaluation.encodePropertyResult(referenceValueAst, sprop, computeRes);
					break;
				}
				}

				if (newVal.equals(reference)) {
					// OK
					continue;
				}
				final String propId = lp.locator.result.type + " + " + lp.prop.name;
				final boolean isNewPropId = alreadyReportedPropIds.add(propId);
				if (isNewPropId) {
					System.out.println("Found uniq issue: " + propId);
				}
				if (isNewPropId) {
					final EvaluatedValue fresh = PropEvaluation.evaluateProp(config.reparse(), lp);
					if (checkStyle == CheckStyle.FRESH_PARSE_AND_INVOKE_PUBLIC_FACING) {
						Tragdor.report(new PropertyValueDiffInReferenceCompileReport(lp, reference.value, //
								newVal.value, //
								fresh.value));
					} else {
						if (didResetFirst) {
							Tragdor.report(new NonIdempotentPropertyEquationAfterReset(lp, reference.value, //
									newVal.value, fresh.value));
						} else {
							Tragdor.report(new NonIdempotentPropertyEquation(lp, reference.value, //
									newVal.value, fresh.value));
						}
					}
				}
			}

			if (debugCycle) {
				System.out.println("Cycle done in " + (System.currentTimeMillis() - cycleStart) + "ms");
			}
			if (System.currentTimeMillis() > nextAutoSave) {
				// Some time has passed, autosave
				Tragdor.saveReports();
				nextAutoSave = System.currentTimeMillis() + 30_000L;
			}

			if (hasRunOverTimeBudget()) {
				System.out.println("Has run for long enough to exceed timeout of " + searchBudgetMs + "ms");
				break;
			}
		}
	}

	private SpotcheckInfo transformToComputeMethod(AstInfo referenceValueAst, LocatedProp prop) {
		if (!transformToComputeMethodCache.containsKey(prop)) {
			Class<?> clazz;
			try {
				clazz = referenceValueAst.loadAstClass.apply(prop.locator.result.type);
			} catch (RuntimeException e) {
				System.out.println("Could not load requested type '" + prop.locator.result.type + "'");
				transformToComputeMethodCache.put(prop, null);
				return null;
			}
			final int numArgs = prop.prop.args == null ? 0 : prop.prop.args.size();
			final Class<?>[] argTypes = new Class<?>[numArgs];
			for (int i = 0; i < numArgs; ++i) {
				argTypes[i] = EvaluatePropertyHandler.getValueType(referenceValueAst, prop.prop.args.get(i));
			}

			final Method userFacingMethod;
			try {
				userFacingMethod = clazz.getMethod(prop.prop.name, argTypes);
			} catch (NoSuchMethodException e) {
				System.out.println("Failed finding requested method '" + prop.prop.name + "' on " + clazz.getName());
				transformToComputeMethodCache.put(prop, null);
				return null;
			}
			if (userFacingMethod.getReturnType() == Void.TYPE) {
				transformToComputeMethodCache.put(prop, null);
				return null;
			}
			if (MethodKindDetector.isNta(userFacingMethod)) {
				// It is an nta, so the compute method will produce an unattached node, which we
				// cannot compare with. ignore.
				// TODO ..but maybe we can invalidate the cache? No, I don't think thats a good
				// idea, will likely incur issues with equations that use identity comparisons
				transformToComputeMethodCache.put(prop, null);
				return null;
			}

			final Class<?> declClazz = userFacingMethod.getDeclaringClass();

			final TALStep prevResult = prop.locator.result;
			final NodeLocator declLocator = new NodeLocator(new TALStep(declClazz.getName(), prevResult.label,
					prevResult.start, prevResult.end, prevResult.depth), prop.locator.steps);

			final LocatedProp fixedProp = new LocatedProp(declLocator, prop.prop);

			String guessedResetMthName = prop.prop.name;
			final Type[] genParams = userFacingMethod.getGenericParameterTypes();
			for (Type t : genParams) {
				guessedResetMthName += "_" + CreateLocator.convTypeNameToSignature(CreateLocator.extractSimpleNames(t));
			}
			guessedResetMthName += "_reset";
			try {
				declClazz.getDeclaredMethod(guessedResetMthName);
				// Got a cache field!
				final SpotcheckInfo si = new SpotcheckInfo(fixedProp, guessedResetMthName);
				transformToComputeMethodCache.put(prop, si);
				return si;

			} catch (NoSuchMethodException e2) {
				// No reset, just call the original method
				final SpotcheckInfo si = new SpotcheckInfo(fixedProp, null);
				transformToComputeMethodCache.put(prop, si);
				return si;

			}
		}
		return transformToComputeMethodCache.get(prop);
	};

	protected abstract List<LocatedProp> getCycleProps();

	private static class SpotcheckInfo {

		public final LocatedProp computeProp;
		public final String resetMthName;

		public SpotcheckInfo(LocatedProp computeProp, String resetMthName) {
			this.computeProp = computeProp;
			this.resetMthName = resetMthName;
		}
	}

}
