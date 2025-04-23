package tragdor.report.impl;

import codeprober.protocol.data.RpcBodyLine;
import tragdor.LocatedProp;
import tragdor.report.ReportType;

public class NonIdempotentPropertyEquationAfterReset extends NonIdempotentPropertyEquation {

	public NonIdempotentPropertyEquationAfterReset(LocatedProp subject, RpcBodyLine valueDuringReferenceRun,
			RpcBodyLine valueDuringRepeatInvocation, RpcBodyLine freshAstValue) {
		super(subject, valueDuringReferenceRun, valueDuringRepeatInvocation, freshAstValue);
	}

	@Override
	public ReportType getType() {
		return ReportType.NON_IDEMPOTENT_PROPERTY_EQUATION_AFTER_RESET;
	}

	@Override
	public String getMessage() {
		final String subjType = subject.locator.result.type;
		final String[] typeParts = subjType.split("\\.");
		return String.format(
				"The value for '%s.%s' has one value during normal run, and another when the equation is reset and re-evaluated",
				typeParts.length == 0 ? subjType : typeParts[typeParts.length - 1], subject.prop.name);
	}
}