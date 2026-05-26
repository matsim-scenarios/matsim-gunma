package org.matsim.run;

import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.MATSimApplication;
import org.matsim.application.prepare.population.CleanPopulation;
import org.matsim.contrib.cadyts.car.CadytsCarModule;
import org.matsim.contrib.cadyts.car.CadytsContext;
import org.matsim.contrib.cadyts.general.CadytsScoring;
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
import org.matsim.prepare.ExtendExperiencedPlansListener;
import org.matsim.prepare.OpenGunmaPreparationUtils;
import org.matsim.prepare.opt.SelectPlansFromIndex;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.SimWrapperModule;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@CommandLine.Command(
	header = ":: Open Gunma Calibration Scenario ::",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)
public class OpenGunmaCalibrationScenario extends OpenGunmaScenario {

	private static final Logger log = LogManager.getLogger(OpenGunmaCalibrationScenario.class);

	@CommandLine.Option(names = "--mode", description = "Calibration mode that should be run.", required = true)
	private CalibrationMode mode;

	@CommandLine.Option(names = "--weight", description = "Strategy weight.", defaultValue = "1")
	private double weight;

	@CommandLine.Option(names = "--population", description = "Path to population.", required = true)
	private Path populationPath;

	@CommandLine.Option(names = "--facilities", description = "Path to facilities.")
	private Path facilitiesPath;

	@CommandLine.Option(
		names = "--all-car",
		description = "All plans will use car mode. Capacity is adjusted automatically by " + OpenGunmaPreparationUtils.CAR_FACTOR,
		defaultValue = "false"
	)
	private boolean allCar;

	@CommandLine.Option(names = "--scale-factor", description = "Scale factor for capacity to avoid congestions.", defaultValue = "1.5")
	private double scaleFactor;

	@CommandLine.Option(names = "--plan-index", description = "Only use one plan with specified index")
	private Integer planIndex;

	private final boolean simwrapperOn = false;

	public OpenGunmaCalibrationScenario() {
		super();
	}

	public static void main(String[] args) {
		MATSimApplication.run(OpenGunmaCalibrationScenario.class, args);
	}

	private static Coord getCoord(Scenario scenario, Activity act) {
		if (act.getCoord() != null) {
			return act.getCoord();
		}

		if (act.getFacilityId() != null) {
			return Objects.requireNonNull(
				scenario.getActivityFacilities().getFacilities().get(act.getFacilityId()),
				() -> "Facility %s not found".formatted(act.getFacilityId())
			).getCoord();
		}

		return scenario.getNetwork().getLinks().get(act.getLinkId()).getCoord();
	}

	@Override
	@SuppressWarnings("JavaNCSS")
	protected Config prepareConfig(Config config) {
		log.info("Running {} calibration {}", mode, populationPath);

		config.plans().setInputFile(populationPath.getFileName().toString());
		if (facilitiesPath != null) {
			config.facilities().setInputFile(facilitiesPath.getFileName().toString());
		}

		configureCommonConfig(config);
		configureCommonActivityScoring(config);

		config.controller().setRunId(mode.toString());
		config.scoring().setWriteExperiencedPlans(true);
		config.controller().setLastIteration(10);

		SimWrapperConfigGroup sw = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		if (sample.isSet()) {
			double sampleSize = sample.getSample();
			double countScale = allCar ? OpenGunmaPreparationUtils.CAR_FACTOR : 1;

			config.qsim().setFlowCapFactor(sampleSize * countScale);
			config.qsim().setStorageCapFactor(sampleSize * countScale);
			config.counts().setCountsScaleFactor(sampleSize * countScale);
			config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));
			sw.setSampleSize(sampleSize * countScale);
			config.controller().setOutputDirectory(sample.adjustName(config.controller().getOutputDirectory()));
		}

		config.qsim().setFlowCapFactor(config.qsim().getFlowCapFactor() * scaleFactor);
		config.qsim().setStorageCapFactor(config.qsim().getStorageCapFactor() * scaleFactor);

		log.info("Running with flow and storage capacity: {} / {}", config.qsim().getFlowCapFactor(), config.qsim().getStorageCapFactor());

		if (allCar) {
			config.transit().setUseTransit(false);
			sw.setDefaultDashboards(SimWrapperConfigGroup.DefaultDashboardsMode.disabled);
			config.routing().setNetworkModes(List.of(TransportMode.car, TransportMode.ride));
			config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams(TransportMode.bike)
				.setBeelineDistanceFactor(1.3)
				.setTeleportedModeSpeed(3.1388889));
			config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams(TransportMode.truck)
				.setBeelineDistanceFactor(1.3)
				.setTeleportedModeSpeed(8.3));
			config.routing().addTeleportedModeParams(new RoutingConfigGroup.TeleportedModeParams("freight")
				.setBeelineDistanceFactor(1.3)
				.setTeleportedModeSpeed(8.3));
			config.qsim().setMainModes(List.of(TransportMode.car));
		}

		List<String> relevantSubpopulations = List.of("person", "commuter2gunma");
		for (String subpopulation : relevantSubpopulations) {
			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta)
					.setWeight(1.0)
					.setSubpopulation(subpopulation)
			);
		}

		if (mode == CalibrationMode.cadyts) {
			for (String subpopulation : relevantSubpopulations) {
				config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute)
					.setWeight(weight / 8)
					.setSubpopulation(subpopulation));
			}

			double performingUtilsHr = config.scoring().getPerforming_utils_hr();
			config.scoring().getModes().values().forEach(m -> {
				m.setMarginalUtilityOfTraveling(-performingUtilsHr);
				m.setConstant(0);
				m.setMarginalUtilityOfDistance(0);
				m.setDailyMonetaryConstant(0);
				m.setDailyUtilityConstant(0);
				m.setMonetaryDistanceRate(0);
			});

			config.controller().setOutputDirectory("./output/cadyts-" + scaleFactor);
			config.replanning().setMaxAgentPlanMemorySize(7);
			config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.ignore);
			config.global().setNumberOfThreads(Math.min(12, config.global().getNumberOfThreads()));
			config.qsim().setNumberOfThreads(Math.min(12, config.qsim().getNumberOfThreads()));
		} else if (mode == CalibrationMode.routeChoice) {
			for (String subpopulation : relevantSubpopulations) {
				config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute)
					.setWeight(weight / 8)
					.setSubpopulation(subpopulation));
			}
		} else if (mode == CalibrationMode.eval) {
			iterations = 0;
			config.controller().setLastIteration(0);
		} else {
			throw new IllegalStateException("Mode not implemented:" + mode);
		}

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		if (removePt) {
			removePtFromScenario(scenario);
		}

		if (mode == CalibrationMode.cadyts) {
			for (Person person : scenario.getPopulation().getPersons().values()) {
				for (int i = 0; i < person.getPlans().size(); i++) {
					person.getPlans().get(i).setType(String.valueOf(i));
				}
			}
		}

		if (planIndex != null) {
			log.info("Using plan with index {}", planIndex);
			for (Person person : scenario.getPopulation().getPersons().values()) {
				SelectPlansFromIndex.selectPlanWithIndex(person, planIndex);
			}
		}

		if (!allCar) {
			return;
		}

		scenario.getPopulation().getFactory().getRouteFactories()
			.setRouteFactory(NetworkRoute.class, new MediumCompressedNetworkRouteFactory());

		log.info("Converting all agents to car plans.");
		MainModeIdentifier mmi = new DefaultAnalysisMainModeIdentifier();

		for (Person person : scenario.getPopulation().getPersons().values()) {
			for (Plan plan : person.getPlans()) {
				final List<PlanElement> planElements = plan.getPlanElements();
				final List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(plan);

				for (TripStructureUtils.Trip trip : trips) {
					final List<PlanElement> fullTrip = planElements.subList(
						planElements.indexOf(trip.getOriginActivity()) + 1,
						planElements.indexOf(trip.getDestinationActivity())
					);

					String modeName = mmi.identifyMainMode(fullTrip);
					if (Objects.equals(modeName, TransportMode.car) ||
						Objects.equals(modeName, TransportMode.truck) ||
						Objects.equals(modeName, "freight")) {
						continue;
					}

					double dist = CoordUtils.calcEuclideanDistance(
						getCoord(scenario, trip.getOriginActivity()),
						getCoord(scenario, trip.getDestinationActivity())
					);

					if (dist <= 350 && (Objects.equals(modeName, TransportMode.walk) || Objects.equals(modeName, TransportMode.bike))) {
						continue;
					}

					String desiredMode = dist <= 350 ? TransportMode.walk : TransportMode.car;
					if (!Objects.equals(modeName, desiredMode)) {
						fullTrip.clear();
						Leg leg = PopulationUtils.createLeg(desiredMode);
						TripStructureUtils.setRoutingMode(leg, desiredMode);
						fullTrip.add(leg);
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

		prepareCommonControler(controler, allCar);
		if (simwrapperOn) {
			controler.addOverridingModule(new SimWrapperModule());
		}
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

	public enum CalibrationMode {
		eval,
		cadyts,
		routeChoice
	}
}
