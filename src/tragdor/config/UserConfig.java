package tragdor.config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import org.json.JSONArray;
import org.json.JSONObject;

import codeprober.AstInfo;
import tragdor.util.Exit;
import tragdor.util.ReportFilterLoader;
import tragdor.util.ReportFilterLoader.FilterDecision;

public class UserConfig {

	private final JSONObject rootConfig;
	private final JSONObject tragdorConfig;
	private List<EntryPointConfig> cachedEntryPoints;
	private List<ToolConfig> cachedToolConfigs;
	private AstInfo mostRecentParseResult;
	private BiPredicate<String, String> cachedAttrExclusionPredicate;

	private int activeToolCfgIdx = 0;

	public UserConfig(JSONObject obj) {
		this.rootConfig = obj;
		this.tragdorConfig = this.rootConfig.getJSONObject("tragdor");
	}

	public JSONObject getRootConfig() {
		return rootConfig;
	}

	public JSONObject getTragdorConfig() {
		return tragdorConfig;
	}

	public void setActiveConfigIndex(int idx) {
		this.activeToolCfgIdx = idx;
	}

	public int getActiveToolConfigIdx() {
		return activeToolCfgIdx;
	}

	public AstInfo reparse() {
		mostRecentParseResult = AstGlue.reparse(getToolConfigs().get(activeToolCfgIdx));
		return mostRecentParseResult;
	}

	public AstInfo getMostRecentParseResult() {
		return mostRecentParseResult;
	}

	public String getSearchAlgorithm() {
		return tragdorConfig.optString("search_algorithm", "random_order");
	}

	public boolean shouldExcludeReport(String nodeType, String attrType) {
		if (cachedAttrExclusionPredicate == null) {
			final JSONObject filter = tragdorConfig.optJSONObject("filter");
			if (filter == null) {
				cachedAttrExclusionPredicate = (a, b) -> false;
			} else {
				final BiFunction<String, String, FilterDecision> parsed = ReportFilterLoader.parseFilter(filter);
				cachedAttrExclusionPredicate = (node, attr) -> {
					return parsed.apply(node, attr) == FilterDecision.EXCLUDE;
				};
			}
		}
		return cachedAttrExclusionPredicate.test(nodeType, attrType);
	}

	public List<EntryPointConfig> getEntryPoints() {
		if (cachedEntryPoints == null) {
			JSONArray raw = tragdorConfig.getJSONArray("discoveryEntryPoints");
			if (raw.isEmpty()) {
				throw new Error("dynsed.discoveryEntryPoints is empty");
			}
			cachedEntryPoints = new ArrayList<>();
			for (int i = 0; i < raw.length(); ++i) {
				cachedEntryPoints.add(EntryPointConfig.fromJSON(raw.getJSONObject(i)));
			}
		}
		return cachedEntryPoints;
	}

	public List<ToolConfig> getToolConfigs() {
		if (cachedToolConfigs == null) {
			final JSONObject toolCfg = tragdorConfig.getJSONObject("tool");
			final List<ToolConfig> singlePass = ToolConfig.fromJSON(toolCfg);

			int repeats = tragdorConfig.optInt("repeat_passes", 1);
			cachedToolConfigs = new ArrayList<>();
			for (int i = 0; i < repeats; ++i) {
				cachedToolConfigs.addAll(singlePass);
			}
		}
		return cachedToolConfigs;
	}

	public static UserConfig parse(InputStream src) throws IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[512];
		int read;
		while ((read = src.read(buf)) != -1) {
			baos.write(buf, 0, read);
		}
		final byte[] arr = baos.toByteArray();
		final JSONObject obj = new JSONObject(new String(arr, 0, arr.length, StandardCharsets.UTF_8));
		if (!obj.has("tragdor")) {
			System.out.println("Invalid config, should contain 'tragdor' in the root");
			throw new IOException("Bad stream");
		}
		final UserConfig ret = new UserConfig(obj);

		for (Entry<Object, Object> x : System.getProperties().entrySet()) {
			final String key = x.getKey() + "";
			final String val = x.getValue() + "";
			if (key.startsWith("tragdor.cfg.")) {
				final String subkey = key.substring("tragdor.cfg.".length());
				System.out.println("Overriding " + subkey + " with " + val);
				switch (subkey) {
				case "tool.jar":
					ret.tragdorConfig.getJSONObject("tool").put("jar", val);
					break;

				case "tool.args":
					ret.tragdorConfig.getJSONObject("tool").put("args", new JSONArray(val.split(",")));
					break;

				case "search_algorithm":
					ret.tragdorConfig.put("search_algorithm", val);
					break;

				case "repeat_passes":
					ret.tragdorConfig.put("repeat_passes", Integer.parseInt(val));
					break;

				default:
					System.err.println("Unknown config key '" + key + "'");
					Exit.exit(1);
					break;
				}
			}
		}

		// Intentionally call these early to trigger errors in case of bad config
		ret.getEntryPoints();
		ret.getToolConfigs();

		return ret;
	}

	public static UserConfig parse(File src) throws IOException {
		try (FileInputStream fis = new FileInputStream(src)) {
			return parse(fis);
		}
	}
}
