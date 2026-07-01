package org.matsim.dashboard;

import jakarta.annotation.Nullable;
import org.matsim.simwrapper.dashboard.TripDashboard;

/**
 * Thin wrapper around the upstream trip dashboard so Gunma can keep its existing wiring.
 */
public class GunmaTripDashboard extends TripDashboard {

	public GunmaTripDashboard(@Nullable String modeShareRefCsv, @Nullable String modeShareDistRefCsv, @Nullable String modeUsersRefCsv) {
		super(modeShareRefCsv, modeShareDistRefCsv, modeUsersRefCsv);
	}

	@Override
	public GunmaTripDashboard withDistanceDistribution(String modeShareDistRefCsv) {
		super.withDistanceDistribution(modeShareDistRefCsv);
		return this;
	}

	@Override
	public GunmaTripDashboard withGroupedRefData(String groupedRefCsv, String... categories) {
		super.withGroupedRefData(groupedRefCsv, categories);
		return this;
	}

	@Override
	public GunmaTripDashboard withChoiceEvaluation(boolean enable) {
		super.withChoiceEvaluation(enable);
		return this;
	}

	@Override
	public GunmaTripDashboard setAnalysisArgs(String... args) {
		super.setAnalysisArgs(args);
		return this;
	}

	@Override
	public GunmaTripDashboard setGroupsOfSubpopulationsForPersonAnalysis(String... groupsOfSubpopulations) {
		super.setGroupsOfSubpopulationsForPersonAnalysis(groupsOfSubpopulations);
		return this;
	}

	@Override
	public GunmaTripDashboard setGroupsOfSubpopulationsForCommercialAnalysis(String... groupsOfSubpopulations) {
		super.setGroupsOfSubpopulationsForCommercialAnalysis(groupsOfSubpopulations);
		return this;
	}
}
