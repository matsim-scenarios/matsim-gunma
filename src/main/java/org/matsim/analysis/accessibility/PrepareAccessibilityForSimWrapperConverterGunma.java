package org.matsim.analysis.accessibility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.GridUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import picocli.CommandLine;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.io.csv.CsvWriteOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.matsim.contrib.accessibility.AccessibilityModule.CONFIG_FILENAME_ACCESSIBILITY;


@CommandLine.Command(
	name = "accessibility", description = "Accessibility analysis.",
	mixinStandardHelpOptions = true, showDefaultValues = true
)
@CommandSpec(requireRunDirectory = true,
	produces = {
		"%s/accessibilities_simwrapper.csv"
	}
)


public class PrepareAccessibilityForSimWrapperConverterGunma implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(PrepareAccessibilityForSimWrapperConverterGunma.class);


	// MATSim output directory; this should already contain the "analysis/accessibility/" subdirectories, as the Accessibility Post-Processing should have already occurred
	// should contain "analysis/accessibility/{POI}/accessibilities.csv" file containing the coordiantes for the measuring point (xcoord, ycoord)
	// as well as accessibilities for different modes (i.e. freespeed_accessibility, pt_accessibility)
	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(PrepareAccessibilityForSimWrapperConverterGunma.class);

	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(PrepareAccessibilityForSimWrapperConverterGunma.class);


	private final boolean countPopulation = true;

	public static void main(String[] args) {
		new PrepareAccessibilityForSimWrapperConverterGunma().execute(args);
	}


	@Override
	public Integer call() throws Exception {


		// Checks for what POIs the accesibility analysis has been run
		Set<String> activityOptions = null;
		try {
			activityOptions = Files.list(input.getRunDirectory().resolve("analysis/accessibility/"))
				.filter(Files::isDirectory)
				.map(Path::getFileName)
				.map(Path::toString).
				collect(Collectors.toSet());
		} catch (IOException e) {

			log.error(e.getMessage());
		}

		// for each opportunity type, we copy the accessibilities.csv file and simply rename two columns (x,y)
		for (String activityOption : activityOptions) {

			String folderNameForActivityOption = input.getRunDirectory() + "/analysis/accessibility/" + activityOption;
			File folder = new File(folderNameForActivityOption);
			List<File> files = Arrays.stream(Objects.requireNonNull(folder.listFiles((dir, name) -> name.endsWith("accessibilities.csv")))).toList();

			for (File file : files) {

				String filePath = file.getAbsolutePath();

				String outputPath = file.getAbsolutePath().replace("accessibilities.csv", "accessibilities_simwrapper.csv");

				try {
					Path path = Path.of(outputPath);
					if (Files.exists(path)) {
						Files.delete(path);
						log.info("File deleted: " + outputPath);
					} else {
						log.warn("File does not exist: " + outputPath);
					}
				} catch (IOException e) {
					log.error("Failed to delete file: " + e.getMessage());
				}

//				try {
					// Use CsvReadOptions to configure the CSV reading options
					CsvReadOptions options = CsvReadOptions.builder(filePath)
						.separator(',')
						.header(true)
						.missingValueIndicator("")
						.build();

					// Read the CSV file into a Table object
					Table table = Table.read().csv(options);

//					table.removeColumns("id");
					DoubleColumn xCol = table.doubleColumn("xcoord");
					DoubleColumn yCol = table.doubleColumn("ycoord");
					xCol.setName("x");
					yCol.setName("y");

					if (countPopulation) {
						popAgeAnalysis(table, activityOption, xCol, yCol);
					}


					// Write the modified table to a new CSV file
					CsvWriteOptions writeOptions = CsvWriteOptions.builder(outputPath)
						.separator(',')
						.header(true)
						.build();

					table.write().csv(writeOptions);

//				} catch (Exception e) {
//					log.error(e.getMessage());
//				}

			}

		}



		return 0;
	}

	private void popAgeAnalysis(Table table, String activityOption, DoubleColumn xCol, DoubleColumn yCol) {
		// Count Population per Pixel
		String populationPath = ApplicationUtils.matchInput("output_plans", input.getRunDirectory()).toAbsolutePath().toString();
		Config config = ConfigUtils.loadConfig(input.getRunDirectory() + "/analysis/accessibility/" + activityOption + "/" + CONFIG_FILENAME_ACCESSIBILITY);

		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);

		LongColumn popCol = LongColumn.create("population");
		LongColumn pop65plusCol = LongColumn.create("population65+");
		LongColumn pop75plusCol = LongColumn.create("population75+");
		LongColumn pop85plusCol = LongColumn.create("population85+");

		LongColumn ageCol = LongColumn.create("ageColumn");


		for (int i = 0; i < table.rowCount(); i++) {
			popCol.append(0L);
			pop65plusCol.append(0L);
			pop75plusCol.append(0L);
			pop85plusCol.append(0L);
			ageCol.append(0);
		}

		Scenario scenario = ScenarioUtils.createScenario(config);
		new PopulationReader(scenario).readFile(populationPath);


		double boundingBoxLeft;
		double boundingBoxRight;
		double boundingBoxBottom;
		double boundingBoxTop;
		if (acg.getAreaOfAccessibilityComputation().equals(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromBoundingBox)) {
			boundingBoxRight = acg.getBoundingBoxRight();
			boundingBoxLeft = acg.getBoundingBoxLeft();
			boundingBoxTop = acg.getBoundingBoxTop();
			boundingBoxBottom = acg.getBoundingBoxBottom();
		} else if (acg.getAreaOfAccessibilityComputation().equals(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromShapeFile)) {
			Geometry boundary = GridUtils.getBoundary(acg.getShapeFileCellBasedAccessibility());
			Envelope envelope = boundary.getEnvelopeInternal();

			boundingBoxLeft = envelope.getMinX();
			boundingBoxRight = envelope.getMaxX();
			boundingBoxBottom = envelope.getMinY();
			boundingBoxTop = envelope.getMaxY();
		} else {
			throw new RuntimeException("Unsupported area of accessibility computation: " + acg.getAreaOfAccessibilityComputation());
		}

		for (Person person : scenario.getPopulation().getPersons().values()) {

			if (person.getId().toString().startsWith("commuter_")) {
				continue;
			}

			Double homeX = (Double) person.getAttributes().getAttribute("home_x");
			Double homeY = (Double) person.getAttributes().getAttribute("home_y");

			// Skip this person if home coordinates are not available
			if (homeX == null || homeY == null) {
				continue;
			}


			if (homeX > boundingBoxRight || homeX < boundingBoxLeft || homeY > boundingBoxTop || homeY < boundingBoxBottom) {
				continue;
			}

			int age = (Integer) person.getAttributes().getAttribute("age");

			// Find the closest pixel in the table
			double closestDistance = Double.MAX_VALUE;
			int closestRow = -1;
			for (int i = 0; i < table.rowCount(); i++) {
				double pixelX = xCol.getDouble(i);
				double pixelY = yCol.getDouble(i);
				double distance = Math.sqrt(Math.pow(homeX - pixelX, 2) + Math.pow(homeY - pixelY, 2));

				if (distance < closestDistance) {
					closestDistance = distance;
					closestRow = i;
				}
			}

			if (closestRow == -1) {
				throw new RuntimeException("closest row be different than initialized value");
			}

			ageCol.set(closestRow, ageCol.getLong(closestRow) + age);

			popCol.set(closestRow, popCol.getLong(closestRow) + 1);

			if (age >= 65) {
				pop65plusCol.set(closestRow, pop65plusCol.getLong(closestRow) + 1);
			}

			if (age >= 75) {
				pop75plusCol.set(closestRow, pop75plusCol.getLong(closestRow) + 1);
			}

			if (age >= 85) {
				pop85plusCol.set(closestRow, pop85plusCol.getLong(closestRow) + 1);
			}


		}


		DoubleColumn ageAvg = ageCol.divide(popCol).setName("age");


		DoubleColumn pop65plusShareCol = pop65plusCol.divide(popCol).setName("populationShare65+");
		DoubleColumn pop75plusShareCol = pop75plusCol.divide(popCol).setName("populationShare75+");
		DoubleColumn pop85plusShareCol = pop85plusCol.divide(popCol).setName("populationShare85+");


		table.addColumns(ageAvg, popCol, pop65plusCol, pop75plusCol, pop85plusCol, pop65plusShareCol, pop75plusShareCol, pop85plusShareCol);
	}


}
