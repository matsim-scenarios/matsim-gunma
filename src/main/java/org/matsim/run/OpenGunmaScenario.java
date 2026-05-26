package org.matsim.run;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.SampleOptions;
import org.matsim.contrib.vsp.scenario.SnzActivities;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import picocli.CommandLine;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@CommandLine.Command(
	header = ":: Open Gunma Scenario ::",
	version = OpenGunmaDefaults.VERSION,
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)
public class OpenGunmaScenario extends MATSimApplication {

	public static final String VERSION = OpenGunmaDefaults.VERSION;
	public static final String CRS = OpenGunmaDefaults.CRS;

	protected final boolean removePt = true;

	@CommandLine.Mixin
	protected SampleOptions sample = new SampleOptions(100, 25, 10, 1);

	@CommandLine.Option(names = "--plan-selector", description = "Plan selector to use.")
	protected String planSelector = DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta;

	public OpenGunmaScenario() {
		super(ConfigUtils.loadConfig(OpenGunmaDefaults.configPath()));
	}

	public static void main(String[] args) {
		MATSimApplication.run(OpenGunmaScenario.class, args);
	}

	/**
	 * Replace trips with certain mode with empty trip of different mode.
	 */
	public static void replaceModeLegsWithOtherMode(Plan plan, Set<String> modes, String replacementMode) {

		final List<PlanElement> planElements = plan.getPlanElements();
		plan.setScore(null);

		for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {

			Optional<Leg> cleanLeg = trip.getLegsOnly().stream().filter(l -> modes.contains(l.getMode())).findFirst();

			if (cleanLeg.isEmpty()) {
				continue;
			}

			final List<PlanElement> fullTrip = planElements.subList(
				planElements.indexOf(trip.getOriginActivity()) + 1,
				planElements.indexOf(trip.getDestinationActivity())
			);

			fullTrip.clear();

			Leg leg = PopulationUtils.createLeg(replacementMode);
			TripStructureUtils.setRoutingMode(leg, replacementMode);
			fullTrip.add(leg);
		}
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

	static void modifyForSample(Config config, SimWrapperConfigGroup sw, SampleOptions sample) {
		double sampleSize = sample.getSample();

		config.qsim().setFlowCapFactor(sampleSize);
		config.qsim().setStorageCapFactor(sampleSize);
		config.counts().setCountsScaleFactor(sampleSize);
		sw.setSampleSize(sampleSize);

		config.controller().setRunId(sample.adjustName(config.controller().getRunId()));
		config.controller().setOutputDirectory(sample.adjustName(config.controller().getOutputDirectory()));
		config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));
		config.facilities().setInputFile(sample.adjustName(config.facilities().getInputFile()));
	}

	protected final void configureCommonConfig(Config config) {
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);

		SimWrapperConfigGroup sw = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		sw.setDefaultDashboards(SimWrapperConfigGroup.DefaultDashboardsMode.disabled);
	}

	protected final void configureCommonActivityScoring(Config config) {
		Activities.addScoringParams(config, true);
		SnzActivities.addMorningEveningScoringParams(config);
	}

	protected final void prepareCommonScenario(Scenario scenario) {
		if (removePt) {
			removePtFromScenario(scenario);
		}

		reduceNetworkCapacityForFreight(scenario);

		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (person.getId().toString().startsWith("commuter")) {
				PersonUtils.setCarAvail(person, "always");
			}
		}
	}

	protected final void prepareCommonControler(Controler controler, boolean carOnly) {
		controler.addOverridingModule(new TravelTimeBinding(carOnly));
	}

	@Override
	protected Config prepareConfig(Config config) {

		config.controller().setLastIteration(500);
		configureCommonConfig(config);
		configureCommonActivityScoring(config);

		SimWrapperConfigGroup sw = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		if (sample.isSet()) {
			modifyForSample(config, sw, sample);
		}

		config.scoring().getModes().get(TransportMode.walk).setConstant(0.0);
		config.scoring().getModes().get(TransportMode.car).setConstant(-0.847163);
		config.scoring().getModes().get(TransportMode.ride).setConstant(-1.652781);
		config.scoring().getModes().get(TransportMode.bike).setConstant(-1.432778);

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
		prepareCommonScenario(scenario);
	}

	@Override
	protected void prepareControler(Controler controler) {
		prepareCommonControler(controler, false);
	}

	protected static void reduceNetworkCapacityForFreight(Scenario scenario) {
		double freightPct = 1.0 - 0.8757594;
		for (Link link : scenario.getNetwork().getLinks().values()) {
			if (link.getAllowedModes().contains(TransportMode.car)) {
				if (link.getAttributes().getAttribute("type").toString().contains("highway.residential") ||
					link.getAttributes().getAttribute("type").toString().contains("highway.living_street")) {

				} else if (link.getAttributes().getAttribute("type").toString().contains("primary") ||
					link.getAttributes().getAttribute("type").toString().contains("trunk") ||
					link.getAttributes().getAttribute("type").toString().contains("motorway")) {
					link.setCapacity(link.getCapacity() - link.getCapacity() * freightPct);
				} else {
					link.setCapacity(link.getCapacity() - link.getCapacity() * (freightPct - 0.03));
				}
			}
		}
	}

	/**
	 * Add travel time bindings for ride and freight modes, which are not actually network modes.
	 */
	public static final class TravelTimeBinding extends AbstractModule {

		private final boolean carOnly;

		public TravelTimeBinding() {
			this(false);
		}

		public TravelTimeBinding(boolean carOnly) {
			this.carOnly = carOnly;
		}

		@Override
		public void install() {
			if (carOnly) {
				return;
			}
		}
	}
}
