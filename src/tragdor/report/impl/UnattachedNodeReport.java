package tragdor.report.impl;

import org.json.JSONObject;

import tragdor.LocatedProp;
import tragdor.report.BaseReport;
import tragdor.report.ReportType;

public class UnattachedNodeReport extends BaseReport {

	private final LocatedProp subject;

	public UnattachedNodeReport(LocatedProp subject) {
		this.subject = subject;
	}

	@Override
	public ReportType getType() {
		return ReportType.UNATTACHED_NODE;
	}

	@Override
	public String getMessage() {
		final String subjType = subject.locator.result.type;
		final String[] typeParts = subjType.split("\\.");
		return String.format("'%s.%s' returned an AST node not attached to the root of the AST",
				typeParts.length == 0 ? subjType : typeParts[typeParts.length - 1], subject.prop.name);
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
	public JSONObject getDetails() {
		return new JSONObject() //
				.put("subject", subject.locator.toJSON()) //
				.put("property", subject.prop.toJSON()) //
		;

	}

}
