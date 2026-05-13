package org.matsim.accessibility;

import org.apache.commons.io.FileUtils;
import org.geotools.api.feature.simple.SimpleFeature;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.AccessibilityFromEvents;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.contrib.drt.estimator.DrtEstimator;
import org.matsim.contrib.drt.estimator.impl.DirectTripBasedDrtEstimator;
import org.matsim.contrib.drt.estimator.impl.distribution.NoDistribution;
import org.matsim.contrib.drt.estimator.impl.trip_estimation.ConstantRideDurationEstimator;
import org.matsim.contrib.drt.estimator.impl.waiting_time_estimation.ConstantWaitingTimeEstimator;
import org.matsim.contrib.drt.estimator.impl.waiting_time_estimation.ShapeFileBasedWaitingTimeEstimator;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.FacilitiesConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.dashboard.AccessibilityDashboardGunma;
import org.matsim.simwrapper.SimWrapper;
import org.matsim.simwrapper.SimWrapperConfigGroup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs offline accessibility analysis for Gunma Prefecture in Japan.
 */
public final class RunGunmaAccessibility {

	static final String coordinateSystem = "EPSG:2450";
	static final String dirToCopy = "../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.6/2026-03-13-base/";

	//	static final List<String> relevantPois = List.of("supermarket", "public_bath", "hospital", "shinkansen", "middle");
	static final List<String> relevantPois = List.of("supermarket", "shinkansen");

	static final List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.car, Modes4Accessibility.teleportedWalk);
//	static final List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.estimatedDrt);

	private static final boolean personBased = true;

	private static final boolean drtDifferentiated = false;

	private static final PopulationFilter filterPopulation = PopulationFilter.base;


	static final String outputDir = "../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.6/2026-04-17a-carWalk-supermarketShinkansen";

	// DRT Estimator

	// SMALL: Fleet Size of 500 vehicles
//	static final String outputDir = "../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.6/2026-04-13/01-drt-500/";
//	private static final double drtDetourIntercept = 1.31;
//	private static final double drtDetourSlope = 1.09;
//	private static final ShpOptions drtWaitShp = new ShpOptions("../shared-svn/projects/matsim-gunma/data/processed/drt/wait_times-small-gunma.shp", "EPSG:2450", null);
//	private static final int baseTypicalWaitingTime = 3837;

	// SMALL: Fleet Size of 1000 vehicles
//	static final String outputDir = "../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.6/2026-04-13/04-drt-1000-uniform-wait-time/";
//	private static final double drtDetourIntercept = 0.479;
//	private static final double drtDetourSlope = 1.18;
//	private static final ShpOptions drtWaitShp = new ShpOptions("../shared-svn/projects/matsim-gunma/data/processed/drt/wait_times-small-gunma.shp", "EPSG:2450", null);
//	private static final int baseTypicalWaitingTime = 838;
//
	private static final int drtConstantWaitTime = 632;


	// LARGE: Fleet Size of 1500 vehicles
//	static final String outputDir = "../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.6/2026-04-13/01-drt-1500/";
	private static final double drtDetourIntercept = 0.787;
	private static final double drtDetourSlope = 1.21;
	private static final ShpOptions drtWaitShp = new ShpOptions("../shared-svn/projects/matsim-gunma/data/processed/drt/wait_times-large-gunma.shp", "EPSG:2450", null);
	private static final int baseTypicalWaitingTime = 440;

	// DRT SCORING
	//old
//	private static double drtScoringAsc = 0.0;
//	private static double drtScoringBetaDist = -2.5E-4;
//	private static double drtScoringBetaTime = 0.0;
//	private static double drtScoringMonetaryDist = 0.0;

	//new
	private static double drtScoringAsc = -1.653;
	private static double drtScoringBetaTime = -2.04;
	private static double drtScoringBetaDist = 0.0;
	private static double drtScoringMonetaryDist = -0.036;

	// test
//	private static double drtScoringAsc = 0.0;
//	private static double drtScoringBetaTime = 0.0;
//	private static double drtScoringBetaDist = 0.0;
//	private static double drtScoringMonetaryDist = 0.0;
	// 	 Constant Wait time



	private RunGunmaAccessibility() {
		// prevent instantiation
	}


	static void main(String[] args) throws IOException {

		FileUtils.copyDirectory(new File(dirToCopy), new File(outputDir));

		String mapCenterString = "139.0266,36.5211";


		calculateAccessibility();


		// CREATE DASHBOARD
//		createDashboard(mapCenterString);
	}

	@SuppressWarnings("checkstyle:JavaNCSS")
	private static void calculateAccessibility() throws IOException {
		AccessibilityConfigGroup accConfig = new AccessibilityConfigGroup();

		if (personBased) {

			accConfig.setPersonBased(true);
			accConfig.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromPopulation);


		} else {
			accConfig.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromShapeFile);

			accConfig.setShapeFileCellBasedAccessibility("../shared-svn/projects/matsim-gunma/data/raw/01_shapefiles/gunma_2450/gunma_2450.shp");
			// extents of gunma from qgis -- this really shouldn't be neccessary if we use shp file...
			accConfig.setBoundingBoxLeft(-8874.3893231801757793);
			accConfig.setBoundingBoxBottom(104516.0981667207088321);
			accConfig.setBoundingBoxRight(-1513.2262783532476078);
			accConfig.setBoundingBoxTop(117287.2301061319012661);

		}

		accConfig.setTileSize_m(500);

		accConfig.setTimeOfDay(8 * 3600.);

		for (Modes4Accessibility mode : Modes4Accessibility.values()) {
			accConfig.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}

		// Accessibility Calculation

		String configFile = ApplicationUtils.matchInput("output_config.xml", Path.of(outputDir)).toString();
		String eventsFile = ApplicationUtils.matchInput("output_events.xml.gz", Path.of(outputDir)).toString();
//		String populationFile = ApplicationUtils.matchInput("output_plans.xml.gz", Path.of(outputDir)).toString();
//		String networkFile = ApplicationUtils.matchInput("output_network.xml.gz", Path.of(outputDir)).toString();

//		String networkFile = "input/v" + OpenGunmaScenario.VERSION + "/gunma-v" + OpenGunmaScenario.VERSION + "-network.xml.gz";
		String networkFile = "gunma.output_network.xml.gz";
		String populationFile = "gunma.output_plans.xml.gz";

		String poisFile = "/Users/jakob/git/shared-svn/projects/matsim-gunma/data/processed/01_shapefiles/osm_buffer5km/pois.xml";



		final Config config = ConfigUtils.loadConfig(configFile);

		config.plans().setInputFile(populationFile);
		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(outputDir);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		config.routing().setRoutingRandomness(0.);
		config.facilities().setInputFile(poisFile);

		config.vehicles().setVehiclesFile(null);
//		config.facilities().setInputFile(null);

		config.global().setCoordinateSystem("EPSG:2450");

		config.network().setInputFile(networkFile);
		config.addModule(accConfig);






		//DRT config
		// drt config
		ConfigUtils.addOrGetModule( config, DvrpConfigGroup.class );

		DrtConfigGroup drtConfigGroup = new DrtConfigGroup();
		drtConfigGroup.setOperationalScheme(DrtConfigGroup.OperationalScheme.door2door);
//		drtConfigGroup.setTransitStopFile(stopsFile);

		drtConfigGroup.addOrGetDrtOptimizationConstraintsParams().addOrGetDefaultDrtOptimizationConstraintsSet().setMaxWalkDistance(100000.);

		MultiModeDrtConfigGroup multiModeDrtConfigGroup = new MultiModeDrtConfigGroup();
		multiModeDrtConfigGroup.addParameterSet(drtConfigGroup);
		config.addModule(multiModeDrtConfigGroup);
		config.addModule(drtConfigGroup);


		// scoring
		ScoringConfigGroup.ModeParams drtParams = new ScoringConfigGroup.ModeParams(TransportMode.drt);
		drtParams.setConstant(drtScoringAsc);
		drtParams.setMarginalUtilityOfDistance(drtScoringBetaDist);
		drtParams.setMarginalUtilityOfTraveling(drtScoringBetaTime);
		drtParams.setMonetaryDistanceRate(drtScoringMonetaryDist);
		config.scoring().addModeParams(drtParams);


		// SCENARIO



		MutableScenario scenario = (MutableScenario) ScenarioUtils.loadScenario(config);


		Set<Id<Person>> commutersIds = scenario.getPopulation().getPersons().keySet().stream().filter(id -> id.toString().startsWith("commuter_")).collect(Collectors.toSet());
		commutersIds.forEach(id -> scenario.getPopulation().removePerson(id));


		if (filterPopulation == PopulationFilter.filter85plus) {

			// Only look at 85+ agents!
			Set<Id<Person>> personsToRemove = new HashSet<>();
			for (Person person : scenario.getPopulation().getPersons().values()) {
				if (person.getAttributes().getAttribute("age") == null ||
					(int) person.getAttributes().getAttribute("age") < 85) {

//					person.getAttributes().getAttribute("zone") != "10383") consider including 10382

					personsToRemove.add(person.getId());
				}

//				if(!String.valueOf(person.getAttributes().getAttribute("zone")).equals("10383")){
//					personsToRemove.add(person.getId());
//				}
			}

			for (Id<Person> personId : personsToRemove) {
				scenario.getPopulation().getPersons().remove(personId);
			}
		} else if (filterPopulation == PopulationFilter.threeAgents) {
			Set<Id<Person>> personsToRemove = new HashSet<>(scenario.getPopulation().getPersons().keySet());
			personsToRemove.remove(Id.createPersonId("gunma_f000110a2"));
			personsToRemove.remove(Id.createPersonId("gunma_f0039c14b"));
			personsToRemove.remove(Id.createPersonId("gunma_f0199eb38"));

			for (Id<Person> personId : personsToRemove) {
				scenario.getPopulation().getPersons().remove(personId);
			}
		}


		//		== Bounding box from persons home coords ==
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;

		for (Person person : scenario.getPopulation().getPersons().values()) {
			Double x = (Double) person.getAttributes().getAttribute("home_x");
			Double y = (Double) person.getAttributes().getAttribute("home_y");
			if (x != null && y != null) {
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
		}
		accConfig.setBoundingBoxBottom(minY).setBoundingBoxTop(maxY)
			.setBoundingBoxLeft(minX).setBoundingBoxRight(maxX);

		DrtEstimator drtEstimator;
		if (drtDifferentiated){

			List<SimpleFeature> zonesToWaitTimes = drtWaitShp.readFeatures();
			drtEstimator = new DirectTripBasedDrtEstimator.Builder()
				// baseTypicalWaitTime should be egal, but I will set it as the highest zonal waiting time
				.setWaitingTimeEstimator(new ShapeFileBasedWaitingTimeEstimator(scenario.getNetwork(), zonesToWaitTimes, baseTypicalWaitingTime))
				.setWaitingTimeDistributionGenerator(new NoDistribution())
				.setRideDurationEstimator(new ConstantRideDurationEstimator(drtDetourSlope, drtDetourIntercept))
				.setRideDurationDistributionGenerator(new NoDistribution())
				.build();

		} else {
			drtEstimator = new DirectTripBasedDrtEstimator.Builder()
				.setWaitingTimeEstimator(new ConstantWaitingTimeEstimator(drtConstantWaitTime))
				.setWaitingTimeDistributionGenerator(new NoDistribution())
				.setRideDurationEstimator(new ConstantRideDurationEstimator(drtDetourSlope, drtDetourIntercept))
				.setRideDurationDistributionGenerator(new NoDistribution())
				.build();

		}

		AccessibilityFromEvents.Builder builder = new AccessibilityFromEvents.Builder(scenario, eventsFile, relevantPois);
		builder.setDrtEstimator(drtEstimator);
		builder.build().run();
	}

	private static void createDashboard(String mapCenterString) {

		final Config config = ConfigUtils.createConfig();
		config.controller().setOutputDirectory(outputDir);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);


		//CONFIG
		config.controller().setLastIteration(0);
		config.controller().setWritePlansInterval(-1);
		config.controller().setWriteEventsInterval(-1);
		config.global().setCoordinateSystem(coordinateSystem);



		config.facilities().setFacilitiesSource(FacilitiesConfigGroup.FacilitiesSource.none);

		//simwrapper
		SimWrapperConfigGroup group = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		group.setSampleSize(0.001);
		if (mapCenterString != null) {
			group.defaultParams().setMapCenter(mapCenterString);
		}
		group.setDefaultDashboards(SimWrapperConfigGroup.DefaultDashboardsMode.disabled);


		SimWrapper sw = SimWrapper.create(config)
			.addDashboard(new AccessibilityDashboardGunma(coordinateSystem, relevantPois, List.of(Modes4Accessibility.car)));

		boolean append = true;
		try {
			sw.generate(Path.of(outputDir), append);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		sw.run(Path.of(outputDir));

	}

	/**
	 * Defines which policy case (or base case) is being simulated.
	 */
	public enum PopulationFilter {
		base,
		filter85plus,
		threeAgents
	}
}
