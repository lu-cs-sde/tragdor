package tragdor.config;

import static tragdor.util.Benchmark.tickTock;

import java.io.FileReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import codeprober.AstInfo;
import codeprober.ast.AstNode;
import codeprober.locator.CreateLocator;
import codeprober.metaprogramming.AstNodeApiStyle;
import codeprober.metaprogramming.InvokeProblem;
import codeprober.metaprogramming.Reflect;
import codeprober.metaprogramming.TypeIdentificationStyle;
import codeprober.protocol.PositionRecoveryStrategy;
import codeprober.toolglue.ParseResult;
import codeprober.util.ASTProvider;
import codeprober.util.ASTProvider.LoadedJar;
import tragdor.util.Exit;

public class AstGlue {

	public static AstInfo reparse(ToolConfig cfg) {
		if (CreateLocator.identityLocatorCache != null) {
			CreateLocator.identityLocatorCache.clear();
		}
		return tickTock("reparse", () -> {

			Object rootNode = null;
			if ("true".equals(System.getenv("DIRECTLY_CALL_SCANNER_PARSER"))) {
				boolean startedParsing = false;
				try {
					final LoadedJar ljar = ASTProvider.loadJar(cfg.tool);

					final Class<?> scannerCls = ljar.classLoader.loadClass("lang.ast.LangScanner");
					final Object scannerInstance = scannerCls.getConstructor(java.io.Reader.class)
							.newInstance(new FileReader(cfg.args[cfg.args.length - 1]));

					final Class<?> parserCls = ljar.classLoader.loadClass("lang.ast.LangParser");
					final Object parserInstance = parserCls.getConstructor().newInstance();
					final Method parseMth = parserCls.getMethod("parse", scannerCls.getSuperclass());
					startedParsing = true;
					rootNode = parseMth.invoke(parserInstance, scannerInstance);
				} catch (NoSuchMethodException | ClassNotFoundException e) {
					System.err.println("Failed directly invoking LangScanner/LangParser");
					e.printStackTrace();
					Exit.exit(1);
				} catch (Exception e) {
					if (startedParsing) {
						// Parsing failed, fall down to error checking below
					} else {
						System.err.println("Unknown error while directly invoking LangScanner/LangParser");
						e.printStackTrace();
						Exit.exit(1);
					}
				}

			} else {
				final ParseResult pres = ASTProvider.parseAst(cfg.tool, cfg.args);
				rootNode = pres.rootNode;
			}
			if (rootNode == null) {
				System.err.println("Failed parsing");
				throw new RuntimeException("Failed parsing");
			}

			resetAstState(rootNode);
			final AstInfo ret = new AstInfo(new AstNode(rootNode), PositionRecoveryStrategy.FAIL,
					AstNodeApiStyle.BEAVER_PACKED_BITS, TypeIdentificationStyle.REFLECTION);
			flushTreeCache(ret);
			return ret;
		});
	}

	public static void resetAstState(Object astNodePtr) {

		// State resetting code below stolen from codeprober.toolglue.AstStateResetter
		// Even though this is a fresh parse, we must reset global states that may or
		// may not eixst. In JastAdd, this is done via '.state().reset()'
		try {
			final Method stateAccessor = astNodePtr.getClass().getMethod("resetState");
//			final Method stateAccessor = astNodePtr.getClass().getMethod("state");
			final String retName = stateAccessor.getReturnType().getName();
//			// Some guard rails to avoid accidentally calling state().reset() on non-JastAdd
//			// tools.
			if (retName.endsWith(".ASTState") || retName.endsWith("$ASTState")) {

//				final Object stateObj =
				stateAccessor.invoke(astNodePtr);
//				if (stateObj != null) {
//					final Method resetMth = stateObj.getClass().getMethod("reset");
//					if (resetMth.getReturnType() == Void.TYPE) {
//						resetMth.invoke(stateObj);
//					}
//				}
			} else {
				System.out.println("No resetState available...");
			}
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException e) {
			// Don't print anything, this is optional functionality, not available in all
			// tools.
			System.err.println("Failed resetting AST state");
			e.printStackTrace();
		}
	}

	public static boolean isAstStateCircular(AstInfo info) {
		try {
			final Method stateAccessor = info.ast.underlyingAstNode.getClass().getMethod("state");
			final String retName = stateAccessor.getReturnType().getName();
			// Some guard rails to avoid accidentally calling state().reset() on non-JastAdd
			// tools.
			if (retName.endsWith(".ASTState") || retName.endsWith("$ASTState")) {
				final Object stateObj = stateAccessor.invoke(info.ast.underlyingAstNode);
				if (stateObj != null) {
					final Method inCircleMth = stateObj.getClass().getDeclaredMethod("inCircle");
					if (inCircleMth.getReturnType() == Boolean.TYPE) {
						inCircleMth.setAccessible(true);
						final Boolean ret = (Boolean) inCircleMth.invoke(stateObj);
						return ret;
					} else {
						System.err.println(
								"inCircle does not return Boolean.. it returns " + inCircleMth.getReturnType());
					}
				} else {
					System.err.println("No state...");
				}
			} else {
				System.err.println("State has unknown type name: '" + retName + "'");
			}
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException e) {
			// Don't print anything, this is optional functionality, not available in all
			// tools.
			e.printStackTrace();
		}
		return false;
	}

	private static boolean warnedMissingFlushTreeCache = false;

	public static void flushTreeCache(AstInfo info) {
		if (CreateLocator.identityLocatorCache != null) {
			CreateLocator.identityLocatorCache.clear();
		}
		tickTock("flushTreeCache", () -> {
			try {
				Reflect.invoke0(info.ast.underlyingAstNode, "flushTreeCache");
			} catch (InvokeProblem e) {
				if (!warnedMissingFlushTreeCache) {
					warnedMissingFlushTreeCache = true;
					System.err.println("flushTreeCache not supported in AST");
					e.getCause().printStackTrace();
				}
			}
			return null;
		});

	}
}
