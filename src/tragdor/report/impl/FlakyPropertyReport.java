package tragdor.report.impl;

import org.json.JSONObject;

import codeprober.protocol.data.RpcBodyLine;
import tragdor.LocatedProp;
import tragdor.report.BaseReport;
import tragdor.report.ReportType;

public class FlakyPropertyReport extends BaseReport {

	protected final LocatedProp offendingProp;
	protected final RpcBodyLine exampleValue1, exampleValue2;

	public FlakyPropertyReport(LocatedProp offendingProp, RpcBodyLine exampleValue1, RpcBodyLine exampleValue2) {
		this.offendingProp = offendingProp;
		this.exampleValue1 = exampleValue1;
		this.exampleValue2 = exampleValue2;
//		if (offendingProp.prop.name.equals("type")) {
//			System.out.println("!");
//		}
	}

	@Override
	public ReportType getType() {
		return ReportType.FLAKY_PROPERTY;
	}

	@Override
	public String getRelatedNodeType() {
		return offendingProp.locator.result.type;
	}

	@Override
	public String getRelatedAttrName() {
		return offendingProp.prop.name;
	}

	@Override
	public String getMessage() {
		final String subjType = offendingProp.locator.result.type;
		final String[] typeParts = subjType.split("\\.");
		return String.format("The value for '%s.%s' is flaky",
				typeParts.length == 0 ? subjType : typeParts[typeParts.length - 1], offendingProp.prop.name);
	}

	@Override
	public JSONObject getDetails() {
		return new JSONObject() //
				.put("subject", offendingProp.locator.toJSON()) //
				.put("prop", offendingProp.prop.toJSON()) //
				.put("exampleValue1", exampleValue1.toJSON()) //
				.put("exampleValue2", exampleValue2.toJSON()) //
				;
	}
}
