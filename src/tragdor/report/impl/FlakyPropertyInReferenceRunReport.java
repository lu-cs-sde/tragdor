package tragdor.report.impl;

import codeprober.protocol.data.RpcBodyLine;
import tragdor.LocatedProp;
import tragdor.report.ReportType;

public class FlakyPropertyInReferenceRunReport extends FlakyPropertyReport {

	public FlakyPropertyInReferenceRunReport(LocatedProp offendingProp, RpcBodyLine exampleValue1, RpcBodyLine exampleValue2) {
		super(offendingProp, exampleValue1, exampleValue2);
	}

	@Override
	public ReportType getType() {
		return ReportType.FLAKY_PROPERTY_IN_REFERENCE_RUN;
	}

	@Override
	public String getMessage() {
		final String subjType = offendingProp.locator.result.type;
		final String[] typeParts = subjType.split("\\.");
		return String.format("The value for '%s.%s' is inconsistent across reference runs",
				typeParts.length == 0 ? subjType : typeParts[typeParts.length - 1], offendingProp.prop.name);
	}
}
