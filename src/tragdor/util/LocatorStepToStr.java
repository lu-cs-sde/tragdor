package tragdor.util;

import java.util.List;
import java.util.stream.Collectors;

import codeprober.protocol.data.NodeLocatorStep;
import codeprober.protocol.data.Property;
import codeprober.protocol.data.PropertyArg;

public class LocatorStepToStr {

	public static String propArgToStr(PropertyArg arg) {
		switch (arg.type) {
		case bool:
		case integer:
			return arg.value + "";
		case string:
			return "\"" + arg.value + "\"";

		default:
			return arg.toJSON().toString();
		}

	}

	public static String stepsToStr(List<NodeLocatorStep> steps) {
		return "[" + steps.stream().map(x -> {
			switch (x.type) {
			case child:
				return x.asChild() + "";

			case nta:

				final Property ntav = x.asNta().property;
				if (ntav.args == null || ntav.args.isEmpty()) {
					return String.format("%s()", ntav.name);
				}
				return String.format("%s(%s)", ntav.name, ntav.args.stream().map(y -> propArgToStr(y)).collect(Collectors.joining(", ")));

			case tal:
			default:
				return x.asTal().toJSON().toString();
			}
		}).collect(Collectors.joining(", ")) + "]";
	}
}
