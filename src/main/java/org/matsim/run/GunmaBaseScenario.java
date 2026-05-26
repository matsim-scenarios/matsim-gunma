package org.matsim.run;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
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
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import picocli.CommandLine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base runtime scenario for Gunma.
 *
 * <p>This class contains configuration and runtime behavior that is shared by the
 * preparation/calibration and policy-specific scenario variants.
 */
@CommandLine.Command(
	header = ":: Gunma Scenario ::",
	version = GunmaDefaults.VERSION,
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)
public class GunmaBaseScenario extends MATSimApplication {

	/** Scenario version exposed for compatibility with existing helpers. */
	public static final String VERSION = GunmaDefaults.VERSION;
	/** Scenario CRS exposed for compatibility with existing helpers. */
	public static final String CRS = GunmaDefaults.CRS;

	protected final boolean removePt = true;

	@CommandLine.Mixin
	protected SampleOptions sample = new SampleOptions(100, 25, 10, 1);

	/** Creates the base scenario using the canonical Gunma config path. */
	public GunmaBaseScenario() {
		super(ConfigUtils.loadConfig(GunmaDefaults.configPath()));
	}

	/**
	 * Runs the base Gunma scenario from the command line.
	 *
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		MATSimApplication.run(GunmaBaseScenario.class, args);
	}

	/**
	 * Removes persons whose selected plan contains any public transport leg.
	 *
	 * @param scenario scenario to modify in place
	 */
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

	/**
	 * Applies sample-size dependent config changes to capacities, counts, file names, and output paths.
	 *
	 * @param config MATSim config to update
	 * @param sw simwrapper config module
	 * @param sample sample options selected for the run
	 */
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

	/**
	 * Applies config settings that are shared by all Gunma scenario variants.
	 *
	 * @param config MATSim config to update
	 */
	protected final void configureCommonConfig(Config config) {
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);

		SimWrapperConfigGroup sw = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		sw.setDefaultDashboards(SimWrapperConfigGroup.DefaultDashboardsMode.disabled);
	}

	/**
	 * Adds scoring parameters that are shared by all Gunma scenario variants.
	 *
	 * @param config MATSim config to update
	 */
	protected final void configureCommonActivityScoring(Config config) {
		Activities.addScoringParams(config, true);
		SnzActivities.addMorningEveningScoringParams(config);
	}

	/**
	 * Applies shared scenario mutations after the scenario is loaded.
	 *
	 * @param scenario loaded scenario to mutate
	 */
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

	/**
	 * Installs controller modules that are shared by all Gunma scenario variants.
	 *
	 * @param controler controller to configure
	 */
	protected final void prepareCommonControler(Controler controler) {
		controler.addOverridingModule(new TravelTimeBinding(false));
	}

	@Override
	protected Config prepareConfig(Config config) {

		configureCommonConfig(config);
		configureCommonActivityScoring(config);
		config.controller().setLastIteration(500);

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
					.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta)
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
		prepareCommonControler(controler);
	}

	/**
	 * Reduces effective road capacity to approximate freight traffic occupancy.
	 *
	 * @param scenario scenario whose network should be modified
	 */
	protected static void reduceNetworkCapacityForFreight(Scenario scenario) {
		// 87.5% of vehicles are "light vehicles", based on MLIT data
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

		/** Creates the default binding for the standard multimodal scenario. */
		public TravelTimeBinding() {
			this(false);
		}

		/**
		 * Creates the binding module.
		 *
		 * @param carOnly if {@code true}, omit non-car travel-time bindings
		 */
		public TravelTimeBinding(boolean carOnly) {
			this.carOnly = carOnly;
		}

		/** Installs the configured travel-time bindings. */
		@Override
		public void install() {
			if (carOnly) {
				return;
			}
		}
	}
}
