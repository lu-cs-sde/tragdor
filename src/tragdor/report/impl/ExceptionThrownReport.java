package tragdor.report.impl;

import org.json.JSONArray;
import org.json.JSONObject;

import tragdor.LocatedProp;
import tragdor.report.BaseReport;
import tragdor.report.ReportType;

public class ExceptionThrownReport extends BaseReport {

	private final LocatedProp offendingProp;
	private final Throwable exec;

	public ExceptionThrownReport(LocatedProp offendingProp, Throwable exec) {
		this.offendingProp = offendingProp;
		this.exec = exec;
 	}

	@Override
	public ReportType getType() {
		return ReportType.EXCEPTION_THROWN;
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
		return String.format("'%s.%s' threw '%s' during evaluation",
				typeParts.length == 0 ? subjType : typeParts[typeParts.length - 1], offendingProp.prop.name,
				exec.getClass().getName());
	}

	@Override
	public JSONObject getDetails() {
//		exec.getStackTrace()
		final JSONArray stack = new JSONArray();
		for (StackTraceElement elem : exec.getStackTrace()) {
			if (elem.getClassName().startsWith("codeprober.metaprogramming.Reflect")) {
				// Bridge between us and the target tool, no need to include further info
				break;
			}
			stack.put(new JSONObject() //
					.put("line", elem.getLineNumber()) //
					.put("class", elem.getClassName()) //
					.put("file", elem.getFileName()) //
					.put("mth", elem.getMethodName()) //
			);
		}
		return new JSONObject() //
				.put("message", exec.getMessage() == null ? "" : exec.getMessage()) //
				.put("subject", offendingProp.locator.toJSON()) //
				.put("prop", offendingProp.prop.toJSON()) //
				.put("stackTrace", stack) //
		;
	}
}
