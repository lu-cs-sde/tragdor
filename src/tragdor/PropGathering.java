package tragdor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.AttrsInNode;
import codeprober.locator.CreateLocator;
import codeprober.locator.NodesWithProperty;
import codeprober.metaprogramming.Reflect;
import codeprober.protocol.data.NodeLocator;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;
import codeprober.protocol.data.Tracing;
import codeprober.requesthandler.TracingBuilder;
import codeprober.requesthandler.TracingBuilder.PendingTrace;
import tragdor.EvaluatedValue.Kind;
import tragdor.config.UserConfig;
import tragdor.steps.step1.DependencyGraphNode;
import tragdor.util.Benchmark;

public class PropGathering {

	private final Map<LocatedProp, EvaluatedValue> dst;
	private final boolean collectTraceValues;
	private final UserConfig config;

	public PropGathering(Map<LocatedProp, EvaluatedValue> dst, boolean collectTraceValues, UserConfig config) {
		this.dst = dst;
		this.collectTraceValues = collectTraceValues;
		this.config = config;
	}

	public static void registerExclusions(TracingBuilder traceBuilder) {
		traceBuilder.addExcludedAstType("BytecodeTypeAccess");
		traceBuilder.addExcludedAttribute("BytecodeTypeAccess.rewrittenNode()");
		traceBuilder.addExcludedAttribute("rewrittenNode()");
		traceBuilder.addExcludedAttribute("ParameterDeclaration.name()");
	}

	private static final List<PropertyArg> dummyTraceArg = new ArrayList<>();

	private static void extractDependencyGraphFromTracing(DependencyGraphNode fromNode, Tracing tr,
			Function<LocatedProp, DependencyGraphNode> getOrCreateNode) {
		final DependencyGraphNode fromDGN = (tr.prop.args == dummyTraceArg) ? fromNode
				: getOrCreateNode.apply(new LocatedProp(tr.node, stripParenFromName(tr.prop)));
		if (fromDGN == null) {
			System.out.println("Avoiding extracting dependencyGraph from " + tr.prop.name
					+ " because it has complex arguments and no non-complex predecessor");
			return;
		}
		for (Tracing target : tr.dependencies) {
			if (target.prop.args == dummyTraceArg) {
				// Ignore
			} else {
				final DependencyGraphNode toDGN = getOrCreateNode
						.apply(new LocatedProp(target.node, stripParenFromName(target.prop)));
				if (fromDGN == toDGN) {
					// Also ignore, possibly a bug in TracingBuilder.
				} else {
					fromDGN.addOutgoingEdge(toDGN);
				}
			}
			extractDependencyGraphFromTracing(fromDGN, target, getOrCreateNode);
		}

	}

	private static boolean ignoreCircularAttributeDependencies = !"false"
			.equals(System.getProperty("ignore_circular_attribute_dependencies"));

	public Map<LocatedProp, DependencyGraphNode> evaluateTracedWithFullDependencyGraph(NodeLocator loc,
			BiConsumer<AstInfo, TracingBuilder> evaluator) {
		final Map<LocatedProp, DependencyGraphNode> ret = new HashMap<>();

		final Function<LocatedProp, DependencyGraphNode> getOrCreateNode = lp -> {
			DependencyGraphNode dg = ret.get(lp);
			if (dg == null) {
				dg = new DependencyGraphNode(lp);
				ret.put(lp, dg);
			}
			return dg;
		};

		final AstInfo fresh = config.reparse();

		final boolean[] acceptNotifications = new boolean[] { true };
		final IdentityHashMap<PendingTrace, Boolean> pendingEdges = new IdentityHashMap<>();
		final TracingBuilder traceBuilder = new TracingBuilder(fresh) {

			int circularIgnoreDepth = 0;

			public void accept(Object[] args) {
				if (!acceptNotifications[0]) {
					return;
				}
				final String event = String.valueOf(args[0]);
				if (args.length > 0) {

					if (circularIgnoreDepth > 0) {
						switch (event) {
						case "CIRCULAR_CASE1_START": {
							++circularIgnoreDepth;
							break;
						}
						case "CIRCULAR_CASE1_RETURN": {
							--circularIgnoreDepth;

							if (circularIgnoreDepth == 0) {
								final Object[] begin = Arrays.copyOf(args, args.length);
								begin[0] = "COMPUTE_BEGIN";
								super.accept(begin);
								final Object[] end = Arrays.copyOf(args, args.length);
								end[0] = "COMPUTE_END";
								super.accept(end);
							}
							break;
						}
						}
						return;
					}
					switch (event) {
					case "CACHE_READ": {
						/**
						 * Expected structure:
						 * <ul>
						 * <li>0: Trace.Event event
						 * <li>1: ASTNode node
						 * <li>2: String attribute
						 * <li>3: Object params
						 * <li>4: Object value
						 * </ul>
						 */
						if (args.length != 5) {
							System.err.println("Unknown CACHE_READ event structure");
							return;
						}
						final Object astNode = args[1];
						final String attribute = String.valueOf(args[2]);
						if (super.excludeAttribute(astNode, attribute)) {
							return;
						}

						final PendingTrace active = peekActiveTrace();
						if (active == null) {
							return;
						}
						if (active.userData == null) {
							active.userData = new ArrayList<Object[]>();
						}
						((List<Object[]>) active.userData).add(args);
						pendingEdges.put(active, true);
						return;
					}
					case "CIRCULAR_CASE1_START": // Fall-through
					{
						if (ignoreCircularAttributeDependencies) {
							++circularIgnoreDepth;
						}
						break;
					}

					case "COMPUTE_BEGIN": {
						super.accept(args);
						break;
					}
					case "COMPUTE_END":
//						Expected structure: Trace.Event event, ASTNode node, String attribute, Object params, Object value
						super.accept(args);
						break;
					}
				}
			};
		};

		registerExclusions(traceBuilder);
		if (!collectTraceValues) {
			traceBuilder.setSkipEncodingTraceResults(true);
		}
		traceBuilder.setFallbackArgsForUnknownArgumentTypes(dummyTraceArg);
		registerTraceReceiver(fresh, traceBuilder);
		Benchmark.tickTockv("traceEvaluator", () -> evaluator.accept(fresh, traceBuilder));

		System.out.println("Stopping trace..");
		traceBuilder.stop();

		System.out.println("Finishing trace!");
		final Tracing rootTrace = Benchmark.tickTock("finishTrace", () -> traceBuilder.finish(loc));
		if (rootTrace != null) {
			System.out.println("Extracting from trace..");
			extractTrace(new AstInfo[] { fresh }, rootTrace, dst);

			System.out.println("Building dependency graph..");
			acceptNotifications[0] = false;

			Benchmark.tickTockv("extractDependencyGraph", () -> {
				if (rootTrace != null && rootTrace.prop != null && "MultipleTraceEvents".equals(rootTrace.prop.name)) {
					for (Tracing target : rootTrace.dependencies) {
						extractDependencyGraphFromTracing(null, target, getOrCreateNode);
					}
				} else {
					extractDependencyGraphFromTracing(null, rootTrace, getOrCreateNode);
				}
			});

			System.out.println("Handling pendingEdges..");
			final Map<LocatedProp, Object> pendingDstEncodedValues = new HashMap<>();
			for (PendingTrace trSrc : pendingEdges.keySet()) {
				Benchmark.tickTockv("pendingEdges", () -> {
					if (trSrc.property.args == dummyTraceArg) {
						return;
					}
					final NodeLocator srcNode = trSrc.getLocator(); // CreateLocator.fromNode(fresh, new
																	// AstNode(trSrc.node));
					if (srcNode == null) {
						return;
					}
					final LocatedProp fromProp = new LocatedProp(srcNode, stripParenFromName(trSrc.property));
					final DependencyGraphNode fromDGN = getOrCreateNode.apply(fromProp);

					for (Object[] outgoingArgs : (List<Object[]>) trSrc.userData) {
						final AstNode toNode = new AstNode(outgoingArgs[1]);
						if (!traceBuilder.isAttached(toNode)) {
							// Oh dear! Pretend it did not happen;
							continue;
						}
						final NodeLocator toLoc = CreateLocator.fromNode(fresh, toNode);
						if (toLoc == null) {
							continue;
						}
						final String attr = outgoingArgs[2] + "";
						final List<PropertyArg> decodedArgs = traceBuilder.decodeTraceArgs(outgoingArgs[1], attr,
								outgoingArgs[3]);
						if (decodedArgs == dummyTraceArg) {
							// Intentional reference equality; failed decoding args
							continue;
						}
						final int lpar = attr.indexOf('(');
						final Property toProp = new Property( //
								attr.substring(attr.indexOf('.') + 1, lpar == -1 ? attr.length() : lpar), //
								decodedArgs //
						);
						final LocatedProp dstProp = new LocatedProp(toLoc, toProp);
						final DependencyGraphNode toDGN = getOrCreateNode.apply(dstProp);
						if (fromDGN == toDGN) {
							// Ignore, likely a bug in TracingBuilder.
							continue;
						}
						fromDGN.addOutgoingEdge(toDGN);

						/**
						 * Expected structure:
						 * <ul>
						 * <li>0: Trace.Event event
						 * <li>1: ASTNode node
						 * <li>2: String attribute
						 * <li>3: Object params
						 * <li>4: Object value
						 * </ul>
						 */
						pendingDstEncodedValues.put(dstProp, outgoingArgs[4]);
					}
				});
			}
			for (Entry<LocatedProp, Object> x : pendingDstEncodedValues.entrySet()) {
//				if (!dst.containsKey(x.getKey())) {
//				System.err.println("?? Missing entry in dst for prop: " + dstProp);
//				System.exit(1);

				insertPropvalue(x.getKey(), new EvaluatedValue(Kind.VALUE,
						PropEvaluation.maskResult(traceBuilder.encodeValue(x.getValue()))));
//				}
			}
		}
		return ret;
	}

	private static Property stripParenFromName(Property src) {
		final int parenIdx = src.name.indexOf("(");
		if (parenIdx != -1) {
			return new Property(src.name.substring(0, parenIdx), src.args);
		}
		return src;
	}

	private void extractTrace(AstInfo[] info, Tracing trace, Map<LocatedProp, EvaluatedValue> dst) {
		// In case an attribute depends on itself, then we want to handle the
		// "top-level" invocation last, in order to record the last result value in dst
		trace.dependencies.forEach(tr -> extractTrace(info, tr, dst));

		// Identity comparison on purpose
		if (trace.prop.args != dummyTraceArg) {
			final Property strippedName = stripParenFromName(trace.prop);
			// Ignore synthetic multi-event created by TracingBuilder
			if (!"MultipleTraceEvents".equals(strippedName.name)) {
				final LocatedProp lprop = new LocatedProp(trace.node, strippedName);
//					if (!dst.containsKey(lprop)) {
				if (collectTraceValues) {
					insertPropvalue(lprop, new EvaluatedValue(Kind.VALUE, PropEvaluation.maskResult(trace.result)));
				} else {
					insertPropvalue(lprop, EvaluatedValue.dummy);
				}
			}
		}
	}

	private void insertPropvalue(LocatedProp key, EvaluatedValue value) {
		dst.put(key, value);
	}

	public int countSearch(AstInfo info, String predicate, int limitNodes) {
		return NodesWithProperty.get(info, info.ast, "", predicate, limitNodes).size();
	}

	public void gatherWithoutTracingOrFlushing(AstInfo info, String predicate, int start, int end, int limitNodes,
			String propName, TracingBuilder tb, boolean[] anyEntryPointThrewAnExceptionPtr) {
		final boolean wildcardProp = "*".equals(propName);
		final List<Object> searchResults = NodesWithProperty.get(info, info.ast, wildcardProp ? "" : propName,
				predicate, limitNodes);
		end = Math.min(end, searchResults.size());
		final Runnable reRegisterTraceReceiver = () -> {
			registerTraceReceiver(info, tb);
		};
		System.out
				.println("Searching among " + searchResults.size() + " result(s), slice [" + start + ".." + end + "]");
		for (int i = start; i < end; ++i) {
//			System.out.println("..search line " + i);
			final Object line = searchResults.get(i);
			if (!(line instanceof AstNode)) {
				continue;
			}
//			System.out.println("@ " + i);
			final AstNode node = (AstNode) line;
			final NodeLocator loc = CreateLocator.fromNode(info, node);
			if (loc == null) {
				System.err.println("Failed creating locator during search. Node: " + node + ", predicate: " + predicate
						+ ", props: " + propName + ", i: " + i);
			}
//			for (String propName : props) {
			Benchmark.tickTock("gather:" + propName, () -> {
				if (wildcardProp) {
					// Get all props
					for (Property prop : AttrsInNode.getTyped(info, node, null, false) //
							.stream().filter(x -> x.args.isEmpty()) //
							.collect(Collectors.toList())) {
						final LocatedProp lprop = new LocatedProp(loc, prop);
						insertPropvalue(lprop, PropEvaluation.evaluateProp(info, lprop, reRegisterTraceReceiver));
					}
				} else {
					// Get a specific prop
					final LocatedProp lprop = new LocatedProp(loc, new Property(propName));
//					System.out.println("Setting verbose=true, in preparation of evaluating " + lprop.toString());
//					TracingBuilder.beVerboseNextAccept = true;
					final EvaluatedValue res = PropEvaluation.evaluateProp(info, lprop, reRegisterTraceReceiver);
//					System.out.println("Entry prop " + lprop + " result = " + res);
					insertPropvalue(lprop, res);
					if (res.kind == EvaluatedValue.Kind.EXCEPTION) {
						System.out.println("Discovery prop " + lprop + " threw an exception, resetting stack..");
						anyEntryPointThrewAnExceptionPtr[0] = true;
						// Must reset stack
						tb.resetActiveStack();
						// It won't unwind correctly by itself due to the exception, and this can cause
						// false dependency graph connections.
						// In addition, the circular state may very well be stuck
//						if (Dynsed.isAstStateCircular(info)) {
//							Dynsed.resetAstState(info.ast.underlyingAstNode);
//							// Resetting that removes the trace connection, so re-register
//							registerTraceReceiver(info, tb);
//						}
					}
				}
				return null;
			});
//			}
		}
		System.out.println("done");
	}

	private static void registerTraceReceiver(AstInfo info, TracingBuilder tb) {
		if (info.hasOverride1(info.ast.underlyingAstNode.getClass(), "cpr_setTraceReceiver", Consumer.class)) {
			Reflect.invokeN(info.ast.underlyingAstNode, "cpr_setTraceReceiver", new Class<?>[] { Consumer.class },
					new Object[] { tb });
		} else {
			System.err.println("?? Missing cpr_setTraceReceiver, trace collection will not work");
		}
	}
}
