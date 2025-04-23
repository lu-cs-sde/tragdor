package tragdor.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

public class ToolConfig {

	public final String tool;
	public final String[] args;

	public ToolConfig(String tool, String[] args) {
		this.tool = tool;
		this.args = args;
	}

	public static List<ToolConfig> fromJSON(JSONObject obj) {
		final String tool = obj.getString("jar");
		final JSONArray rawArgs = obj.optJSONArray("args");
		if (rawArgs == null || rawArgs.isEmpty()) {
			return Arrays.asList(new ToolConfig(tool, new String[0]));
		}
		final List<JSONArray> argsSources = new ArrayList<>();
		if (rawArgs.get(0) instanceof JSONArray) {
			// Multiple args
			for (int i = 0; i < rawArgs.length(); ++i) {
				argsSources.add(rawArgs.getJSONArray(i));
			}
		} else {
			argsSources.add(rawArgs);
		}

		final List<ToolConfig> ret = new ArrayList<>();
		for (JSONArray args : argsSources) {
			ret.add(new ToolConfig(tool,
					args.toList().stream().map(x -> x.toString()).collect(Collectors.toList()).toArray(new String[0])));

		}
		return ret;
	}

	@Override
	public String toString() {
		return String.format("Tool:'%s', Args:'%s'", tool, Arrays.toString(args));
	}

}
