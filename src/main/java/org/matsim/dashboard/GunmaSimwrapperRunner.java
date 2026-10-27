package org.matsim.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@CommandLine.Command(
	name = "gunma-dashboard",
	mixinStandardHelpOptions = true,
	description = "Run standalone SimWrapper post-processing for existing run output."
)

/*
  This class runs the SimWrapper dashboard generation for Gunma simulations, as stand-alone post-processing step.
 */
public final class GunmaSimwrapperRunner implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(GunmaSimwrapperRunner.class);
	@CommandLine.Parameters(arity = "1..*", description = "Path to run output directories for which the dashboards is to be generated.")
	private List<Path> inputPaths;

	@CommandLine.Option(names = "--counts", defaultValue = "input/v1.8/gunma-v1.8-counts-jartic.xml.gz",
		description = "Observed traffic counts (v1.9 reuses the v1.8 input data).")
	private Path countsPath;

	@CommandLine.Option(names = "--generate-only", description = "Generate dashboard definitions without running analyses.")
	private boolean generateOnly;

	private GunmaSimwrapperRunner() {
	}

	public static void main(String[] args) {
		new GunmaSimwrapperRunner().execute(args);
	}

	@Override
	public Integer call() {

		if (!Files.isRegularFile(countsPath)) {
			throw new IllegalArgumentException("Missing observed counts: " + countsPath.toAbsolutePath());
		}
		// Check both cases before writing either dashboard set. Heavy files stay on the cluster.
		for (Path runDirectory : inputPaths) {
			ApplicationUtils.matchInput("config.xml", runDirectory);
			if (!generateOnly) {
				for (String input : List.of("events.xml", "network.xml", "plans.xml", "activities.csv",
					"persons.csv", "trips.csv", "logfile.log", "modestats.csv", "scorestats.csv")) {
					ApplicationUtils.matchInput(input, runDirectory);
				}
			}
		}

		for (Path runDirectory : inputPaths) {
			log.info("Creating dashboards for {}", runDirectory);

			Path configPath = ApplicationUtils.matchInput("config.xml", runDirectory);
			Config config = ConfigUtils.loadConfig(configPath.toString());
			SimWrapperConfigGroup simwrapperCfg = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
			simwrapperCfg.setSampleSize(config.qsim().getFlowCapFactor());
			log.info("Post-processing {} with sample size {} and counts {}", runDirectory,
				simwrapperCfg.getSampleSize(), countsPath.toAbsolutePath());
			SimWrapper sw = SimWrapper.create(config);


			// Facilities Dashboard
			GunmaFacilitiesDashboard facilities = new GunmaFacilitiesDashboard();


			// Population Dashbaord
			GunmaPopulationAttributeDashboard populationAttributeDashboard = new GunmaPopulationAttributeDashboard("resources/population_age_all_ref.csv", "resources/population_age_men_ref.csv", "resources/population_age_women_ref.csv");


			GunmaTripDashboard trips = new GunmaTripDashboard("resources/mode_share_ref.csv", "resources/mode_share_per_dist_ref.csv", "resources/mode_users_ref.csv")
				.withDistanceDistribution("resources/mode_share_distance_distribution.csv")
				.setAnalysisArgs("--match-id", "^gunma.+", "--shp-filter", "none")
				.withGroupedRefData("resources/mode_share_per_group_dist_ref.csv", "age")
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

			GunmaAggrODDashboard aggregateODDashboardGunma = new GunmaAggrODDashboard("EPSG:2450");
			aggregateODDashboardGunma
				.addTab("within-gunma", "resources/gunma_od_sim.csv", "resources/jis_zones_50km_clip.shp")
				.addTab("inflow-muncip", "resources/gunma_inflow_od_sim.csv", "resources/jis_zones_50km_clip.shp")
				.addTab("outflow-muncip", "resources/gunma_outflow_od_sim.csv", "resources/jis_zones_50km_clip.shp")
				.addTab("inflow-pref", "resources/gunma_inflow_pref_od_sim.csv", "resources/pref_shp.shp")
				.addTab("outflow-pref", "resources/gunma_outflow_pref_od_sim.csv", "resources/pref_shp.shp");



//			GunmaAggrODDashboard aggregateODDashboardGunma = new GunmaAggrODDashboard("resources/jis_zones_50km_clip.shp", "EPSG:2450", "resources/gunma_od_sim.csv", "resources/gunma_outside_od_sim.csv", "resources/gunma_outside_pref_od_sim.csv", "resources/pref_shp.shp");


			// Traffic Counts (based on counts from MLIT)
			TrafficCountsDashboard trafficCountsDashboard = new TrafficCountsDashboard();
			trafficCountsDashboard.withCountsPath(countsPath.toAbsolutePath().toString());
			trafficCountsDashboard.withModes(TransportMode.car, Set.of(TransportMode.car));

			OverviewDashboard overview = new OverviewDashboard();
			sw.addDashboard(overview);
//			sw.addDashboard(facilities);
			sw.addDashboard(populationAttributeDashboard);
			sw.addDashboard(activityDashboard);
			sw.addDashboard(odTripDashboard);
			sw.addDashboard(aggregateODDashboardGunma);
			sw.addDashboard(trips);
//			sw.addDashboard(trafficDashboard);
			sw.addDashboard(trafficCountsDashboard);
			try {
				//replace existing dashboards
				boolean append = false;
				sw.generate(runDirectory, append);
				if (!generateOnly) {
					sw.run(runDirectory);
					// SimWrapper logs some analysis errors instead of propagating them. Check every
					// generated dashboard's data dependencies before declaring the run upload-ready.
					ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
					List<String> missing = new ArrayList<>();
					try (var dashboards = Files.newDirectoryStream(runDirectory, "dashboard-*.yaml")) {
						for (Path dashboard : dashboards) {
							ArrayDeque<JsonNode> nodes = new ArrayDeque<>();
							nodes.add(mapper.readTree(dashboard.toFile()));
							while (!nodes.isEmpty()) {
								JsonNode node = nodes.remove();
								if (node.isContainerNode()) node.forEach(nodes::add);
								else if (node.isTextual() && node.asText().startsWith("analysis/")
									&& !Files.isRegularFile(runDirectory.resolve(node.asText()))) {
									missing.add(node.asText());
								}
							}
						}
					}
					if (!missing.isEmpty()) {
						throw new IOException("Incomplete dashboard data in " + runDirectory + ": " + missing);
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return 0;
	}

}
