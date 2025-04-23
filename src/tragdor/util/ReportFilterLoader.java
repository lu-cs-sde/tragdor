package tragdor.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.json.JSONArray;
import org.json.JSONObject;

public class ReportFilterLoader {

	public static enum FilterDecision {
		INCLUDE, EXCLUDE
	};

	private static class ThingFilterEntry {
		public final String nodeType, attrName;
		public final boolean nodeUseEndsWith, attrUseEndsWith;

		public ThingFilterEntry(String nodeType, boolean nodeUseEndsWith, String attrName, boolean attrUseEndsWith) {
			this.nodeType = nodeType;
			this.attrName = attrName;
			this.nodeUseEndsWith = nodeUseEndsWith;
			this.attrUseEndsWith = attrUseEndsWith;
		}

		public boolean matches(String nodeType, String attrName) {
			if (nodeType == null) {
				if (nodeUseEndsWith && this.nodeType.equals("")) {
					return false;
				}
			} else {
				if (!(nodeUseEndsWith //
						? nodeType.endsWith(this.nodeType)
								: nodeType.equals(this.nodeType))) {
					return false;
				}
			}
			if (attrName == null) {
				if (attrUseEndsWith && this.attrName.equals("")) {
					return false;
				} else {
					// Match!
				}
			} else {
				if (!(attrUseEndsWith //
						? attrName.endsWith(this.attrName)
						: attrName.equals(this.attrName))) {
					return false;
				}
			}
			return true;
		}
	}

	private static class ThingFilter {

		private List<ThingFilterEntry> instances = new ArrayList<>();

		public void add(Object rawEntry) {
			if (rawEntry instanceof JSONArray) {
				final JSONArray arr = ((JSONArray) rawEntry);
				for (int i = 0; i < arr.length(); ++i) {
					add(arr.getString(i));
				}
			} else if (rawEntry instanceof String) {
				add((String) rawEntry);
			} else {
				throw new IllegalArgumentException(
						"Bad config - filter value '" + rawEntry + "' is neither an array of strings nor a string");
			}
		}

		public void add(String entry) {
			final String[] parts = entry.split("\\.");
			if (parts.length == 1) {
				if (entry.equals("*")) {
					instances.add(new ThingFilterEntry("", true, "", true));
					return;
				}
				throw new IllegalArgumentException(
						"Bad config - filter value '" + entry + "' does not follow form 'Type.attr'");
			}

			final boolean shorthandType = parts.length == 2;
			String type = "";
			for (int j = 0; j < parts.length - 1; j++) {
				if (j > 0) {
					type += ".";
				}
				type += parts[j];
			}
			final String attr = parts[parts.length - 1];
			final boolean wildcardType = type.equals("*");
			final boolean wildcardAttr = attr.equals("*");
			instances.add(new ThingFilterEntry( //
					wildcardType ? "" : (shorthandType ? ("." + type) : type), wildcardType || shorthandType, //
					wildcardAttr ? "" : attr, wildcardAttr));
		}

		public boolean matches(String nodeType, String attrType) {
			for (ThingFilterEntry tfe : instances) {
				if (tfe.matches(nodeType, attrType)) {
					return true;
				}
			}
			return false;
		}
	}

	public static BiFunction<String, String, FilterDecision> parseFilter(JSONObject src) {
		final Object exclude = src.opt("exclude");
		final Object include = src.opt("include");
		if ((exclude == null) && (include == null)) {
			throw new IllegalArgumentException(
					"Bad config - must specify at least one of 'include' or 'exclude' in a filter");
		}

		final ThingFilter includeFilter;
		if (include != null) {
			includeFilter = new ThingFilter();
			includeFilter.add(include);
		} else {
			includeFilter = null;
		}

		final ThingFilter excludeFilter;
		if (exclude != null) {
			excludeFilter = new ThingFilter();
			excludeFilter.add(exclude);
		} else {
			excludeFilter = null;
		}

		return (nodeType, attrName) -> {
			if (includeFilter != null && includeFilter.matches(nodeType, attrName)) {
				return FilterDecision.INCLUDE;
			}
			if (excludeFilter != null && excludeFilter.matches(nodeType, attrName)) {
				return FilterDecision.EXCLUDE;
			}
			// Include everything by default
			return FilterDecision.INCLUDE;

		};
	}
}
