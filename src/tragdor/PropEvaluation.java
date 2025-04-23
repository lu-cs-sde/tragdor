package tragdor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.ApplyLocator;
import codeprober.locator.ApplyLocator.ResolvedNode;
import codeprober.locator.CreateLocator;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.metaprogramming.StreamInterceptor;
import codeprober.protocol.create.EncodeResponseValue;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.requesthandler.EvaluatePropertyHandler;
import codeprober.requesthandler.EvaluatePropertyHandler.UnpackedAttrValue;
import codeprober.util.BenchmarkTimer;
import tragdor.EvaluatedValue.Kind;
import tragdor.config.AstGlue;
import tragdor.report.impl.ExceptionThrownReport;
import tragdor.report.impl.FailedFindingNodeInNewAstReport;
import tragdor.report.impl.UnattachedNodeReport;
import tragdor.util.Exit;

public class PropEvaluation {

	public static EvaluatedValue evaluateProp(AstInfo info, LocatedProp prop) {
		return evaluateProp(info, prop, null);
	}

	public static ResolvedNode applyLocatorWithUncachedRetry(AstInfo info, NodeLocator locator) {
		InvokeProblem problem = null;
		try {
			ResolvedNode result = ApplyLocator.toNode(info, locator);
			if (result != null && result.node != null && result.nodeLocator != null) {
				return result;
			}
		} catch (InvokeProblem ip) {
			problem = ip;
		}

		// If we get here, node locator application failed.
		// This might be due to identity caching
		if (CreateLocator.identityLocatorCache != null) {
			CreateLocator.identityLocatorCache.clear();
			try {
				ResolvedNode secondTry = ApplyLocator.toNode(info, locator);
				if (secondTry != null && secondTry.node != null && secondTry.nodeLocator != null) {
					return secondTry;
				}

			} catch (InvokeProblem ip2) {
				problem = ip2;
			}
			Tragdor.report(new FailedFindingNodeInNewAstReport(locator));
		}

		System.err.println("Failed applying locator " + locator.toJSON());
		if (problem != null) {
			// Exceptions should never happen, so we exit when they happen
			problem.printStackTrace();
			Exit.exit(1);
		}
		// No exception, the application simply failed. This can happen when non-NTA
		// attributes create fresh nodes, for example. Don't exit, just return null.
		return null;
	}

	public static EvaluatedValue evaluateProp(AstInfo info, LocatedProp prop, Runnable reRegisterTraceReceiver) {

//		checkCircleState(info, "pre locator");
		final ResolvedNode result = applyLocatorWithUncachedRetry(info, prop.locator);
		if (result == null) {
			return new EvaluatedValue(EvaluatedValue.Kind.EXCEPTION, RpcBodyLine.fromStderr("Failed applying locator"));
		}

//		checkCircleState(info, "pre eval");
		AstNode subject = result.node;
		try {
			final Object value;

			if (prop.prop.args == null || prop.prop.args.isEmpty()) {
				value = Reflect.invoke0(subject.underlyingAstNode, prop.prop.name);
			} else {
				final int numArgs = prop.prop.args.size();
				final Class<?>[] argTypes = new Class<?>[numArgs];
				final Object[] argValues = new Object[numArgs];
//				final List<PropertyArg> updatedArgs = new ArrayList<>();
//				updatedArgsPtr.set(updatedArgs);
				for (int i = 0; i < numArgs; ++i) {
					final PropertyArg arg = prop.prop.args.get(i);
					final Class<?> argType = EvaluatePropertyHandler.getValueType(info, arg);
					argTypes[i] = argType;
					final UnpackedAttrValue unpacked = EvaluatePropertyHandler.unpackAttrValue(info, arg, msg -> {
					});
					argValues[i] = unpacked.unpacked;
//					updatedArgs.add(unpacked.response);
				}
				BenchmarkTimer.EVALUATE_ATTR.enter();
				try {
					value = Reflect.invokeN(subject.underlyingAstNode, prop.prop.name, argTypes, argValues);
				} catch (InvokeProblem ip) {
					System.err.println("InvokeProblem when invoking " + prop.prop.toJSON());
					System.err.println("ArgTypes: " + Arrays.toString(argTypes));
					System.err.println("ArgValues: " + Arrays.toString(argValues));
					throw ip;
				} finally {
					BenchmarkTimer.EVALUATE_ATTR.exit();
				}
				for (Object argValue : argValues) {
					// Flush all StreamInterceptor args
					if (argValue instanceof StreamInterceptor) {
						((StreamInterceptor) argValue).consume();
					}
				}
			}

//			checkCircleState(info, "pos eval");
//			currentPropDebug = prop;
//			return new EvaluatedValue(Kind.VALUE, encodeValue(info, value));

			return encodePropertyResult(info, prop, value);
//
//			final List<RpcBodyLine> lines = new ArrayList<>();
//			final int preUnattached = CreateLocator.numEncounteredUnattachedNodes;
//			try {
//				EncodeResponseValue.encodeTyped(info, lines, new ArrayList<>(), value, new HashSet<>());
//			} catch (RuntimeException e) {
//				System.err.println("Invoking " + prop.prop.name
//						+ " succeeded, but encoding the response value resulted in an exception");
//				e.printStackTrace();
//				Dynsed.report(new ExceptionThrownReport(prop, e));
//				return new EvaluatedValue(Kind.EXCEPTION, RpcBodyLine.fromStderr(e.toString()));
//			}
//			if (preUnattached != CreateLocator.numEncounteredUnattachedNodes) {
//				// At least one node in the result is not attached to the AST
//				Dynsed.report(new UnattachedNodeReport(prop));
//			}
//
////			checkCircleState(info, "pos encode");
////			encodeTyped(AstInfo info, List<RpcBodyLine> out, List<Diagnostic> diagnostics, Object value,
////					HashSet<Object> alreadyVisitedNodes) {
//			final RpcBodyLine preMaskedResult = lines.size() == 1 ? lines.get(0) : RpcBodyLine.fromArr(lines);
//			return new EvaluatedValue(Kind.VALUE, maskResult(preMaskedResult));
//			final List<RpcBodyLine> lines = new ArrayList<>();
//			EncodeResponseValue.encodeTyped(info, lines, new ArrayList<>(), value, new HashSet<>());
//			return new EvaluatedValue(Kind.VALUE, lines.stream().map(x -> x.toJSON().toString()).collect(Collectors.toList()));
		} catch (InvokeProblem ip) {
			Throwable cause = ip.getCause();
			if (cause instanceof InvocationTargetException) {
				cause = cause.getCause();
			}
			System.err.println("Got InvokeProblem when evaluating prop! " + prop.toJSON());
			cause.printStackTrace();
			Tragdor.report(new ExceptionThrownReport(prop, cause));



			if (AstGlue.isAstStateCircular(info)) {
				Tragdor.resetAstState(info.ast.underlyingAstNode);
				if (reRegisterTraceReceiver != null) {
					reRegisterTraceReceiver.run();
				}
			}
//			Dynsed.resetAstState(info.ast.underlyingAstNode);
//			ip.printStackTrace();
//			System.exit(1);
			return new EvaluatedValue(Kind.EXCEPTION, RpcBodyLine.fromStderr(cause.toString()));
		}
	}

	public static EvaluatedValue encodePropertyResult(AstInfo info, LocatedProp prop, Object rawResult) {
		final List<RpcBodyLine> lines = new ArrayList<>();
		final int preUnattached = CreateLocator.numEncounteredUnattachedNodes;
		try {
			EncodeResponseValue.encodeTyped(info, lines, new ArrayList<>(), rawResult, new HashSet<>());
		} catch (RuntimeException e) {
			System.err.println("Invoking " + prop.prop.name
					+ " succeeded, but encoding the response value resulted in an exception");
			e.printStackTrace();
			Tragdor.report(new ExceptionThrownReport(prop, e));
			return new EvaluatedValue(Kind.EXCEPTION, RpcBodyLine.fromStderr(e.toString()));
		}
		if (preUnattached != CreateLocator.numEncounteredUnattachedNodes) {
			// At least one node in the result is not attached to the AST
			Tragdor.report(new UnattachedNodeReport(prop));
		}

//		checkCircleState(info, "pos encode");
//		encodeTyped(AstInfo info, List<RpcBodyLine> out, List<Diagnostic> diagnostics, Object value,
//				HashSet<Object> alreadyVisitedNodes) {
		final RpcBodyLine preMaskedResult = lines.size() == 1 ? lines.get(0) : RpcBodyLine.fromArr(lines);
		return new EvaluatedValue(Kind.VALUE, maskResult(preMaskedResult));

	}

	private static final Pattern MISSING_TOSTR_WARNING = Pattern
			.compile("^No toString\\(\\) or cpr_getOutput\\(\\) implementation in ([a-zA-Z.]+)$");

	public static RpcBodyLine maskResult(RpcBodyLine src) {
		if (src.isArr()) {
			/**
			 * There is a standard output message for types without toString or
			 * cpr_getOutput, in the following format:
			 *
			 * <pre>
			 *   No toString() or cpr_getOutput() implementation in foo.bar.Baz
			 *   foo.bar.Baz@abc123
			 * </pre>
			 *
			 * However this output can cause unnecessary diff reports, so we will mask it by
			 * removing the second line.
			 */
			final List<RpcBodyLine> arr = src.asArr();

			final List<RpcBodyLine> filtered = new ArrayList<>();
			for (int i = 0; i < arr.size() - 1; ++i) {
				final RpcBodyLine first = arr.get(i);
				final RpcBodyLine second = arr.get(i + 1);

				filtered.add(first);

				if (first.isPlain() && second.isPlain()) {
					final String fp = first.asPlain();
					final String sp = second.asPlain();
					final Matcher matcher = MISSING_TOSTR_WARNING.matcher(fp);
					if (matcher.matches()) {
						if (Pattern.compile("^" + matcher.group(1) + "@[0-9a-z]+$").matcher(sp).matches()) {
							++i; // Skip second
							continue;
						}
					}
				}
			}
			if (arr.size() > 0) {
				// Add last
				filtered.add(arr.get(arr.size() - 1));
			}
			if (filtered.size() == 1) {
				return filtered.get(0);
			}
			return RpcBodyLine.fromArr(filtered);
		}

		return src;
	}
}
