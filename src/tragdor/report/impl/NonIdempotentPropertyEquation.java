package tragdor.report.impl;

import org.json.JSONObject;

import codeprober.protocol.data.RpcBodyLine;
import tragdor.LocatedProp;
import tragdor.report.BaseReport;
import tragdor.report.ReportType;

public class NonIdempotentPropertyEquation extends BaseReport {

	protected final LocatedProp subject;

	private final RpcBodyLine freshAstValue;

	private final RpcBodyLine valueDuringReferenceRun, valueDuringRepeatInvocation;

	public NonIdempotentPropertyEquation(LocatedProp subject, RpcBodyLine valueDuringReferenceRun,
			RpcBodyLine valueDuringRepeatInvocation, RpcBodyLine freshAstValue) {
		this.subject = subject;
		this.valueDuringReferenceRun = valueDuringReferenceRun;
		this.valueDuringRepeatInvocation = valueDuringRepeatInvocation;
		this.freshAstValue = freshAstValue;
	}

	@Override
	public ReportType getType() {
		return ReportType.NON_IDEMPOTENT_PROPERTY_EQUATION;
	}

	@Override
	public String getRelatedNodeType() {
		return subject.locator.result.type;
	}

	@Override
	public String getRelatedAttrName() {
		return subject.prop.name;
	}

	@Override
	public String getMessage() {
		final String subjType = subject.locator.result.type;
		final String[] typeParts = subjType.split("\\.");
		return String.format(
				"The value for '%s.%s' has one value during normal run, and another when the equation is re-evaluated",
				typeParts.length == 0 ? subjType : typeParts[typeParts.length - 1], subject.prop.name);
	}

	@Override
	public JSONObject getDetails() {
		return new JSONObject() //
				.put("subject", subject.locator.toJSON()) //
				.put("property", subject.prop.toJSON()) //
				.put("freshAstValue", freshAstValue.toJSON()) //
				.put("referenceRunValue", valueDuringReferenceRun.toJSON()) //
				.put("repeatInvocationValue", valueDuringRepeatInvocation.toJSON()) //

		;
	}
}
