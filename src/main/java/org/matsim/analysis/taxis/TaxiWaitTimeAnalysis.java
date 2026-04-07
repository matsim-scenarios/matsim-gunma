package org.matsim.analysis.taxis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.dvrp.passenger.PassengerPickedUpEvent;
import org.matsim.contrib.dvrp.passenger.PassengerPickedUpEventHandler;
import org.matsim.contrib.taxi.util.TaxiEventsReaders;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.prepare.population.Attributes;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

import java.io.IOException;
import java.util.*;

/**
 * Taxi wait time per JIS zone.
 */
public final class TaxiWaitTimeAnalysis {

	private TaxiWaitTimeAnalysis(){}



	public static void main(String[] args) throws IOException {

		String outputFile = "../shared-svn/projects/matsim-gunma/data/processed/drt/wait_times-2500-gunma.csv";


//		String inputDir = "output-10000/";
//		String inputDir = "output-5000/";
		String inputDir = "output-2500/";
//		String inputDir = "output-2000/";
//		String inputDir = "output-1500/";
//		String inputDir = "output-1000/";

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		new MatsimFacilitiesReader(scenario).readFile(inputDir + "gunma.output_facilities.xml.gz");

		new PopulationReader(scenario).readFile(inputDir + "gunma.output_plans.xml.gz");

		new MatsimNetworkReader(scenario.getNetwork()).readFile(inputDir + "gunma.output_network.xml.gz");

		// JIS ZONES

		ShpOptions shp = new ShpOptions("/Users/jakob/git/shared-svn/projects/matsim-gunma/data/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp", null, null);
		ShpOptions.Index jisIndex = shp.createIndex(
			shp.getShapeCrs(),
			Attributes.JIS_ZONE_FIELD
		);

		// EVENTS

		String inputFile = inputDir + "gunma.output_events.xml.gz";

		//create an event object

		EventsManager events = EventsUtils.createEventsManager();

		//create the handler and add it
		TaxiEventHandler handler1 = new TaxiEventHandler(scenario, jisIndex);
		events.addHandler(handler1);

		//create the reader and read the file
		events.initProcessing();

		MatsimEventsReader reader = TaxiEventsReaders.createEventsReader(events);
//		MatsimEventsReader reader = new MatsimEventsReader(events);
		reader.readFile(inputFile);
		events.finishProcessing();

		// Write output

		StringColumn zoneCol = StringColumn.create("zone");
		DoubleColumn waitingCol = DoubleColumn.create("waiting_time");

		for (String zone : handler1.zoneToWaitingTimes.keySet()) {
			for (Double waitingTime : handler1.zoneToWaitingTimes.get(zone)) {
				zoneCol.append(zone);
				waitingCol.append(waitingTime);
			}
		}

		Table table = Table.create("zone_stats")
			.addColumns(zoneCol, waitingCol);

		table.write().csv(outputFile);



//		Set<String> zones = new HashSet<>();
//		zones.addAll(handler1.zoneToTotalWaitingTime.keySet());
//		zones.addAll(handler1.zoneToPickupCnt.keySet());
//
//		StringColumn zoneCol = StringColumn.create("zone");
//		DoubleColumn waitingCol = DoubleColumn.create("TotalWaitingTime");
//		IntColumn pickupCol = IntColumn.create("ZoneToPickupCnt");
//
//		for (String z : zones) {
//			zoneCol.append(z);
//			waitingCol.append(handler1.zoneToTotalWaitingTime.getOrDefault(z, 0.0));
//			pickupCol.append(handler1.zoneToPickupCnt.getOrDefault(z, 0));
//		}
//
//		Table table = Table.create("zone_stats")
//			.addColumns(zoneCol, waitingCol, pickupCol);
//
//		table.write().csv("../shared-svn/projects/matsim-gunma/data/processed/01_shapefiles/drt/wait_times_by_zone.csv");



	}

	/**
	 * gathers taxi wait times per JIS zone. average wait time per zone is then calculated in r.
	 */
	public static class TaxiEventHandler implements ActivityEndEventHandler, PassengerPickedUpEventHandler {


		private final Scenario scenario;
		private final ShpOptions.Index jisIndex;
		private Map<Id<Person>, String> personToZone;
		Map<String, Double> zoneToTotalWaitingTime;
		Map<String, Integer> zoneToPickupCnt;
		Map<String, List<Double>> zoneToWaitingTimes;


		private Map<Id<Person>, Double> personToRequestTime;

		public TaxiEventHandler(Scenario scenario, ShpOptions.Index jisIndex) {
			this.scenario = scenario;
			this.jisIndex = jisIndex;

			personToZone = new HashMap<>();
			zoneToTotalWaitingTime = new HashMap<>();
			zoneToPickupCnt = new HashMap<>();
			personToRequestTime = new HashMap<>();
			zoneToWaitingTimes = new HashMap<>();
		}

		@Override
		public void handleEvent(ActivityEndEvent event) {

			String zone;
			if (!event.getPersonId().toString().startsWith("gunma")) {
				return;
			}

			if (event.getActType().endsWith("interaction")) {
				return;
			}

			if (event.getActType().startsWith("home")) {
				zone = (String) scenario.getPopulation().getPersons().get(event.getPersonId()).getAttributes().getAttribute("zone");
			} else {
//				System.out.println(event);
				if (scenario.getActivityFacilities().getFacilities().containsKey(event.getFacilityId())) {
					zone = (String) scenario.getActivityFacilities().getFacilities().get(event.getFacilityId()).getAttributes().getAttribute("zone");
				} else {
					zone = jisIndex.query(scenario.getNetwork().getLinks().get(event.getLinkId()).getCoord());
				}
			}

			personToZone.put(event.getPersonId(), zone);
			personToRequestTime.put(event.getPersonId(), event.getTime());


		}

		@Override
		public void handleEvent(PassengerPickedUpEvent event) {

			String zone = personToZone.get(event.getPersonId());
			Double totalWaitingTime = event.getTime() - personToRequestTime.get(event.getPersonId());
			personToRequestTime.remove(event.getPersonId());
			zoneToTotalWaitingTime.merge(zone, totalWaitingTime, Double::sum);
			zoneToPickupCnt.merge(zone, 1, Integer::sum);
			zoneToWaitingTimes.computeIfAbsent(zone, k -> new ArrayList<>()).add(totalWaitingTime);

		}

	}

}
