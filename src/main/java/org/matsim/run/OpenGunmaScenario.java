package org.matsim.run;


import org.matsim.application.MATSimApplication;
import org.matsim.application.options.SampleOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;

import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(header = ":: Open Gunma Scenario ::", version = OpenGunmaScenario.VERSION, mixinStandardHelpOptions = true, showDefaultValues = true)
public class OpenGunmaScenario extends MATSimApplication {

	public static final String VERSION = "1.4";
	public static final String CRS = "EPSG:2450";

	@CommandLine.Mixin
	private final SampleOptions sample = new SampleOptions(10, 25, 3, 1);

	@CommandLine.Option(names = "--plan-selector",
		description = "Plan selector to use.",
		defaultValue = DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta)
	private String planSelector;

	public OpenGunmaScenario() {
		super(ConfigUtils.loadConfig(String.format("input/v%s/berlin-v%s.config.xml", VERSION, VERSION)));

	}

	public static void main(String[] args) {
		MATSimApplication.run(OpenGunmaScenario.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {

//		SimWrapperConfigGroup sw = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
//
//		if (sample.isSet()) {
//			double sampleSize = sample.getSample();
//
//			config.qsim().setFlowCapFactor(sampleSize);
//			config.qsim().setStorageCapFactor(sampleSize);
//
//			// Counts can be scaled with sample size
//			config.counts().setCountsScaleFactor(sampleSize);
//			sw.sampleSize = sampleSize;
//
//			config.controller().setRunId(sample.adjustName(config.controller().getRunId()));
//			config.controller().setOutputDirectory(sample.adjustName(config.controller().getOutputDirectory()));
//			config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));
//		}
//
		config.qsim().setUsingTravelTimeCheckInTeleportation(true);

		// overwrite ride scoring params with values derived from car
//		RideScoringParamsFromCarParams.setRideScoringParamsBasedOnCarParams(config.scoring(), 1.0);
//		Activities.addScoringParams(config, true);
//
//		// Required for all calibration strategies
		for (String subpopulation : List.of("person", "commuter2gunma")) {
			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(planSelector)
					.setWeight(1.0)
					.setSubpopulation(subpopulation)
			);

			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute)
					.setWeight(0.15)
					.setSubpopulation(subpopulation)
			);

			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator)
					.setWeight(0.15)
					.setSubpopulation(subpopulation)
			);
			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice)
					.setWeight(0.15)
					.setSubpopulation(subpopulation)
			);
		}




//
//		// Need to switch to warning for best score
//		if (planSelector.equals(DefaultPlanStrategiesModule.DefaultSelector.BestScore)) {
//			config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);
//		}
//
//		// Bicycle config must be present
//		ConfigUtils.addOrGetModule(config, BicycleConfigGroup.class);
//
		return config;
	}

//	@Override
//	protected void prepareScenario(Scenario scenario) {
//
//	}
//
//	@Override
//	protected void prepareControler(Controler controler) {
//
//		controler.addOverridingModule(new SimWrapperModule());
//
//		controler.addOverridingModule(new TravelTimeBinding());
//
//		controler.addOverridingModule(new QsimTimingModule());
//
//		// AdvancedScoring is specific to matsim-berlin!
//		if (ConfigUtils.hasModule(controler.getConfig(), AdvancedScoringConfigGroup.class)) {
//			controler.addOverridingModule(new AdvancedScoringModule());
//			controler.getConfig().scoring().setExplainScores(true);
//		} else {
//			// if the above config group is not present we still need income dependent scoring
//			// this implementation also allows for person specific asc
//			controler.addOverridingModule(new AbstractModule() {
//				@Override
//				public void install() {
//					bind(ScoringParametersForPerson.class).to(IncomeDependentUtilityOfMoneyPersonScoringParameters.class).asEagerSingleton();
//				}
//			});
//		}
//		controler.addOverridingModule(new PersonMoneyEventsAnalysisModule());
//	}

	/**
	 * Add travel time bindings for ride and freight modes, which are not actually network modes.
	 */
	public static final class TravelTimeBinding extends AbstractModule {

		private final boolean carOnly;

		public TravelTimeBinding() {
			this.carOnly = false;
		}

		public TravelTimeBinding(boolean carOnly) {
			this.carOnly = carOnly;
		}

		@Override
		public void install() {
//			addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
//			addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());
//
//			if (!carOnly) {
//				addTravelTimeBinding("freight").to(Key.get(TravelTime.class, Names.named(TransportMode.truck)));
//				addTravelDisutilityFactoryBinding("freight").to(Key.get(TravelDisutilityFactory.class, Names.named(TransportMode.truck)));
//
//
//				bind(BicycleLinkSpeedCalculator.class).to(BicycleLinkSpeedCalculatorDefaultImpl.class);
//
//				// Bike should use free speed travel time
//				addTravelTimeBinding(TransportMode.bike).to(BicycleTravelTime.class);
//				addTravelDisutilityFactoryBinding(TransportMode.bike).to(OnlyTimeDependentTravelDisutilityFactory.class);
//			}
		}
	}

}
