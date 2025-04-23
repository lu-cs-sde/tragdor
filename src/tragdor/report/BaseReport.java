package tragdor.report;

import org.json.JSONObject;

public abstract class BaseReport implements Report {

	private String jsonStr;
	private int toolIdx = -1;
	private long discoveryTimeMs = -1L;

	@Override
	public JSONObject toJSON() {
		final JSONObject res = Report.super.toJSON();
		if (jsonStr == null) {
			jsonStr = res.toString();
		}
		// Put this _after_ the jsonStr assign. Don't want to deduplicate/compare with
		// the toolIdx/discoveryTime
		if (toolIdx != -1) {
			res.put("toolIdx", toolIdx);
		}
		if (discoveryTimeMs != -1L) {
			res.put("discoveryTimeMs", discoveryTimeMs);
		}
		return res;
	}

	@Override
	public int hashCode() {
		toJSON();
		return jsonStr.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		toJSON();
		if (obj instanceof BaseReport) {
			BaseReport other = (BaseReport) obj;
			other.toJSON();
			return jsonStr.equals(other.jsonStr);
		}
		if (obj instanceof Report) {
			return jsonStr.equals(((Report) obj).toJSON().toString());
		}
		return false;
	}

	@Override
	public void setToolConfigIndex(int idx) {
		toolIdx = idx;
	}

	@Override
	public void setDiscoveryTimeMs(long ms) {
		discoveryTimeMs = ms;
	}
}
