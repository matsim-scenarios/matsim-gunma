package org.matsim.dashboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.simwrapper.SimWrapper;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.dashboard.*;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@CommandLine.Command(
	name = "gunma-dashboard",
	description = "Run standalone SimWrapper post-processing for existing run output."
)

/*
  This class runs the SimWrapper dashboard generation for Gunma simulations, as stand-alone post-processing step.
 */
public final class GunmaSimwrapperRunner implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(GunmaSimwrapperRunner.class);
	@CommandLine.Parameters(arity = "1..*", description = "Path to run output directories for which the dashboards is to be generated.")
	private List<Path> inputPaths;

	private GunmaSimwrapperRunner() {
	}

	public static void main(String[] args) {
		new GunmaSimwrapperRunner().execute(args);
	}

	@Override
	public Integer call() {

		for (Path runDirectory : inputPaths) {
			log.info("Creating dashboards for {}", runDirectory);

			Path configPath = ApplicationUtils.matchInput("config.xml", runDirectory);
			Config config = ConfigUtils.loadConfig(configPath.toString());
			SimWrapper sw = SimWrapper.create(config);



			SimWrapperConfigGroup simwrapperCfg = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);


			// Facilities Dashboard
			GunmaFacilitiesDashboard facilities = new GunmaFacilitiesDashboard();


			// Population Dashbaord
			GunmaPopulationAttributeDashboard populationAttributeDashboard = new GunmaPopulationAttributeDashboard("resources/population_age_all_ref.csv", "resources/population_age_men_ref.csv", "resources/population_age_women_ref.csv");


			GunmaTripDashboard trips = new GunmaTripDashboard("resources/mode_share_ref.csv", "resources/mode_share_per_dist_ref.csv", "resources/mode_users_ref.csv")
				.withDistanceDistribution("resources/mode_share_distance_distribution.csv")
				.setAnalysisArgs("--match-id", "^gunma.+", "--shp-filter", "none")
				.withChoiceEvaluation(false);



			GunmaActivityDashboard activityDashboard = new GunmaActivityDashboard("resources/jis_zones_gunma.shp");
//			List<GunmaActivityDashboard.Indicator> indicators = List.of(GunmaActivityDashboard.Indicator.COUNTS, GunmaActivityDashboard.Indicator.DENSITY, GunmaActivityDashboard.Indicator.RELATIVE_DENSITY);
			List<GunmaActivityDashboard.Indicator> indicators = List.of(GunmaActivityDashboard.Indicator.COUNTS);
			activityDashboard.addActivityType(
				"work",
				List.of("work"),
				indicators, false,
				"resources/activities_work_per_region-ref.csv"
			);
//
//			activityDashboard.addActivityType(
//				"work",
//				List.of("work"),
//				indicators, true,
//				null
//			);

			activityDashboard.addActivityType(
				"education",
				List.of("education"),
				indicators, true,
				null
			);
//
			activityDashboard.addActivityType(
				"other",
				List.of("other"),
				indicators, true,
				null
			);

			activityDashboard.setProjection("EPSG:2450");

			TrafficDashboard trafficDashboard = new TrafficDashboard(Set.copyOf(config.qsim().getMainModes()));

			// Origins and Destinations, Per Mode, mapped seperately
			ODTripDashboard odTripDashboard = new ODTripDashboard(Set.of(TransportMode.car, TransportMode.walk), "EPSG:2450");


			// Origins and Destinations, flows shown

			GunmaAggrODDashboard aggregateODDashboardGunma = new GunmaAggrODDashboard("resources/jis_zones_50km_clip.shp", "EPSG:2450", "resources/gunma_od_sim.csv", "resources/gunma_outside_od_sim.csv", "resources/gunma_outside_pref_od_sim.csv", "resources/pref_shp.shp");


			// Traffic Counts (based on counts from MLIT)
			TrafficCountsDashboard trafficCountsDashboard = new TrafficCountsDashboard();
			trafficCountsDashboard.withCountsPath("input/v1.3/counts-from-mlit.xml.gz");
			trafficCountsDashboard.withModes(TransportMode.car, Set.of(TransportMode.car));

			sw.addDashboard(facilities);
			sw.addDashboard(populationAttributeDashboard);
			sw.addDashboard(activityDashboard);
			sw.addDashboard(odTripDashboard);
			sw.addDashboard(aggregateODDashboardGunma);
			sw.addDashboard(trips);
			sw.addDashboard(trafficDashboard);
			sw.addDashboard(trafficCountsDashboard);
			try {
				//replace existing dashboards
				boolean append = false;
				sw.generate(runDirectory, append);
				sw.run(runDirectory);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return 0;
	}

}

