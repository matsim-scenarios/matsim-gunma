package org.matsim.prepare;

import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.SampleOptions;
import org.matsim.application.prepare.CreateLandUseShp;
import org.matsim.application.prepare.network.CleanNetwork;
import org.matsim.application.prepare.network.CreateNetworkFromSumo;
import org.matsim.application.prepare.population.*;
import org.matsim.application.prepare.pt.CreateTransitScheduleFromGtfs;
import org.matsim.contrib.cadyts.car.CadytsCarModule;
import org.matsim.contrib.cadyts.car.CadytsContext;
import org.matsim.contrib.cadyts.general.CadytsScoring;
import org.matsim.contrib.vsp.scenario.SnzActivities;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.mediumcompressed.MediumCompressedNetworkRouteFactory;
import org.matsim.core.replanning.choosers.ForceInnovationStrategyChooser;
import org.matsim.core.replanning.choosers.StrategyChooser;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.DefaultAnalysisMainModeIdentifier;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.dashboard.GunmaSimwrapperRunner;
import org.matsim.prepare.counts.CreateCountsFromJarticData;
import org.matsim.prepare.counts.CreateCountsFromMlitData;
import org.matsim.prepare.facilities.CreateMATSimFacilitiesGunma;
import org.matsim.prepare.facilities.FacilitiesFilter;
import org.matsim.prepare.opt.RunCountOptimization;
import org.matsim.prepare.opt.SelectPlansFromIndex;
import org.matsim.prepare.population.*;

import org.matsim.prepare.vehicles.PrepareVehicleTypes;
import org.matsim.run.Activities;
import org.matsim.run.OpenGunmaScenario;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.SimWrapperModule;
import picocli.CommandLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.matsim.run.OpenGunmaScenario.*;

/**
 * This scenario class is used for run a MATSim scenario in various stages of the calibration process.
 */
@CommandLine.Command(header = ":: Open Gunma Calibration ::", mixinStandardHelpOptions = true)
@MATSimApplication.Prepare({GunmaSimwrapperRunner.class,
	CreateLandUseShp.class, CreateGunmaPopulation.class, CreateGunmaCommuterPopulation.class, MergePopulations.class,
	DownSamplePopulation.class,
	CreateNetworkFromSumo.class, CreateTransitScheduleFromGtfs.class,
	CleanNetwork.class, RunActivitySampling.class, InitLocationChoice.class,
	CreateMATSimFacilitiesGunma.class, FacilitiesFilter.class, LookupJisZone.class, CreateCountsFromMlitData.class, CreateCountsFromJarticData.class,
	RunCountOptimization.class, SelectPlansFromIndex.class, SplitActivityTypesDuration.class, AmendStartTimeCommuters.class, PrepareVehicleTypes.class, SplitMorningEveningActivities.class})



public class RunOpenGunmaCalibration extends MATSimApplication {

	/**
	 * Scaling factor if all persons use car (~70% share).
	 */
	public static final int CAR_FACTOR = 100 / 70;
	/**
	 * Flexible activities, which need to be known for location choice and during generation.
	 * A day can not end on a flexible activity.
	 */
	public static final Set<String> FLEXIBLE_ACTS = Set.of(Activities.other.name());
	private static final Logger log = LogManager.getLogger(RunOpenGunmaCalibration.class);
	@CommandLine.Mixin
	private final SampleOptions sample = new SampleOptions(100, 25, 10, 1);
	@CommandLine.Option(names = "--mode", description = "Calibration mode that should be run.")
	private CalibrationMode mode;
	@CommandLine.Option(names = "--weight", description = "Strategy weight.", defaultValue = "1")
	private double weight;
	@CommandLine.Option(names = "--population", description = "Path to population.")
	private Path populationPath;
	@CommandLine.Option(names = "--facilities", description = "Path to facilities.")
	private Path facilitiesPath;
	@CommandLine.Option(names = "--all-car", description = "All plans will use car mode. Capacity is adjusted automatically by " + CAR_FACTOR, defaultValue = "false")
	private boolean allCar;

	@CommandLine.Option(names = "--scale-factor", description = "Scale factor for capacity to avoid congestions.", defaultValue = "1.5")
	private double scaleFactor;

	@CommandLine.Option(names = "--plan-index", description = "Only use one plan with specified index")
	private Integer planIndex;
	private final boolean simwrapperOn = false;
	boolean removePt = true;


	public RunOpenGunmaCalibration() {
		super(ConfigUtils.loadConfig("input/v" + OpenGunmaScenario.VERSION + "/gunma-v" + OpenGunmaScenario.VERSION + "-config.xml"));
	}

	/**
	 * Round to two digits.
	 */
	public static double roundNumber(double x) {
		return BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
	}

	/**
	 * Round coordinates to sufficient precision.
	 */
	public static Coord roundCoord(Coord coord) {
		return new Coord(roundNumber(coord.getX()), roundNumber(coord.getY()));
	}

	public static void main(String[] args) {
		MATSimApplication.run(RunOpenGunmaCalibration.class, args);
	}

	private static Coord getCoord(Scenario scenario, Activity act) {

		if (act.getCoord() != null)
			return act.getCoord();

		if (act.getFacilityId() != null)
			return Objects.requireNonNull(
				scenario.getActivityFacilities().getFacilities().get(act.getFacilityId()),
				() -> "Facility %s not found".formatted(act.getFacilityId())).getCoord();

		return scenario.getNetwork().getLinks().get(act.getLinkId()).getCoord();
	}

	@Override
	@SuppressWarnings("JavaNCSS")
	protected Config prepareConfig(Config config) {

		log.info("Running {} calibration {}", mode, populationPath);

		// Override Inputs in Config
		if (populationPath == null) {
			throw new IllegalArgumentException("Population path is required [--population]");
		} else {
			config.plans().setInputFile(populationPath.getFileName().toString());
		}

		if (facilitiesPath != null) {
			config.facilities().setInputFile(facilitiesPath.getFileName().toString());
		}

		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

		config.controller().setRunId(mode.toString());
		config.scoring().setWriteExperiencedPlans(true);

		config.controller().setLastIteration(10);
		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);


		// remove PT from config
//		if (removePt) {
//			removePtFromConfig(config);
//		}

		// Location choice does not work with the split types
		Activities.addScoringParams(config, true);


		// morning evening activities:
		SnzActivities.addMorningEveningScoringParams(config);


		SimWrapperConfigGroup sw = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		sw.setDefaultDashboards(SimWrapperConfigGroup.DefaultDashboardsMode.disabled);


		if (sample.isSet()) {
			double sampleSize = sample.getSample();
			double countScale = allCar ? CAR_FACTOR : 1;

			config.qsim().setFlowCapFactor(sampleSize * countScale);
			config.qsim().setStorageCapFactor(sampleSize * countScale);

			// Counts can be scaled with sample size
			config.counts().setCountsScaleFactor(sampleSize * countScale);
			config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));

			sw.setSampleSize(sampleSize * countScale);

			config.controller().setOutputDirectory(sample.adjustName(config.controller().getOutputDirectory()));

		}

		// Routes are not relaxed yet, and there should not be too heavy congestion
		// factors are increased to accommodate for more than usual traffic
		config.qsim().setFlowCapFactor(config.qsim().getFlowCapFactor() * scaleFactor);
		config.qsim().setStorageCapFactor(config.qsim().getStorageCapFactor() * scaleFactor);

		log.info("Running with flow and storage capacity: {} / {}", config.qsim().getFlowCapFactor(), config.qsim().getStorageCapFactor());

		if (allCar) {
			config.transit().setUseTransit(false);

			// Disable dashboards, for all car runs, these take too many resources
			sw.setDefaultDashboards(SimWrapperConfigGroup.DefaultDashboardsMode.disabled);

			// Only car and ride will be network modes, ride is not simulated on the network though
			config.routing().setNetworkModes(List.of(TransportMode.car, TransportMode.ride));
			config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams(TransportMode.bike)
				.setBeelineDistanceFactor(1.3)
				.setTeleportedModeSpeed(3.1388889)
			);
			config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams(TransportMode.truck)
				.setBeelineDistanceFactor(1.3)
				.setTeleportedModeSpeed(8.3)
			);
			config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams("freight")
				.setBeelineDistanceFactor(1.3)
				.setTeleportedModeSpeed(8.3)
			);

			config.qsim().setMainModes(List.of(TransportMode.car));
		} else {
//			ConfigUtils.addOrGetModule(config, BicycleConfigGroup.class);
		}

		// Required for all calibration strategies
		List<String> relevantSubpopulations = List.of("person", "commuter2gunma");
		for (String subpopulation : relevantSubpopulations) {
			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta)
					.setWeight(1.0)
					.setSubpopulation(subpopulation)
			);
		}

		if (mode == null)
			throw new IllegalArgumentException("Calibration mode [--mode} not set!");

		if (mode == CalibrationMode.cadyts) {

			// Re-route for all populations
			for (String subpopulation : relevantSubpopulations) {
				config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute)
					.setWeight(weight / 8)
					.setSubpopulation(subpopulation)
				);
			}
			double performingUtilsHr = config.scoring().getPerforming_utils_hr();

			// Agents should generally use the faster routes, this is without any mode choice
			config.scoring().getModes().values().forEach(m -> {
				// Only time goes into the score
				m.setMarginalUtilityOfTraveling(-performingUtilsHr);
				m.setConstant(0);
				m.setMarginalUtilityOfDistance(0);
				m.setDailyMonetaryConstant(0);
				m.setDailyUtilityConstant(0);
				m.setMonetaryDistanceRate(0);
			});

			config.controller().setOutputDirectory("./output/cadyts-" + scaleFactor);

			// Need to store more plans because of plan types
			config.replanning().setMaxAgentPlanMemorySize(7);

			config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.ignore);

			// Reduce number of threads, to reduce memory usage
			config.global().setNumberOfThreads(Math.min(12, config.global().getNumberOfThreads()));
			config.qsim().setNumberOfThreads(Math.min(12, config.qsim().getNumberOfThreads()));

		} else if (mode == CalibrationMode.routeChoice) {

			// Re-route for all populations
			// Weight is decreased, force innovation is used
			for (String subpopulation : relevantSubpopulations) {
				config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute)
					.setWeight(weight / 8)
					.setSubpopulation(subpopulation)
				);
			}

		} else if (mode == CalibrationMode.eval) {

			iterations = 0;
			config.controller().setLastIteration(0);

		} else
			throw new IllegalStateException("Mode not implemented:" + mode);

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {


		if (removePt) {
			removePtFromScenario(scenario);
		}

		// kai settings
//		org.matsim.contrib.vsp.scenario.Activities.changeWrapAroundActsIntoMorningAndEveningActs( scenario );


		if (mode == CalibrationMode.cadyts)
//			 each initial plan needs a separate type, so it won't be removed
			for (Person person : scenario.getPopulation().getPersons().values()) {
				for (int i = 0; i < person.getPlans().size(); i++) {
					person.getPlans().get(i).setType(String.valueOf(i));
				}
			}

		if (planIndex != null) {

			log.info("Using plan with index {}", planIndex);

			for (Person person : scenario.getPopulation().getPersons().values()) {
				SelectPlansFromIndex.selectPlanWithIndex(person, planIndex);
			}
		}

		if (allCar) {

			scenario.getPopulation().getFactory().getRouteFactories()
				.setRouteFactory(NetworkRoute.class, new MediumCompressedNetworkRouteFactory());

			log.info("Converting all agents to car plans.");

			MainModeIdentifier mmi = new DefaultAnalysisMainModeIdentifier();

			for (Person person : scenario.getPopulation().getPersons().values()) {
				for (Plan plan : person.getPlans()) {
					final List<PlanElement> planElements = plan.getPlanElements();
					final List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(plan);

					for (TripStructureUtils.Trip trip : trips) {

						final List<PlanElement> fullTrip =
							planElements.subList(
								planElements.indexOf(trip.getOriginActivity()) + 1,
								planElements.indexOf(trip.getDestinationActivity()));

						String mode = mmi.identifyMainMode(fullTrip);

						// Already car, nothing to do
						if (Objects.equals(mode, TransportMode.car) ||
							Objects.equals(mode, TransportMode.truck) ||
							Objects.equals(mode, "freight"))
							continue;

						double dist = CoordUtils.calcEuclideanDistance(getCoord(scenario, trip.getOriginActivity()), getCoord(scenario, trip.getDestinationActivity()));

						// short bike and walk trips are not changed
						if (dist <= 350 && (Objects.equals(mode, TransportMode.walk) || Objects.equals(mode, TransportMode.bike)))
							continue;

						// rest of the trips is set to walk if below threshold, car otherwise
						String desiredMode = dist <= 350 ? TransportMode.walk : TransportMode.car;

						if (!Objects.equals(mode, desiredMode)) {
							fullTrip.clear();
							Leg leg = PopulationUtils.createLeg(desiredMode);
							TripStructureUtils.setRoutingMode(leg, desiredMode);
							fullTrip.add(leg);
						}
					}
				}
			}
		}
	}

	@Override
	protected void prepareControler(Controler controler) {

		if (mode == CalibrationMode.cadyts) {

			controler.addOverridingModule(new CadytsCarModule());
			controler.setScoringFunctionFactory(new ScoringFunctionFactory() {
				@Inject
				ScoringParametersForPerson parameters;
				@Inject
				private CadytsContext cadytsContext;

				@Override
				public ScoringFunction createNewScoringFunction(Person person) {
					SumScoringFunction sumScoringFunction = new SumScoringFunction();

					Config config = controler.getConfig();

					// Not using the usual scoring, just cadyts + travel time
					// final ScoringParameters params = parameters.getScoringParameters(person);
					// sumScoringFunction.addScoringFunction(new CharyparNagelLegScoring(params, controler.getScenario().getNetwork()));

					final CadytsScoring<Link> scoringFunction = new CadytsScoring<>(person.getSelectedPlan(), config, cadytsContext);
					scoringFunction.setWeightOfCadytsCorrection(30 * config.scoring().getBrainExpBeta());
					sumScoringFunction.addScoringFunction(scoringFunction);

					return sumScoringFunction;
				}
			});

			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					binder().bind(new TypeLiteral<StrategyChooser<Plan, Person>>() {
					}).toInstance(new ForceInnovationStrategyChooser<>((int) Math.ceil(1.0 / weight), ForceInnovationStrategyChooser.Permute.yes));
				}
			});

		} else if (mode == CalibrationMode.routeChoice) {

			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					binder().bind(new TypeLiteral<StrategyChooser<Plan, Person>>() {
					}).toInstance(new ForceInnovationStrategyChooser<>((int) Math.ceil(1.0 / weight), ForceInnovationStrategyChooser.Permute.yes));
				}
			});
		}

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addControllerListenerBinding().to(ExtendExperiencedPlansListener.class);
			}
		});

		controler.addOverridingModule(new OpenGunmaScenario.TravelTimeBinding(allCar));
		if (simwrapperOn) {
			controler.addOverridingModule(new SimWrapperModule());
		}

//		if (ConfigUtils.hasModule(controler.getConfig(), AdvancedScoringConfigGroup.class)) {
//			controler.addOverridingModule(new AdvancedScoringModule());
//		}
	}

	@Override
	protected List<MATSimAppCommand> preparePostProcessing(Path outputFolder, String runId) {
		return List.of(
			new CleanPopulation().withArgs(
				"--plans", outputFolder.resolve(runId + ".output_plans.xml.gz").toString(),
				"--output", outputFolder.resolve(runId + ".output_selected_plans.xml.gz").toString(),
				"--remove-unselected-plans"
			)
		);
	}

	/**
	 * Different calibration stages.
	 */
	public enum CalibrationMode {
		eval,
		cadyts,
		routeChoice
	}

}
