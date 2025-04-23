package tragdor.report.impl;

import org.json.JSONObject;

import codeprober.protocol.data.NodeLocator;
import tragdor.report.BaseReport;
import tragdor.report.ReportType;

public class FailedFindingNodeInNewAstReport extends BaseReport {

	private final NodeLocator subject;

	public FailedFindingNodeInNewAstReport(NodeLocator subject) {
		this.subject = subject;
	}

	@Override
	public ReportType getType() {
		return ReportType.FAILED_FINDING_NODE_IN_NEW_AST;
	}

	@Override
	public String getMessage() {
		final String subjType = subject.result.type;
		final String[] typeParts = subjType.split("\\.");
		return String.format("Could not find '%s' in new AST, it was present during reference run",
				typeParts.length == 0 ? subjType : typeParts[typeParts.length - 1]);
	}

	@Override
	public String getRelatedNodeType() {
		return subject.result.type;
	}

	@Override
	public String getRelatedAttrName() {
		return null;
	}

	@Override
	public JSONObject getDetails() {
		return new JSONObject() //
				.put("subject", subject.toJSON()) //
		;

	}

}
