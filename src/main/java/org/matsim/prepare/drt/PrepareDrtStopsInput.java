//package org.matsim.prepare.drt;
//
//import org.geotools.api.referencing.FactoryException;
//import org.geotools.api.referencing.operation.TransformException;
//import org.matsim.api.core.v01.Coord;
//import org.matsim.api.core.v01.Id;
//import org.matsim.api.core.v01.Scenario;
//import org.matsim.api.core.v01.TransportMode;
//import org.matsim.api.core.v01.network.Link;
//import org.matsim.api.core.v01.network.Network;
//import org.matsim.api.core.v01.network.Node;
//import org.matsim.core.config.Config;
//import org.matsim.core.config.ConfigUtils;
//import org.matsim.core.network.NetworkUtils;
//import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
//import org.matsim.core.network.io.MatsimNetworkReader;
//import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
//import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
//import org.matsim.pt.transitSchedule.api.TransitStopFacility;
//import org.matsim.run.OpenGunmaScenario;
//import tech.tablesaw.api.Table;
//import tech.tablesaw.io.csv.CsvReadOptions;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Random;
//import java.util.Set;
//
//import static org.matsim.core.scenario.ScenarioUtils.createScenario;
//
///**
// *
// */
//public class PrepareDrtStopsInput {
//
//	public static void main(String[] args) throws IOException, FactoryException, TransformException {
//		//define files
//		String networkFile = "input/v" + OpenGunmaScenario.VERSION + "/gunma-v" + OpenGunmaScenario.VERSION + "-network.xml.gz";
//		File stopXmlOutput = new File("input/v" + OpenGunmaScenario.VERSION + "/gunma-v" + OpenGunmaScenario.VERSION + "-drt_stops.xml");
//
//
//		// create scenario
//		Config config = ConfigUtils.createConfig();
//		Scenario scenario = createScenario(config);
//
//		// read network
//		TransitScheduleFactory tsf = scenario.getTransitSchedule().getFactory();
//
//		MatsimNetworkReader networkReader = new MatsimNetworkReader(scenario.getNetwork());
//		networkReader.readFile(networkFile);
//
//		Network subNetwork = NetworkUtils.createNetwork(config.network());
//		new TransportModeNetworkFilter(scenario.getNetwork()).filter(subNetwork, Set.of(TransportMode.car));
//
////		Random random = new Random(4711);
//		List<Node> nodes = new ArrayList<>();
//
//		nodes.add(scenario.getNetwork().getNodes().get(Id.createNodeId("2044229439")));
//		nodes.add(scenario.getNetwork().getNodes().get(Id.createNodeId("3107430927")));
//
//
//		int i = 0;
//		for (Node node : nodes) {
//
//			Coord coord = node.getCoord();
//			TransitStopFacility transitStopFacility = tsf.createTransitStopFacility(Id.create(i, TransitStopFacility.class), coord, false);
//			Link nearestLink = NetworkUtils.getNearestLink(subNetwork, coord);
//			transitStopFacility.setLinkId(nearestLink.getId());
//			scenario.getTransitSchedule().addStopFacility(transitStopFacility);
//			i++;
//		}
//
//		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(stopXmlOutput.toString());
//
//
//	}
//}
