package org.matsim.prepare.population;

import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.MultiPolygon;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ProjectionUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.GeotoolsTransformation;
import org.matsim.run.OpenGunmaScenario;
import picocli.CommandLine;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.stream.IntStream;

@CommandLine.Command(
	name = "gunma-population",
	description = "Create synthetic population for gunma."
)

// todo
// good assumption of max age?

public class CreateGunmaPopulation implements MATSimAppCommand {

	private static final Object2IntMap<AgeGroup> ageGroupCntGlobal = new Object2IntAVLTreeMap<>();
//	private static final NumberFormat FMT = NumberFormat.getInstance(Locale.GERMAN);

	private static final Logger log = LogManager.getLogger(CreateGunmaPopulation.class);
	private static String columnAllAges;
	private static String column95up;
	private static String column85up;
	private static String column75up;
	private static String column65up;
	private static String column20up;
	private static String column18up;
	private static String column15up;
	private static String column0to14;

	@CommandLine.Option(names = "--sex", description = "Sex: ${COMPLETION-CANDIDATES}")
	private static Sex sex;
	@CommandLine.Option(names = "--input", description = "Path to input csv data", required = true)
	private Path input;
//	@CommandLine.Mixin
//	private FacilityOptions facilities = new FacilityOptions();
	@CommandLine.Mixin
	private ShpOptions shp = new ShpOptions();
	@CommandLine.Option(names = "--output", description = "Path to output population", required = true)
	private Path output;
//	@CommandLine.Option(names = "--year", description = "Year to use statistics from", defaultValue = "2019")
//	private int year;
	@CommandLine.Option(names = "--sample", description = "Sample size to generate", defaultValue = "0.25")
	private double sample;

	private final CoordinateTransformation ct = new GeotoolsTransformation("EPSG:2450", "EPSG:2450");
	private Map<String, MultiPolygon> zones;
	private SplittableRandom rnd;
	private Population population;
	private final FacilityOptions facilities = null;






	public static void main(String[] args) {
		new CreateGunmaPopulation().execute(args);
	}

	/**
	 * Generate a new unique id within population.
	 */
	public static Id<Person> generateId(Population population, String prefix, SplittableRandom rnd) {

		Id<Person> id;
		byte[] bytes = new byte[4];
		do {
			rnd.nextBytes(bytes);
			id = Id.createPersonId(prefix + "_" + sex.abbr + HexFormat.of().formatHex(bytes));

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


		chooseColumnCodesBasedOnSex();

		// Read shapefile of zones
		zones = new HashMap<>();

		if (!shp.isDefined()) {
			log.error("Shape file with mesh grid is required.");
			return 2;
		}

		String key = "KEY_CODE";
		for (SimpleFeature ft : shp.readFeatures()) {
			zones.put((String) ft.getAttribute(key), (MultiPolygon) ft.getDefaultGeometry());
		}

		log.info("Found {} zones", zones.size());

		// create random number generator
		rnd = new SplittableRandom(0);

		// create empty population
		population = PopulationUtils.createPopulation(ConfigUtils.createConfig());


		// read census csv
		CSVFormat.Builder format = CSVFormat.DEFAULT.builder().setDelimiter(',').setHeader().setSkipHeaderRecord(true);


		// creates age distribution per zone
		Map<String, EnumeratedAttributeDistribution<AgeGroup>> zoneToAgeDistribution = new LinkedHashMap<>();

		for (AgeGroup age : AgeGroup.values()) {
			ageGroupCntGlobal.put(age, 0);
		}

		try (CSVParser reader = new CSVParser(Files.newBufferedReader(input, Charset.forName("Shift_JIS")), format.build())) {
			List<CSVRecord> records = reader.getRecords();
			// skip second header row (explanation of columns in japanese)
			records.remove(0);

			for (CSVRecord row : ProgressBar.wrap(records, "Preprocessing zones For Age Distributions)")) {
				try {

					// we only want the rows w/ data in them, not the subsidiary rows.
					if (Integer.parseInt(row.get("HTKSYORI")) != 2) {
						zoneToAgeDistribution.put(row.get("KEY_CODE"), collectAgeDistributions(row));
					}

				} catch (RuntimeException e) {
					log.error("Error preprocessing zone", e);
					log.error(row.toString());
				}
			}
		}
		long ageGroupSum = ageGroupCntGlobal.values().stream().mapToInt(Integer::intValue).sum();
		EnumeratedAttributeDistribution<AgeGroup> ageGroupGlobal = new EnumeratedAttributeDistribution<>(Map.of(
			AgeGroup.AGE_0_14, ageGroupCntGlobal.getInt(AgeGroup.AGE_0_14) / ((double) ageGroupSum),
			AgeGroup.AGE_15_17, ageGroupCntGlobal.getInt(AgeGroup.AGE_15_17) / ((double) ageGroupSum),
			AgeGroup.AGE_18_19, ageGroupCntGlobal.getInt(AgeGroup.AGE_18_19) / ((double) ageGroupSum),
			AgeGroup.AGE_20_64, ageGroupCntGlobal.getInt(AgeGroup.AGE_20_64) / ((double) ageGroupSum),
			AgeGroup.AGE_65_74, ageGroupCntGlobal.getInt(AgeGroup.AGE_65_74) / ((double) ageGroupSum),
			AgeGroup.AGE_75_84, ageGroupCntGlobal.getInt(AgeGroup.AGE_75_84) / ((double) ageGroupSum),
			AgeGroup.AGE_85_94, ageGroupCntGlobal.getInt(AgeGroup.AGE_85_94) / ((double) ageGroupSum),
			AgeGroup.AGE_95_UP, ageGroupCntGlobal.getInt(AgeGroup.AGE_95_UP) / ((double) ageGroupSum)
		));


		try (CSVParser reader = new CSVParser(Files.newBufferedReader(input, Charset.forName("Shift_JIS")), format.build())) {


			List<CSVRecord> records = reader.getRecords();
			// skip second header row (explanation of columns in japanese)
			records.remove(0);
			for (CSVRecord row : ProgressBar.wrap(records, "Processing zones")) {
				try {
					processLOR(row, zoneToAgeDistribution, ageGroupGlobal);
				} catch (RuntimeException e) {
					log.error("Error processing zone", e);
					log.error(row.toString());
				}
			}
		}


		// Write Population
		log.info("Generated {} persons", population.getPersons().size());
		PopulationUtils.sortPersons(population);

		ProjectionUtils.putCRS(population, OpenGunmaScenario.CRS);
		PopulationUtils.writePopulation(population, output.toString());

		return 0;
	}

	private static void chooseColumnCodesBasedOnSex() {
		if (sex.equals(Sex.ALL)) {
			columnAllAges = "T001102001";
			column0to14 = "T001102004";
			column15up = "T001102007";
			column18up = "T001102013";
			column20up = "T001102016";
			column65up = "T001102019";
			column75up = "T001102022";
			column85up = "T001102025";
			column95up = "T001102028";

			throw new RuntimeException("Currently the sex distribution is commented out. Can be easily restored.");

		} else if (sex.equals(Sex.MALE)) {
			columnAllAges = "T001102002";
			column0to14 = "T001102005";
			column15up = "T001102008";
			column18up = "T001102014";
			column20up = "T001102017";
			column65up = "T001102020";
			column75up = "T001102023";
			column85up = "T001102026";
			column95up = "T001102029";
		} else if (sex.equals(Sex.FEMALE)) {
			columnAllAges = "T001102003";
			column0to14 = "T001102006";
			column15up = "T001102009";
			column18up = "T001102015";
			column20up = "T001102018";
			column65up = "T001102021";
			column75up = "T001102024";
			column85up = "T001102027";
			column95up = "T001102030";
		} else {
			throw new RuntimeException("This sex has not yet been configured");
		}
	}

	private static EnumeratedAttributeDistribution<AgeGroup> collectAgeDistributions(CSVRecord row) {

		Object2IntMap<AgeGroup> ageGroupCnt = new Object2IntAVLTreeMap<>();

		int age95_up = Integer.parseInt(row.get(column95up));
		int age85_up = Integer.parseInt(row.get(column85up));
		int age75_up = Integer.parseInt(row.get(column75up));
		int age65_up = Integer.parseInt(row.get(column65up));
		int age20_up = Integer.parseInt(row.get(column20up));
		int age18_up = Integer.parseInt(row.get(column18up));
		int age15_up = Integer.parseInt(row.get(column15up));

		int age0_14 = Integer.parseInt(row.get(column0to14));
		int age85_94 = age85_up - age95_up;
		int age75_84 = age75_up - age85_up;
		int age65_74 = age65_up - age75_up;
		int age20_64 = age20_up - age65_up;
		int age18_19 = age18_up - age20_up;
		int age15_17 = age15_up - age18_up;


		ageGroupCnt.put(AgeGroup.AGE_95_UP, age95_up);
		ageGroupCnt.put(AgeGroup.AGE_85_94, age85_94);
		ageGroupCnt.put(AgeGroup.AGE_75_84, age75_84);
		ageGroupCnt.put(AgeGroup.AGE_65_74, age65_74);
		ageGroupCnt.put(AgeGroup.AGE_20_64, age20_64);
		ageGroupCnt.put(AgeGroup.AGE_18_19, age18_19);
		ageGroupCnt.put(AgeGroup.AGE_15_17, age15_17);
		ageGroupCnt.put(AgeGroup.AGE_0_14, age0_14);

		for (Object2IntMap.Entry<AgeGroup> ageGroupEntry : ageGroupCnt.object2IntEntrySet()) {
			int current = ageGroupEntry.getIntValue();
			ageGroupCntGlobal.put(ageGroupEntry.getKey(), ageGroupCntGlobal.getInt(ageGroupEntry.getKey()) + current);
		}


		int ageGroupSum = ageGroupCnt.values().stream().mapToInt(Integer::intValue).sum();

		if (ageGroupSum == 0) {
			log.warn("Zone {} has zero population. Assuming global age distribution.", row.get("KEY_CODE"));
			return null;
		}

		EnumeratedAttributeDistribution<AgeGroup> ageGroup = new EnumeratedAttributeDistribution<>(Map.of(
			AgeGroup.AGE_0_14, ageGroupCnt.getInt(AgeGroup.AGE_0_14) / ((double) ageGroupSum),
			AgeGroup.AGE_15_17, ageGroupCnt.getInt(AgeGroup.AGE_15_17) / ((double) ageGroupSum),
			AgeGroup.AGE_18_19, ageGroupCnt.getInt(AgeGroup.AGE_18_19) / ((double) ageGroupSum),
			AgeGroup.AGE_20_64, ageGroupCnt.getInt(AgeGroup.AGE_20_64) / ((double) ageGroupSum),
			AgeGroup.AGE_65_74, ageGroupCnt.getInt(AgeGroup.AGE_65_74) / ((double) ageGroupSum),
			AgeGroup.AGE_75_84, ageGroupCnt.getInt(AgeGroup.AGE_75_84) / ((double) ageGroupSum),
			AgeGroup.AGE_85_94, ageGroupCnt.getInt(AgeGroup.AGE_85_94) / ((double) ageGroupSum),
			AgeGroup.AGE_95_UP, ageGroupCnt.getInt(AgeGroup.AGE_95_UP) / ((double) ageGroupSum)
		));

		return ageGroup;
	}

	private void processLOR(CSVRecord row, Map<String, EnumeratedAttributeDistribution<AgeGroup>> zoneToAgeDistribution, EnumeratedAttributeDistribution<AgeGroup> ageGroupGlobal) throws ParseException {

		String keyCode = row.get("KEY_CODE");

		// Population

		int n = Integer.parseInt(row.get(columnAllAges));
//
//
//		// GENDER
//		double women = Double.parseDouble(row.get("T001102003"));
//		double quota = women / n;
//		var sex = new EnumeratedAttributeDistribution<>(Map.of("f", quota, "m", 1 - quota));


		// AGE
		EnumeratedAttributeDistribution<AgeGroup> ageGroup;
		if (Integer.parseInt(row.get("HTKSYORI")) == 2) {
			String parentKeyCode = row.get("HTKSAKI");
			ageGroup = zoneToAgeDistribution.get(parentKeyCode);
		} else {
			ageGroup = zoneToAgeDistribution.get(keyCode);

		}

		if (ageGroup == null) {
			ageGroup = ageGroupGlobal;
		}


		// UNEMPLOYMENT
		// sometimes this entry is not set
//		double unemployed;
//		try {
//			unemployed = FMT.parse(row.get("Anteil Arbeitslose nach SGB II und SGB III an Einwohnerinnen und Einwohner (EW) im Alter von 15 bis unter 65 Jahren")).doubleValue() / 100;
//		} catch (ParseException e) {
//			unemployed = 0;
//			log.warn("LOR {} {} has no unemployment", keyCode, row.get(1));
//		}

//		var employment = new EnumeratedAttributeDistribution<>(Map.of(true, 1 - unemployed, false, unemployed));

		if (!zones.containsKey(keyCode)) {
			log.warn("Zone {} not found", keyCode);
			return;
		}

		MultiPolygon geom = zones.get(keyCode);


		PopulationFactory f = population.getFactory();

//		var youngDist = new UniformAttributeDistribution<>(IntStream.range(1, 18).boxed().toList());
//		var middleDist = new UniformAttributeDistribution<>(IntStream.range(18, 65).boxed().toList());
//		var oldDist = new UniformAttributeDistribution<>(IntStream.range(65, 100).boxed().toList());

		for (int i = 0; i < n * sample; i++) {


			Person person = f.createPerson(generateId(population, "gunma", rnd));
			PersonUtils.setSex(person, sex.abbr);
			PopulationUtils.putSubpopulation(person, "person");

			AgeGroup group = ageGroup.sample();

			PersonUtils.setAge(person, group.getDistribution().sample());

//			if (group == AgeGroup.AGE_0_14) {
//				PersonUtils.setEmployed(person, employment.sample());
//			} else if (group == AgeGroup.YOUNG) {
//				PersonUtils.setAge(person, youngDist.sample());
//				PersonUtils.setEmployed(person, false);
//			} else if (group == AgeGroup.OLD) {
//				PersonUtils.setAge(person, oldDist.sample());
//				PersonUtils.setEmployed(person, false);
//			}


			// CRS of GEOM: 2450
			Coord coord = ct.transform(sampleHomeCoordinate(geom, OpenGunmaScenario.CRS, facilities, rnd, 1500));

			person.getAttributes().putAttribute(Attributes.HOME_X, coord.getX());
			person.getAttributes().putAttribute(Attributes.HOME_Y, coord.getY());

			Plan plan = f.createPlan();
			plan.addActivity(f.createActivityFromCoord("home", coord));

			person.addPlan(plan);
			person.setSelectedPlan(plan);

			population.addPerson(person);
		}
	}

	private enum AgeGroup {

		AGE_0_14(0, 14),
		AGE_15_17(15, 17),
		AGE_18_19(18, 19),
		AGE_20_64(20, 64),
		AGE_65_74(65, 74),
		AGE_75_84(75, 84),
		AGE_85_94(85, 94),
		AGE_95_UP(95, 105);

		private final int minAge;
		private final int maxAge;

		private final UniformAttributeDistribution<Integer> distribution;

		AgeGroup(int minAge, int maxAge) {
			this.minAge = minAge;
			this.maxAge = maxAge;
			this.distribution = new UniformAttributeDistribution<>(IntStream.range(minAge, maxAge).boxed().toList());
		}

		public int getMinAge() {
			return minAge;
		}

		public int getMaxAge() {
			return maxAge;
		}

		public UniformAttributeDistribution<Integer> getDistribution() {
			return distribution;
		}
	}


	enum Sex {
		MALE("m"),
		FEMALE("f"),
		ALL(null);

		public final String abbr;

		Sex(String abbr){
			this.abbr = abbr;
		}
	}
}
