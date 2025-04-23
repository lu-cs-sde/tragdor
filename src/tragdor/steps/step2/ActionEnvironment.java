package tragdor.steps.step2;

import java.util.Random;

import codeprober.AstInfo;
import tragdor.config.UserConfig;

public class ActionEnvironment {

	private final UserConfig config;

	private AstInfo ast;

	public final Random rng;

	public ActionEnvironment(UserConfig config, Random rng) {
		this.config = config;
		this.rng = rng;
	}

	public void discardCachedAst() {
		ast = null;
	}

	public AstInfo getAst() {
		if (ast == null) {
			ast = config.reparse();
		}
		return ast;
	}

	public AstInfo getCachedAst() {
		return ast;
	}
}
