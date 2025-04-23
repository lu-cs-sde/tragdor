package tragdor.contrib;

import java.util.Objects;

import tragdor.LocatedProp;
import tragdor.report.BaseReport;

public class PricedReport {
	public final int cost;
	public final LocatedProp relatedProp;
	public final BaseReport report;

	public PricedReport(int cost, LocatedProp relatedProp, BaseReport report) {
		this.cost = cost;
		this.relatedProp = relatedProp;
		this.report = report;
	}

	@Override
	public int hashCode() {
		return Objects.hash(cost, relatedProp, report);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PricedReport other = (PricedReport) obj;
		return cost == other.cost && relatedProp.equals(other.relatedProp) && report.equals(other.report);
	}
}
