package tragdor.report.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.protocol.data.RpcBodyLine;
import tragdor.LocatedProp;
import tragdor.report.BaseReport;
import tragdor.report.ReportType;

public class PropertyValueDiffReport extends BaseReport {

	private final LocatedProp subject;

	private final RpcBodyLine freshAstValue;

	private final List<LocatedProp> intermediateSteps;
	private final RpcBodyLine valueAfterIntermediates;

	public PropertyValueDiffReport(LocatedProp subject, RpcBodyLine freshAstValue, List<LocatedProp> intermediateSteps,
			RpcBodyLine valueAfterIntermediates) {
		this.subject = subject;
		this.freshAstValue = freshAstValue;
		this.intermediateSteps = intermediateSteps;
		this.valueAfterIntermediates = valueAfterIntermediates;
	}

	@Override
	public ReportType getType() {
		return ReportType.PROPERTY_VALUE_DIFF;
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
		return String.format("The value for '%s.%s' has an observable side effect",
				typeParts.length == 0 ? subjType : typeParts[typeParts.length - 1], subject.prop.name);
	}

	@Override
	public JSONObject getDetails() {
		return new JSONObject() //
				.put("subject", subject.locator.toJSON()) //
				.put("property", subject.prop.toJSON()) //
				.put("freshAstValue", freshAstValue.toJSON()) //
				.put("intermediateSteps",
						new JSONArray(intermediateSteps.stream().map(x -> x.toJSON()).collect(Collectors.toList()))) //
				.put("valueAfterIntermediates", valueAfterIntermediates.toJSON()) //

		;
	}
}
