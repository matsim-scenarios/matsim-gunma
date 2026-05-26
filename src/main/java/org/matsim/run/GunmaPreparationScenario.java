package org.matsim.run;

import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.MATSimApplication;
import org.matsim.application.prepare.CreateLandUseShp;
import org.matsim.application.prepare.network.CleanNetwork;
import org.matsim.application.prepare.network.CreateNetworkFromSumo;
import org.matsim.application.prepare.population.CleanPopulation;
import org.matsim.application.prepare.population.DownSamplePopulation;
import org.matsim.application.prepare.population.MergePopulations;
import org.matsim.application.prepare.population.SplitActivityTypesDuration;
import org.matsim.application.prepare.pt.CreateTransitScheduleFromGtfs;
import org.matsim.contrib.cadyts.car.CadytsCarModule;
import org.matsim.contrib.cadyts.car.CadytsContext;
import org.matsim.contrib.cadyts.general.CadytsScoring;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.replanning.choosers.ForceInnovationStrategyChooser;
import org.matsim.core.replanning.choosers.StrategyChooser;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.dashboard.GunmaSimwrapperRunner;
import org.matsim.prepare.ExtendExperiencedPlansListener;
import org.matsim.prepare.counts.CreateCountsFromJarticData;
import org.matsim.prepare.counts.CreateCountsFromMlitData;
import org.matsim.prepare.facilities.CreateMATSimFacilitiesGunma;
import org.matsim.prepare.facilities.FacilitiesFilter;
import org.matsim.prepare.opt.RunCountOptimization;
import org.matsim.prepare.opt.SelectPlansFromIndex;
import org.matsim.prepare.population.*;
import org.matsim.prepare.vehicles.PrepareVehicleTypes;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.SimWrapperModule;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

//@CommandLine.Command(
//	header = ":: Gunma Calibration Scenario ::",
//	mixinStandardHelpOptions = true,
//	showDefaultValues = true
//)

/**
 * Scenario variant used during model preparation and calibration-oriented preprocessing runs.
 *
 * <p>This variant extends the shared Gunma base scenario with the preparation pipeline registration
 * and the lightweight calibration modes used while generating scenario inputs.
 */
@CommandLine.Command(header = ":: Gunma Preparation Scenario ::", mixinStandardHelpOptions = true)
@MATSimApplication.Prepare({
	GunmaSimwrapperRunner.class,
	CreateLandUseShp.class,
	CreateGunmaPopulation.class,
	CreateGunmaCommuterPopulation.class,
	MergePopulations.class,
	DownSamplePopulation.class,
	CreateNetworkFromSumo.class,
	CreateTransitScheduleFromGtfs.class,
	CleanNetwork.class,
	RunActivitySampling.class,
	InitLocationChoice.class,
	CreateMATSimFacilitiesGunma.class,
	FacilitiesFilter.class,
	LookupJisZone.class,
	CreateCountsFromMlitData.class,
	CreateCountsFromJarticData.class,
	RunCountOptimization.class,
	SelectPlansFromIndex.class,
	SplitActivityTypesDuration.class,
	AmendStartTimeCommuters.class,
	PrepareVehicleTypes.class,
	SplitMorningEveningActivities.class
})
public class GunmaPreparationScenario extends GunmaBaseScenario {

	private static final Logger log = LogManager.getLogger(GunmaPreparationScenario.class);

	@CommandLine.Option(names = "--mode", description = "Calibration mode that should be run.", required = true)
	private PreparationMode mode;

	@CommandLine.Option(names = "--weight", description = "Strategy weight.", defaultValue = "1")
	private double weight;

	@CommandLine.Option(names = "--population", description = "Path to population.", required = true)
	private Path populationPath;

	@CommandLine.Option(names = "--facilities", description = "Path to facilities.")
	private Path facilitiesPath;


	@CommandLine.Option(names = "--scale-factor", description = "Scale factor for capacity to avoid congestions.", defaultValue = "1.5")
	private double scaleFactor;


	private final boolean simwrapperOn = false;

	/**
	 * Runs the preparation scenario from the command line.
	 *
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		MATSimApplication.run(GunmaPreparationScenario.class, args);
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
			double countScale = 1;

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

		List<String> relevantSubpopulations = List.of("person", "commuter2gunma");
		for (String subpopulation : relevantSubpopulations) {
			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta)
					.setWeight(1.0)
					.setSubpopulation(subpopulation)
			);
		}

		if (mode == PreparationMode.cadyts) {
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
		} else if (mode == PreparationMode.routeChoice) {
			for (String subpopulation : relevantSubpopulations) {
				config.replanning().addStrategySettings(new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute)
					.setWeight(weight / 8)
					.setSubpopulation(subpopulation));
			}
		} else if (mode == PreparationMode.eval) {
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

		if (mode == PreparationMode.cadyts) {
			for (Person person : scenario.getPopulation().getPersons().values()) {
				for (int i = 0; i < person.getPlans().size(); i++) {
					person.getPlans().get(i).setType(String.valueOf(i));
				}
			}
		}

	}

	@Override
	protected void prepareControler(Controler controler) {
		if (mode == PreparationMode.cadyts) {
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
		} else if (mode == PreparationMode.routeChoice) {
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

		prepareCommonControler(controler);
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

	/**
	 * Preparation and calibration-oriented execution modes supported by this scenario variant.
	 */
	public enum PreparationMode {
		eval,
		cadyts,
		routeChoice
	}
}
