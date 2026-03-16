package org.matsim.prepare.counts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.MATSimAppCommand;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;
import org.matsim.counts.Measurable;
import org.matsim.counts.MeasurementLocation;
import picocli.CommandLine;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import java.nio.file.Path;


/**
 * Prepare command to generate Counts from the BASt traffic count data.
 * If you want to use manual matched counts, you have to follow a name convention. Unfortunately one count station counts vehicles in
 * both directions For example:
 * The station id is 001. The count direction 1 (Column 'HiRi1') is E (east) and the count direction 2 (Column 'HiRi2') is W (west)
 * If you want to match the count values of the east-lane to matsim link 'my_link_1' add a entry in the .csv like this:
 * row1 : 001_1; my_link_1
 * <p>
 * If you want to ignore stations just paste the station id into the .csv. Both count directions of the station will be ignored.
 *
 * @author hzoerner
 */
@CommandLine.Command(name = "counts-from-jartic", description = "Creates MATSim from Jartic hourly counts.txt")
public class CreateCountsFromJarticData implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(CreateCountsFromJarticData.class);
//	@CommandLine.Option(names = "--road-types", description = "Define on which roads counts are created")
//	private final List<String> roadTypes = List.of("motorway", "primary", "trunk");
//	@CommandLine.Mixin
//	private final ShpOptions shp = new ShpOptions();
//	@CommandLine.Mixin
//	private final CountsOptions counts = new CountsOptions();


	//	@CommandLine.Mixin
//	private final CrsOptions crs = new CrsOptions("EPSG:2450");
//	@CommandLine.Option(names = "--network", description = "path to MATSim network", required = true)
//	private String network = "input/v1.2/";
	@CommandLine.Option(names = "--input", description = "Input LinkId to Counts Path")
	private Path input;
	@CommandLine.Option(names = "--output", description = "Output counts path")
	private Path output;

	public static void main(String[] args) {
		new CreateCountsFromJarticData().execute(args);
	}

	@Override
	public Integer call() {


		Table countsData = Table.read().csv(input.toFile());


		log.info("+++++++ Map aggregated traffic volumes to count stations +++++++");

		Counts<Link> mmCounts = new Counts<>();
		mmCounts.setYear(2015);
		mmCounts.setName("JARTIC Counts");
		mmCounts.setSource("https://www.jartic-open-traffic.org/");
		mmCounts.setDescription("Aggregated hourly traffic volumes");

		String prevLinkId = null;
		MeasurementLocation<Link> measureLocation = null;
		Measurable carVolume = null;
		for (Row row : countsData) {


			String linkId = row.getString("LinkId");

			if (!linkId.equals(prevLinkId)) {
				measureLocation = mmCounts.createAndAddMeasureLocation(Id.createLinkId(linkId), linkId);
				carVolume = measureLocation.createVolume(TransportMode.car, Measurable.HOURLY );
				prevLinkId = linkId;

			}

			String time = row.getString("time");
			int count = row.getInt("count");

			int timeHour = Integer.parseInt(time.replace("traffic_", "").replace("h", ""));
			carVolume.setAtHour(timeHour, count);


		}

		new CountsWriter(mmCounts).write(output.toString());


		return 0;
	}
}
