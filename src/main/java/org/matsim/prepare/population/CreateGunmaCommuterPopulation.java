package org.matsim.prepare.population;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.MultiPolygon;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ProjectionUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.GeotoolsTransformation;
import org.matsim.run.OpenGunmaScenario;
import picocli.CommandLine;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.nio.file.Path;
import java.util.*;

@CommandLine.Command(
	name = "gunma-commuter",
	description = "Create synthetic commuter population for gunma."
)


public class CreateGunmaCommuterPopulation implements MATSimAppCommand {
	private static final Logger log = LogManager.getLogger(CreateGunmaCommuterPopulation.class);

	@CommandLine.Option(names = "--input", description = "Path to input csv data", required = true)
	private Path input;

	@CommandLine.Mixin
	private ShpOptions shp = new ShpOptions();
	@CommandLine.Option(names = "--output", description = "Path to output population", required = true)
	private Path output;
	@CommandLine.Option(names = "--sample", description = "Sample size to generate", defaultValue = "1.0")
	private double sample;

	private final CoordinateTransformation ct = new GeotoolsTransformation("EPSG:2450", "EPSG:2450");
	private final Map<String, MultiPolygon> zones = new HashMap<>();
	private final FacilityOptions facilities = null;




	public static void main(String[] args) {
		new CreateGunmaCommuterPopulation().execute(args);
	}

	/**
	 * Generate a new unique id within population.
	 */
	public static Id<Person> generateId(Population population, String prefix, SplittableRandom rnd) {

		Id<Person> id;
		byte[] bytes = new byte[4];
		do {
			rnd.nextBytes(bytes);
			id = Id.createPersonId(prefix + "_" + HexFormat.of().formatHex(bytes));

		} while (population.getPersons().containsKey(id));

		return id;
	}

	/**
	 * Samples a home coordinates from geometry and landuse.
	 */
	public static Coord sampleHomeCoordinate(MultiPolygon geometry, String crs, FacilityOptions facilities, SplittableRandom rnd, int tries) {

		Envelope bbox = geometry.getEnvelopeInternal();

		int i = 0;
		Coord coord;
		do {

			if (facilities != null) {
				coord = facilities.select(crs, () -> new Coord(
					bbox.getMinX() + (bbox.getMaxX() - bbox.getMinX()) * rnd.nextDouble(),
					bbox.getMinY() + (bbox.getMaxY() - bbox.getMinY()) * rnd.nextDouble()
				));
			} else {
				coord = new Coord(
					bbox.getMinX() + (bbox.getMaxX() - bbox.getMinX()) * rnd.nextDouble(),
					bbox.getMinY() + (bbox.getMaxY() - bbox.getMinY()) * rnd.nextDouble()
				);
			}

			i++;

		} while (!geometry.contains(MGC.coord2Point(coord)) && i < tries);

		if (i == 1500)
			log.warn("Invalid coordinate generated");
		return coord;

		// the current rounding schema rounds too much for the crs we are using. If we go back to rounding, we should keep more signif digits.
//		return RunOpenGunmaCalibration.roundCoord(coord);
	}

	@Override
	@SuppressWarnings("IllegalCatch")
	public Integer call() throws Exception {

		if (!shp.isDefined()) {
			log.error("Shape file with JIS zones is required.");
			return 2;
		}

		for (SimpleFeature ft : shp.readFeatures()) {
			zones.put((String) ft.getAttribute(Attributes.JIS_ZONE_FIELD), (MultiPolygon) ft.getDefaultGeometry());
		}


		log.info("Found {} zones", zones.size());

		// create random number generator
		SplittableRandom rnd = new SplittableRandom(0);

		// create empty population
		Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());


		// read census commuter csv

		CsvReadOptions options = CsvReadOptions.builder(input.toFile())
			.columnTypesPartial(java.util.Map.of(
				"from", ColumnType.STRING,
				"to", ColumnType.STRING
			))
			.build();

		Table t = Table.read().csv(options);
		for (Row row : t) {
			String from = row.getString("from");
			String to = row.getString("to");
			int commuters = row.getInt("n_scaled");

			// For this population, we don't want anyone living in Gunma
			if (from.startsWith("10")) {
				continue;
			}

			// We are only interested in commuters TO Gunma (10xxx), not those commuting from Gunma to other prefectures, or between other prefectures.
			if (!to.startsWith("10")) {

				continue;
			}
// Solved in preprocessing
//			// We also want to exclude the rows with aggregated data, which are marked with "000" at the end of the zone code. These rows are not actual zones and would distort our population synthesis.
//			if (from.endsWith("00") || to.endsWith("00")) {
//
//				continue;
//			}


			for (int i = 0; i < commuters * sample; i++) {

				Person person = population.getFactory().createPerson(generateId(population, "commuter", rnd));
				PopulationUtils.putSubpopulation(person, "commuter2gunma");


				MultiPolygon geom = zones.get(from);

				if (geom == null) {

					throw new RuntimeException("No geometry found for zone" + from);

//					continue;
				}
				Coord coord = ct.transform(sampleHomeCoordinate(geom, OpenGunmaScenario.CRS, facilities, rnd, 1500));
				person.getAttributes().putAttribute(Attributes.HOME_X, coord.getX());
				person.getAttributes().putAttribute(Attributes.HOME_Y, coord.getY());
				person.getAttributes().putAttribute(Attributes.COMMUTE_TO, to);
				person.getAttributes().putAttribute(Attributes.ZONE, from);

				population.addPerson(person);
			}
		}


		// Write Population
		log.info("Generated {} persons", population.getPersons().size());
		PopulationUtils.sortPersons(population);

		ProjectionUtils.putCRS(population, OpenGunmaScenario.CRS);
		PopulationUtils.writePopulation(population, output.toString());

		return 0;
	}

}
