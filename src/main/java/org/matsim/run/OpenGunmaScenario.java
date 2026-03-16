package org.matsim.run;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.SampleOptions;
import org.matsim.contrib.vsp.scenario.SnzActivities;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;

import org.matsim.core.router.TripStructureUtils;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import picocli.CommandLine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@CommandLine.Command(header = ":: Open Gunma Scenario ::", version = OpenGunmaScenario.VERSION, mixinStandardHelpOptions = true, showDefaultValues = true)
public class OpenGunmaScenario extends MATSimApplication {



	public static final String VERSION = "1.6";
	public static final String CRS = "EPSG:2450";

	private final boolean removePt = true;

	@CommandLine.Mixin
	private SampleOptions sample = new SampleOptions(25, 10, 1);

	@CommandLine.Option(names = "--plan-selector", description = "Plan selector to use.")
	private String planSelector = DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta;


	@CommandLine.Option(names = "--policy-case", description = "Which policy case to use")
	private PolicyCase policyCase = PolicyCase.base;

	public OpenGunmaScenario() {
		super(ConfigUtils.loadConfig(String.format("input/v%s/gunma-v%s-config.xml", VERSION, VERSION)));

	}

	public static void main(String[] args) {
		MATSimApplication.run(OpenGunmaScenario.class, args);
	}


	public static void removePtFromScenario(Scenario scenario) {
		Set<Id<Person>> ptPersons = new HashSet<>();
		outer:
		for (Person person : scenario.getPopulation().getPersons().values()) {
			for (Leg leg : TripStructureUtils.getLegs(person.getSelectedPlan())) {

				if (leg.getMode().equals(TransportMode.pt)) {
					ptPersons.add(person.getId());
					continue outer;
				}
			}
		}

		for (Id<Person> personId : ptPersons) {

			scenario.getPopulation().getPersons().remove(personId);
		}
	}

//	public static void removePtFromConfig(Config config) {
//		config.routing().removeTeleportedModeParams(TransportMode.pt);
//		config.changeMode().setModes(List.of(TransportMode.car).toArray(new String[0]));
//
//		config.scoring().removeParameterSet(config.scoring().getActivityParams("pt interaction"));
//		config.subtourModeChoice().setModes(List.of(TransportMode.car, TransportMode.walk, TransportMode.bike, TransportMode.ride).toArray(new String[0]));
//	}

	static void modifyForSample(Config config, SimWrapperConfigGroup sw, SampleOptions sample) {
		double sampleSize = sample.getSample();

		config.qsim().setFlowCapFactor(sampleSize);
		config.qsim().setStorageCapFactor(sampleSize);

		// Counts can be scaled with sample size
		config.counts().setCountsScaleFactor(sampleSize);
		sw.setSampleSize(sampleSize);

		config.controller().setRunId(sample.adjustName(config.controller().getRunId()));
		config.controller().setOutputDirectory(sample.adjustName(config.controller().getOutputDirectory()));
		config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));
		config.facilities().setInputFile(sample.adjustName(config.facilities().getInputFile()));
	}


	@Override
	protected Config prepareConfig(Config config) {
		// general

		config.controller().setLastIteration(500);
		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);

		// adjust  files and for sample
		SimWrapperConfigGroup sw = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);

		sw.setDefaultDashboards(SimWrapperConfigGroup.DefaultDashboardsMode.disabled);

		if (sample.isSet()) {
			modifyForSample(config, sw, sample);
		}

		// remove PT from config
//		if (removePt){
//			removePtFromConfig(config);
//		}

		// ############
		// scoring

		// change modeParms based on calibration
		config.scoring().getModes().get(TransportMode.walk).setConstant(0.0);
		config.scoring().getModes().get(TransportMode.car).setConstant(-0.847163);
		config.scoring().getModes().get(TransportMode.ride).setConstant(-1.652781);
		config.scoring().getModes().get(TransportMode.bike).setConstant(-1.432778);

		Activities.addScoringParams(config, true);

		// morning evening activities:
		SnzActivities.addMorningEveningScoringParams(config);

		// ############
		// replanning
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

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		if (removePt) {
			removePtFromScenario(scenario);
		}


		if (policyCase == PolicyCase.noCarAvailOver75policy || policyCase == PolicyCase.noCarAvailOver75base) {
			for (Person person : scenario.getPopulation().getPersons().values()) {
				if (person.getId().toString().startsWith("gunma_")) {
					if (PersonUtils.getAge(person) >= 75) {

						if (policyCase == PolicyCase.noCarAvailOver75policy) {
							PersonUtils.setCarAvail(person, "never");
						}

						PersonUtils.removeUnselectedPlans(person);

						Set<Leg> legsToRemove = TripStructureUtils.getLegs(person.getSelectedPlan()).stream().filter(leg -> leg.getMode().equals(TransportMode.car)).collect(Collectors.toSet());
						Set<Activity> activitiesToRemove = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.StagesAsNormalActivities).stream().filter(activity -> activity.getType().equals("car interaction")).collect(Collectors.toSet());
						person.getSelectedPlan().getPlanElements().removeAll(legsToRemove);
						person.getSelectedPlan().getPlanElements().removeAll(activitiesToRemove);

					}
				}
			}
		}

		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (person.getId().toString().startsWith("commuter")) {
				PersonUtils.setCarAvail(person, "always");
			}
		}


	}



	@Override
	protected void prepareControler(Controler controler) {

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
	}


	/**
	 * Defines which policy case (or base case) is being simulated.
	 */
	public enum PolicyCase {
		base,
		noCarAvailOver75base,
		noCarAvailOver75policy
	}


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
