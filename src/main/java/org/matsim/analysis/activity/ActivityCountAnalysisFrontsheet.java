package org.matsim.analysis.activity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.*;
import picocli.CommandLine;
import tech.tablesaw.api.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Counts how many activities are in the population and compares to reference data. Also creates a difference table for the per-region analysis.
 */
@CommandSpec(
	requires = {"activities.csv"},
	produces = {"activities_%s_frontsheet.csv", "activities_%s_diff.csv", }
)
public class ActivityCountAnalysisFrontsheet implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(ActivityCountAnalysisFrontsheet.class);


	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(ActivityCountAnalysisFrontsheet.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(ActivityCountAnalysisFrontsheet.class);
//	@CommandLine.Option(names = "--ref-file", description = "Path to reference file")
//	private String refFile;
//	@CommandLine.Option(names = "--sim-file", description = "Path to simulation file")
//	private String simFile;

	public static void main(String[] args) {
		new ActivityCountAnalysisFrontsheet().execute(args);
	}

	/**
	 * Executes the activity count analysis.
	 *
	 * @return Exit code (0 for success).
	 * @throws Exception if errors occur during execution.
	 */
	@Override
	public Integer call() throws Exception {

		Path dir = output.getPath().getParent();

		for (Path simFile : Files.newDirectoryStream(dir)) {
			if (simFile.toString().endsWith("_per_region.csv")) {

				Table simTable = Table.read().csv(simFile.toFile());

				IntColumn simCol = simTable.intColumn("count");

				int simSum = (int) simCol.sum();

				StringColumn metricColOut = StringColumn.create("Metric", "Total Activities");
				IntColumn simColOut = IntColumn.create("Simulated");
				simColOut.append(simSum);

				Table outputTable;

				String refFileName = simFile.getFileName().toString().replace("region.csv", "region-ref.csv");
				Path refFile = simFile.getParent().getParent().resolve("resources/" + refFileName);
				if (Files.exists(refFile)) {
					Table refTable = Table.read().csv(refFile.toFile());

					IntColumn refCol = refTable.intColumn("count");
					int refSum = (int) refCol.sum();
					IntColumn refColOut = IntColumn.create("Reference");
					refColOut.append(refSum);
					outputTable = Table.create("outputtable", metricColOut, simColOut, refColOut);


					// Difference Table

					refTable.column("count").setName("count_ref");
					simTable.column("count").setName("count_sim");

					refTable.column("density").setName("density_ref");
					simTable.column("density").setName("density_sim");

					refTable.column("relative_density").setName("relative_density_ref");
					simTable.column("relative_density").setName("relative_density_sim");
					Table joined = refTable.joinOn("id").allowDuplicateColumnNames(true).inner(simTable);

					joined.addColumns(
						joined.numberColumn("count_sim")
							.subtract(joined.numberColumn("count_ref"))
							.setName("count"),
						joined.numberColumn("density_sim")
							.subtract(joined.numberColumn("density_ref"))
							.setName("density"),
						joined.numberColumn("relative_density_sim")
							.subtract(joined.numberColumn("relative_density_ref"))
							.setName("relative_density")
					);

					joined.selectColumns("id", "count", "density", "relative_density").write().csv(simFile.toString().replace("per_region", "diff"));


				} else {
					outputTable = Table.create("outputtable", metricColOut, simColOut);
				}


				String outputFileName = simFile.toString().replace("per_region", "frontsheet");
				outputTable.write().csv(outputFileName);
			}
		}





		return 0;
	}
}
