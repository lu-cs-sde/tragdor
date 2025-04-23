package tragdor.report.impl;

import org.json.JSONObject;

import codeprober.protocol.data.RpcBodyLine;
import tragdor.LocatedProp;
import tragdor.report.BaseReport;
import tragdor.report.ReportType;

public class PropertyValueDiffInReferenceCompileReport extends BaseReport {

	private final LocatedProp subject;

	private final RpcBodyLine freshAstValue;

	private final RpcBodyLine valueDuringRandomSearch, valueDuringReferenceRun;

	public PropertyValueDiffInReferenceCompileReport(LocatedProp subject, RpcBodyLine valueDuringReferenceRun,
			RpcBodyLine valueDuringRandomSearch, RpcBodyLine freshAstValue) {
		this.subject = subject;
		this.valueDuringReferenceRun = valueDuringReferenceRun;
		this.valueDuringRandomSearch = valueDuringRandomSearch;
		this.freshAstValue = freshAstValue;
	}

	@Override
	public ReportType getType() {
		return ReportType.PROPERTY_VALUE_DIFF_IN_REFERENCE_RUN;
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
				"The value for '%s.%s' has one value during normal run, and another during randomized search",
				typeParts.length == 0 ? subjType : typeParts[typeParts.length - 1], subject.prop.name);
	}

	@Override
	public JSONObject getDetails() {
		return new JSONObject() //
				.put("subject", subject.locator.toJSON()) //
				.put("property", subject.prop.toJSON()) //
				.put("freshAstValue", freshAstValue.toJSON()) //
				.put("referenceRunValue", valueDuringReferenceRun.toJSON()) //
				.put("randomSearchValue", valueDuringRandomSearch.toJSON()) //

		;
	}
}
