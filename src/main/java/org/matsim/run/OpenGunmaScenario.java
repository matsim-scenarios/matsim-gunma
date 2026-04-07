package org.matsim.run;


import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.SampleOptions;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetWriter;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.contrib.dvrp.load.IntegerLoadType;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.taxi.run.MultiModeTaxiConfigGroup;
import org.matsim.contrib.taxi.run.MultiModeTaxiModule;
import org.matsim.contrib.taxi.run.TaxiConfigGroup;
import org.matsim.contrib.vsp.scenario.SnzActivities;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;

import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.prepare.population.Attributes;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import picocli.CommandLine;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;


@CommandLine.Command(header = ":: Open Gunma Scenario ::", version = OpenGunmaScenario.VERSION, mixinStandardHelpOptions = true, showDefaultValues = true)
public class OpenGunmaScenario extends MATSimApplication {



	public static final String VERSION = "1.6";
	public static final String CRS = "EPSG:2450";

	private final boolean removePt = true;

	@CommandLine.Mixin
	private SampleOptions sample = new SampleOptions(100, 25, 10, 1);

	@CommandLine.Option(names = "--plan-selector", description = "Plan selector to use.")
	private String planSelector = DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta;


	@CommandLine.Option(names = "--policy-case", description = "Which policy case to use")
	private PolicyCase policyCase = PolicyCase.base;

	public OpenGunmaScenario() {
		super(ConfigUtils.loadConfig(String.format("input/v%s/gunma-v%s-config-taxi.xml", VERSION, VERSION)));

	}

	public static void main(String[] args) {
		MATSimApplication.run(OpenGunmaScenario.class, args);
	}

	/**
	 * replace trips with certain mode with empty trip of different mode.
	 */
	public static void replaceModeLegsWithOtherMode(Plan plan, Set<String> modes, String replacementMode) {

		final List<PlanElement> planElements = plan.getPlanElements();
		plan.setScore(null);

		// Remove all pt trips


		for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {

			// Check if any of the modes is in the trip
			Optional<Leg> cleanLeg = trip.
				getLegsOnly().
				stream().
				filter(l -> modes.contains(l.getMode())).
				findFirst();

			if (cleanLeg.isEmpty())
				continue;

			// Replaces all trip elements and inserts single leg
			final List<PlanElement> fullTrip =
				planElements.subList(
					planElements.indexOf(trip.getOriginActivity()) + 1,
					planElements.indexOf(trip.getDestinationActivity()));

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

		if (policyCase == PolicyCase.drt || policyCase == PolicyCase.drtOnly) {

			config.plans().setInputFile("gunma-v1.6-100pct-plans-filtered85.xml.gz");

			config.facilities().setInputFile("d1_facilities-all.xml.gz");
			// general
			config.controller().setLastIteration(1);
			config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);


			// create taxis file
			int numTaxis = 1501;

			config.controller().setOutputDirectory("output-" + numTaxis);
			String taxiFileName = "gunma-v" + VERSION + "-taxis-" + numTaxis + ".xml";
			URL taxiFileUrl = ConfigGroup.getInputFileURL(config.getContext(), taxiFileName);
			URL networkUrl = ConfigGroup.getInputFileURL(config.getContext(), config.network().getInputFile());

			Network filteredNetwork = filterNetworkToShape(networkUrl.getPath(), "../shared-svn/projects/matsim-gunma/data/raw/01_shapefiles/gunma_2450/gunma_2450.shp");
			generateTaxiFleet(numTaxis, taxiFileUrl.getPath(), filteredNetwork);

			//taxi config
			ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);
			ConfigUtils.addOrGetModule(config, MultiModeTaxiConfigGroup.class);
			TaxiConfigGroup taxiConfig = TaxiConfigGroup.getSingleModeTaxiConfig(config);
			taxiConfig.taxisFile = taxiFileName;
			taxiConfig.dropoffDuration = 60;
			taxiConfig.pickupDuration = 120;

			taxiConfig.detailedStats = true;
			taxiConfig.timeProfiles = true;



			// Scoring
			ScoringConfigGroup.ModeParams taxiParams = new ScoringConfigGroup.ModeParams(TransportMode.taxi);
			config.scoring().addModeParams(taxiParams);

			//QSIM
			config.qsim().setSimStarttimeInterpretation(QSimConfigGroup.StarttimeInterpretation.onlyUseStarttime);

		}

//		new ConfigWriter(config).write("sdkjhfadslkjasdhf.xml");
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
						replaceModeLegsWithOtherMode(person.getSelectedPlan(), Set.of(TransportMode.car), TransportMode.walk);

					}
				}
			}
		} else if (policyCase == PolicyCase.drt) {
			Set<Id<Person>> personsToRemove = new HashSet<>();
			for (Person person : scenario.getPopulation().getPersons().values()) {
				if (person.getAttributes().getAttribute("age") == null ||
					(int) person.getAttributes().getAttribute("age") < 75){

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

//			Plan plan = scenario.getPopulation().getPersons().get(Id.createPersonId("gunma_f03408691")).getSelectedPlan();
//			replaceModeLegsWithOtherMode(plan, Set.of(TransportMode.car), TransportMode.taxi);

			for (Person person : scenario.getPopulation().getPersons().values()) {
				replaceModeLegsWithOtherMode(person.getSelectedPlan(), Set.of(TransportMode.car), TransportMode.taxi);
			}


			// DRT route factory (see DrtControlerCreator)
			scenario.getPopulation()
				.getFactory()
				.getRouteFactories()
				.setRouteFactory(DrtRoute.class, new DrtRouteFactory());

		} else if (policyCase == PolicyCase.drtOnly) {

			ShpOptions shp = new ShpOptions("/Users/jakob/git/shared-svn/projects/matsim-gunma/data/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp", null, null);
			ShpOptions.Index jisIndex = shp.createIndex(
				shp.getShapeCrs(),
				Attributes.JIS_ZONE_FIELD
			);

			int replacementCnt = 0;

			for(Person person : scenario.getPopulation().getPersons().values()) {
				Plan plan = person.getSelectedPlan();

				final List<PlanElement> planElements = plan.getPlanElements();
				plan.setScore(null);

				for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {


					String originZone = findZone(trip.getOriginActivity(), person, scenario, jisIndex);
//					if (scenario.getActivityFacilities().getFacilities().containsKey(trip.getOriginActivity().getFacilityId())) {
//						originZone = (String) scenario.getActivityFacilities().getFacilities().get(trip.getOriginActivity().getFacilityId()).getAttributes().getAttribute("zone");
//					}

					String destinationZone = findZone(trip.getOriginActivity(), person, scenario, jisIndex);

//					if (scenario.getActivityFacilities().getFacilities().containsKey(trip.getOriginActivity().getFacilityId())) {
//						destinationZone = (String) scenario.getActivityFacilities().getFacilities().get(trip.getOriginActivity().getFacilityId()).getAttributes().getAttribute("zone");
//					}

					// origin and destination should be in gunma for taxi trip to be activated


					// Check if any of the modes is in the trip
					Optional<Leg> cleanLeg = trip.
						getLegsOnly().
						stream().
						filter(l -> Objects.equals(TransportMode.car, l.getMode())).
						findFirst();



					// Replaces all trip elements and inserts single leg
					final List<PlanElement> fullTrip =
						planElements.subList(
							planElements.indexOf(trip.getOriginActivity()) + 1,
							planElements.indexOf(trip.getDestinationActivity()));

					fullTrip.clear();

					if (!originZone.startsWith("10") || !destinationZone.startsWith("10") || cleanLeg.isEmpty()){
						continue;
					}


					Leg leg = PopulationUtils.createLeg(TransportMode.taxi);
					TripStructureUtils.setRoutingMode(leg, TransportMode.taxi);
					fullTrip.add(leg);


					replacementCnt++;
				}

			}

			// remove all agents without a taxi leg
			Set<Id<Person>> personsToRemove = new HashSet<>();
			for (Person person : scenario.getPopulation().getPersons().values()) {

				boolean personHasTaxiLeg = TripStructureUtils.
					getLegs(person.getSelectedPlan()).
					stream().
					map(Leg::getRoutingMode).
					collect(Collectors.toSet()).
					contains(TransportMode.taxi);

				if (!personHasTaxiLeg) {
					personsToRemove.add(person.getId());
				}
			}

			for (Id<Person> personId : personsToRemove) {
				scenario.getPopulation().getPersons().remove(personId);
			}



			// DRT route factory (see DrtControlerCreator)
			scenario.getPopulation()
				.getFactory()
				.getRouteFactories()
				.setRouteFactory(DrtRoute.class, new DrtRouteFactory());

		}


		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (person.getId().toString().startsWith("commuter")) {
				PersonUtils.setCarAvail(person, "always");
			}
		}


	}


	String findZone(Activity act, Person person, Scenario scenario, ShpOptions.Index jisIndex) {

		String zone = null;
		if (act.getType().startsWith("home")) {
			zone = (String) person.getAttributes().getAttribute("zone");
		} else {
			act.getFacilityId();
			if (scenario.getActivityFacilities().getFacilities().containsKey(act.getFacilityId())) {
				zone = (String) scenario.getActivityFacilities().getFacilities().get(act.getFacilityId()).getAttributes().getAttribute("zone");
			} else {
				zone = jisIndex.query(scenario.getNetwork().getLinks().get(act.getLinkId()).getCoord());
			}
		}

		return zone;

	}


	@Override
	protected void prepareControler(Controler controler) {

		if (policyCase == PolicyCase.drt || policyCase == PolicyCase.drtOnly) {

			controler.addOverridingModule(new DvrpModule());
//			controler.addOverridingModule(new OneTaxiModule(taxiFileUrl, PassengerEngineQSimModule.PassengerEngineType.DEFAULT));
			controler.addOverridingModule(new MultiModeTaxiModule());
			controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(MultiModeTaxiConfigGroup.get(controler.getConfig())));

			controler.configureQSimComponents(DvrpQSimComponents.activateModes(TransportMode.taxi));
		}

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

	// adapted from src/main/java/org/matsim/application/prepare/network/zone_preparation/PrepareMaxTravelTimeBasedZonalSystem.java
	private Network filterNetworkToShape(String networkFile, String shpFilename) {


		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkFile);


		ShpOptions shpOptions = new ShpOptions(shpFilename, null, null);
		Geometry areaToKeep = shpOptions.getGeometry();

		List<Link> linksToRemove = new ArrayList<>();
		for (Link link : network.getLinks().values()) {
			Point from = MGC.coord2Point(link.getFromNode().getCoord());
			Point to = MGC.coord2Point(link.getToNode().getCoord());
			if (!from.within(areaToKeep) || !to.within(areaToKeep)) {
				linksToRemove.add(link);
			}
		}
		for (Link link : linksToRemove) {
			network.removeLink(link.getId());
		}

		return network;
	}


	// adapted from src/main/java/org/matsim/contrib/drt/extension/benchmark/scenario/FleetGenerator.java
	private void generateTaxiFleet(int numberofVehicles, String taxisFile, Network network) {
		double operationStartTime = 0.;
		double operationEndTime = 3*24*3600.;
		int seats = 4;
		List<DvrpVehicleSpecification> vehicles = new ArrayList<>();
		Random random = MatsimRandom.getLocalInstance();

		List<Id<Link>> allLinks = new ArrayList<>(network.getLinks().keySet());
		for (int i = 0; i< numberofVehicles; i++){
			Link startLink;
			do {
				Id<Link> linkId = allLinks.get(random.nextInt(allLinks.size()));
				startLink = network.getLinks().get(linkId);
			}
			while (!startLink.getAllowedModes().contains(TransportMode.car));
			//for multi-modal networks: Only links where cars can ride should be used.
			vehicles.add(ImmutableDvrpVehicleSpecification.newBuilder().id(Id.create("taxi" + i, DvrpVehicle.class))
				.startLinkId(startLink.getId())
				.capacity(seats)
				.serviceBeginTime(operationStartTime)
				.serviceEndTime(operationEndTime)
				.build());

		}
		new FleetWriter(vehicles.stream(), new IntegerLoadType("passengers")).write(taxisFile);
	}


	/**
	 * Defines which policy case (or base case) is being simulated.
	 */
	public enum PolicyCase {
		base,
		noCarAvailOver75base,
		noCarAvailOver75policy,
		drt,
		drtOnly
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
