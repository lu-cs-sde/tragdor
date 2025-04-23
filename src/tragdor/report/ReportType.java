package tragdor.report;

public enum ReportType {
	/**
	 * A nta attribute returned a non-AST node value.
	 */
	NON_ASTNODE_NTA,

	/**
	 * The same AST node can be found through two different paths in the AST (i.e
	 * the AST is no longer a tree).
	 */
	NODE_REACHABLE_THROUGH_TWO_PATHS,

	/**
	 * A property (~=attribute) sometimes produces different values.
	 */
	PROPERTY_VALUE_DIFF,

	/**
	 * A property (~=attribute) sometimes produce one value on a fresh AST, and
	 * another during reference runs.
	 */
	PROPERTY_VALUE_DIFF_IN_REFERENCE_RUN,

	/**
	 * A property (~=attribute) seems to sometimes return different values on fresh
	 * ASTs.
	 */
	FLAKY_PROPERTY,

	/**
	 * A property (~=attribute) sometimes produces different values on during
	 * reference runs.
	 */
	FLAKY_PROPERTY_IN_REFERENCE_RUN,

	/**
	 * A property (~=attribute) does not seem to get properly reset after performing
	 * flushTreeCache().
	 */
	INCORRECT_FLUSH,

	/**
	 * An argument was modified, for example a List argument was added to.
	 */
	MODIFIED_ARG,

	/**
	 * A node not connected to the root of the AST was found.
	 */
	UNATTACHED_NODE,


	/**
	 * A node that was found in a reference run could not be found in a new(er) AST.
	 * This indicates that during the reference run, the AST was modified somehow.
	 */
	FAILED_FINDING_NODE_IN_NEW_AST,


	/**
	 * An intrinsic attribute (e.g "AST Token") was mutated.
	 */
	MUTATED_INTRINSICS,

	/**
	 * An attribute threw an exception
	 */
	EXCEPTION_THROWN,

	/**
	 * When the "_compute" method is invoked on an AST with already cached values,
	 * it does not return/compute the same value as during the reference run.
	 */
	NON_IDEMPOTENT_PROPERTY_EQUATION,

	/**
	 * When the "_compute" method is invoked on an AST after calling the
	 * corresponding "_reset", it does not return/compute the same value as during the reference run.
	 */
	NON_IDEMPOTENT_PROPERTY_EQUATION_AFTER_RESET
}
