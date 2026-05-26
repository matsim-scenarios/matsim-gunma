package org.matsim.run;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.common.zones.systems.grid.square.SquareGridZoneSystemParams;
import org.matsim.contrib.drt.optimizer.insertion.extensive.ExtensiveInsertionSearchParams;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtConfigs;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
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
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.prepare.population.Attributes;
import picocli.CommandLine;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

/**
 * Scenario variant for policy and intervention experiments.
 *
 * <p>This class extends the shared Gunma base scenario with policy switches such as vehicle
 * availability changes and DRT or taxi-only experiments.
 */
@CommandLine.Command(
	header = ":: Gunma Policy Scenario ::",
	version = GunmaDefaults.VERSION,
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)
public class GunmaPolicyScenario extends GunmaBaseScenario {

	@CommandLine.Option(names = "--policy-case", description = "Which policy case to use", required = true)
	private PolicyCase policyCase;

	private Set<Id<Link>> filteredLinkIds;

	/**
	 * Runs the policy scenario from the command line.
	 *
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		MATSimApplication.run(GunmaPolicyScenario.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {
		super.prepareConfig(config);

		if (policyCase == PolicyCase.drtOnly || policyCase == PolicyCase.drtOnlyAsTaxi) {
			config.plans().setInputFile("gunma-v1.6-100pct-plans-filtered85.xml.gz");
			config.facilities().setInputFile("d1_facilities-all.xml.gz");
			config.controller().setLastIteration(1);
			config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

			int numTaxis = 750;
			String taxiFileName = "gunma-v" + GunmaDefaults.VERSION + "-taxis-" + numTaxis + ".xml";
			URL taxiFileUrl = ConfigGroup.getInputFileURL(config.getContext(), taxiFileName);
			URL networkUrl = ConfigGroup.getInputFileURL(config.getContext(), config.network().getInputFile());

			Network filteredNetwork = filterNetworkToShape(
				networkUrl.getPath(),
				"../shared-svn/projects/matsim-gunma/data/raw/01_shapefiles/gunma_2450/gunma_2450.shp"
			);
			generateTaxiFleet(numTaxis, taxiFileUrl.getPath(), filteredNetwork);
			filteredLinkIds = filteredNetwork.getLinks().keySet();

			DvrpConfigGroup dvrpConfig = ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);
			if (policyCase == PolicyCase.drtOnlyAsTaxi) {
				config.controller().setOutputDirectory("output-" + numTaxis);
				ConfigUtils.addOrGetModule(config, MultiModeTaxiConfigGroup.class);
				TaxiConfigGroup taxiConfig = TaxiConfigGroup.getSingleModeTaxiConfig(config);
				taxiConfig.taxisFile = taxiFileName;
				taxiConfig.dropoffDuration = 60;
				taxiConfig.pickupDuration = 120;
				taxiConfig.detailedStats = true;
				taxiConfig.timeProfiles = true;
				config.scoring().addModeParams(new ScoringConfigGroup.ModeParams(TransportMode.taxi));
			} else {
				config.controller().setOutputDirectory("output-drt-" + numTaxis);
				dvrpConfig.setNetworkModes(Set.of(TransportMode.drt));

				SquareGridZoneSystemParams squareGridZoneSystemParams = new SquareGridZoneSystemParams();
				squareGridZoneSystemParams.setCellSize(200);
				dvrpConfig.getTravelTimeMatrixParams().addParameterSet(squareGridZoneSystemParams);

				MultiModeDrtConfigGroup multiModeDrtCfg = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
				DrtConfigGroup drtConfig = new DrtConfigGroup();
				drtConfig.setMode(TransportMode.drt);
				drtConfig.setStopDuration(60.);
				drtConfig.addOrGetDrtOptimizationConstraintsParams().addOrGetDefaultDrtOptimizationConstraintsSet().setMaxWaitTime(900);
				drtConfig.addOrGetDrtOptimizationConstraintsParams().addOrGetDefaultDrtOptimizationConstraintsSet().setMaxTravelTimeAlpha(1.3);
				drtConfig.addOrGetDrtOptimizationConstraintsParams().addOrGetDefaultDrtOptimizationConstraintsSet().setMaxTravelTimeBeta(10. * 60.);
				drtConfig.addOrGetDrtOptimizationConstraintsParams().addOrGetDefaultDrtOptimizationConstraintsSet().setRejectRequestIfMaxWaitOrTravelTimeViolated(false);
				drtConfig.setVehiclesFile(taxiFileName);
				drtConfig.setChangeStartLinkToLastLinkInSchedule(false);
				drtConfig.setDrtInsertionSearchParams(new ExtensiveInsertionSearchParams());
				drtConfig.setOperationalScheme(DrtConfigGroup.OperationalScheme.door2door);
				drtConfig.setDrtServiceAreaShapeFile("/Users/jakob/git/shared-svn/projects/matsim-gunma/data/raw/01_shapefiles/gunma_2450/gunma_2450.shp");
				multiModeDrtCfg.addDrtConfigGroup(drtConfig);

				for (DrtConfigGroup drtCfg : multiModeDrtCfg.getModalElements()) {
					DrtConfigs.adjustDrtConfig(drtCfg, config.scoring(), config.routing());
				}
				config.scoring().addModeParams(new ScoringConfigGroup.ModeParams(TransportMode.drt));
			}

			config.qsim().setSimStarttimeInterpretation(QSimConfigGroup.StarttimeInterpretation.onlyUseStarttime);
		}

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		super.prepareScenario(scenario);

		if (policyCase == PolicyCase.noCarAvailOver75policy || policyCase == PolicyCase.noCarAvailOver75base) {
			removeCarAvailabilityByAge(scenario, 75);
			return;
		}

		if (policyCase != PolicyCase.drtOnlyAsTaxi && policyCase != PolicyCase.drtOnly) {
			return;
		}

		String targetMode = policyCase == PolicyCase.drtOnlyAsTaxi ? TransportMode.taxi : TransportMode.drt;
		if (policyCase == PolicyCase.drtOnly) {
			for (Id<Link> filteredLinkId : filteredLinkIds) {
				Set<String> allowedModes = new HashSet<>(scenario.getNetwork().getLinks().get(filteredLinkId).getAllowedModes());
				allowedModes.add(TransportMode.drt);
				scenario.getNetwork().getLinks().get(filteredLinkId).setAllowedModes(allowedModes);
			}
			NetworkUtils.cleanNetwork(scenario.getNetwork(), Set.of(TransportMode.drt));
		}

		ShpOptions shp = new ShpOptions(
			"/Users/jakob/git/shared-svn/projects/matsim-gunma/data/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp",
			null,
			null
		);
		ShpOptions.Index jisIndex = shp.createIndex(shp.getShapeCrs(), Attributes.JIS_ZONE_FIELD);

		for (Person person : scenario.getPopulation().getPersons().values()) {
			Plan plan = person.getSelectedPlan();
			final List<PlanElement> planElements = plan.getPlanElements();
			plan.setScore(null);

			for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
				Optional<Leg> cleanLeg = trip.getLegsOnly().stream()
					.filter(l -> Objects.equals(TransportMode.car, l.getMode()))
					.findFirst();

				final List<PlanElement> fullTrip = planElements.subList(
					planElements.indexOf(trip.getOriginActivity()) + 1,
					planElements.indexOf(trip.getDestinationActivity())
				);
				fullTrip.clear();

				String originZone = findZone(trip.getOriginActivity(), person, scenario, jisIndex);
				String destinationZone = findZone(trip.getDestinationActivity(), person, scenario, jisIndex);

				if (!originZone.startsWith("10") || !destinationZone.startsWith("10") || cleanLeg.isEmpty()) {
					continue;
				}

				Leg leg = PopulationUtils.createLeg(targetMode);
				TripStructureUtils.setRoutingMode(leg, targetMode);
				fullTrip.add(leg);
			}
		}

		Set<Id<Person>> personsToRemove = new HashSet<>();
		for (Person person : scenario.getPopulation().getPersons().values()) {
			boolean personHasTaxiLeg = TripStructureUtils.getLegs(person.getSelectedPlan()).stream()
				.map(Leg::getRoutingMode)
				.collect(Collectors.toSet())
				.contains(targetMode);

			if (!personHasTaxiLeg) {
				personsToRemove.add(person.getId());
			}
		}

		for (Id<Person> personId : personsToRemove) {
			scenario.getPopulation().getPersons().remove(personId);
		}

		scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class, new DrtRouteFactory());
	}

	@Override
	protected void prepareControler(Controler controler) {
		super.prepareControler(controler);

		if (policyCase == PolicyCase.drtOnlyAsTaxi) {
			controler.addOverridingModule(new DvrpModule());
			controler.addOverridingModule(new MultiModeTaxiModule());
			controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(MultiModeTaxiConfigGroup.get(controler.getConfig())));
			controler.configureQSimComponents(DvrpQSimComponents.activateModes(TransportMode.taxi));
		} else if (policyCase == PolicyCase.drtOnly) {
			controler.addOverridingModule(new DvrpModule());
			controler.addOverridingModule(new MultiModeDrtModule());
			controler.configureQSimComponents(DvrpQSimComponents.activateModes(TransportMode.drt));
		}
	}

	/**
	 * Replace trips with certain mode with empty trip of different mode.
	 */
	private void replaceModeLegsWithOtherMode(Plan plan, Set<String> modes, String replacementMode) {

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

	private void removeCarAvailabilityByAge(Scenario scenario, int age) {
		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (person.getId().toString().startsWith("gunma_") && PersonUtils.getAge(person) >= age) {
				if (policyCase == PolicyCase.noCarAvailOver75policy) {
					PersonUtils.setCarAvail(person, "never");
				}

				PersonUtils.removeUnselectedPlans(person);
				replaceModeLegsWithOtherMode(person.getSelectedPlan(), Set.of(TransportMode.car), TransportMode.walk);
			}
		}
	}

	private static String findZone(Activity act, Person person, Scenario scenario, ShpOptions.Index jisIndex) {
		if (act.getType().startsWith("home")) {
			return (String) person.getAttributes().getAttribute("zone");
		}

		if (scenario.getActivityFacilities().getFacilities().containsKey(act.getFacilityId())) {
			return (String) scenario.getActivityFacilities().getFacilities().get(act.getFacilityId()).getAttributes().getAttribute("zone");
		}

		return jisIndex.query(scenario.getNetwork().getLinks().get(act.getLinkId()).getCoord());
	}

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

	private void generateTaxiFleet(int numberOfVehicles, String taxisFile, Network network) {
		double operationStartTime = 0.;
		double operationEndTime = 3 * 24 * 3600.;
		int seats = 4;
		List<DvrpVehicleSpecification> vehicles = new ArrayList<>();
		SplittableRandom random = new SplittableRandom(MatsimRandom.getLocalInstance().nextLong());

		List<Id<Link>> allLinks = new ArrayList<>(network.getLinks().keySet());
		for (int i = 0; i < numberOfVehicles; i++) {
			Link startLink;
			do {
				Id<Link> linkId = allLinks.get(random.nextInt(allLinks.size()));
				startLink = network.getLinks().get(linkId);
			} while (!startLink.getAllowedModes().contains(TransportMode.car));

			vehicles.add(ImmutableDvrpVehicleSpecification.newBuilder()
				.id(Id.create("taxi" + i, DvrpVehicle.class))
				.startLinkId(startLink.getId())
				.capacity(seats)
				.serviceBeginTime(operationStartTime)
				.serviceEndTime(operationEndTime)
				.build());
		}

		new FleetWriter(vehicles.stream(), new IntegerLoadType("passengers")).write(taxisFile);
	}

	/**
	 * Policy cases supported by this scenario variant.
	 */
	public enum PolicyCase {
		noCarAvailOver75base,
		noCarAvailOver75policy,
		drtOnly,
		drtOnlyAsTaxi
	}
}
