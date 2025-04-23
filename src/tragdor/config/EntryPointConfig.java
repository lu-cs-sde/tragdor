package tragdor.config;

import org.json.JSONObject;

import codeprober.AstInfo;
import tragdor.PropGathering;

public class EntryPointConfig {

	public final String predicate;
	public final String property;
	public final int limit;

	public EntryPointConfig(String predicate, String property, int limit) {
		this.predicate = predicate;
		this.property = property;
		this.limit = limit;
	}

	public static EntryPointConfig fromJSON(JSONObject obj) {
		return new EntryPointConfig(obj.getString("predicate"), obj.getString("property"),
				obj.optInt("limitNodes", Integer.MAX_VALUE));
	}

	public int getNumNodes(AstInfo info, PropGathering pg) {
		return pg.countSearch(info, predicate, limit);
	}

	@Override
	public String toString() {
		return String.format("Pred:'%s', Prop:'%s', Limit:%d", predicate, property, limit);
	}
}
