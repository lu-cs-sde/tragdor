package tragdor.report;

import org.json.JSONObject;

public interface Report {

	ReportType getType();

	String getMessage();

	String getRelatedNodeType();

	String getRelatedAttrName();

	void setToolConfigIndex(int idx);
	void setDiscoveryTimeMs(long ms);

	default JSONObject getDetails() {
		return new JSONObject();
	}

	default JSONObject toJSON() {
		return new JSONObject() //
				.put("type", getType().name()) //
				.put("message", getMessage()) //
				.put("details", getDetails()) //
		;
	}

//	@Override
//	default boolean equals(Object other) {
//		return other instanceof Report && getJSON().toString().equals(((Report)other).getJSON().toString());
//	}
}
