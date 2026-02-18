package org.matsim.dashboard;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.matsim.analysis.activity.ActivityCountAnalysisFrontsheet;
import org.matsim.application.analysis.activity.ActivityCountAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Data;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard to show activity related statistics aggregated by type and location.
 * <p>
 * Note that {@link #addActivityType(String, List, List, boolean, String)} needs to be called for each activity type.
 * There is no default configuration.
 */
public class GunmaActivityDashboard implements Dashboard {

	private static final String ID_COLUMN = "id";

	private final String shpFile;
	private final Map<String, String> activityMapping = new LinkedHashMap<>();
	private final Map<String, String> refCsvs = new LinkedHashMap<>();
	private final Set<String> countSingleOccurrencesSet = new HashSet<>();
	private List<Indicator> indicators = new ArrayList<>();
	private String projection = null;


	/**
	 * Create a new activity dashboard using the default shape file.
	 * Note that the shape file must contain multiple regions with an "id" column.
	 * This dashboard can be created with {@link GunmaActivityDashboard (String)} to use a separate shape file for this analysis..
	 */
	public GunmaActivityDashboard() {
		this.shpFile = null;
	}

	/**
	 * Create a new activity dashboard using the given shape file.
	 * @param shpFile Path to the shape file containing an "id" column.
	 */
	public GunmaActivityDashboard(@Nullable String shpFile) {
		this.shpFile = shpFile;
	}

	/**
	 * Set the projection for the map plots. This is optional and can be used if the shape file uses a different projection than WGS84.
	 */
	public void setProjection(String projection){
		this.projection = projection;
	}

	/**
	 * Convenience method to add an activity type with default configuration.
	 */
	public GunmaActivityDashboard addActivityType(String name, List<String> activities, List<Indicator> indicators) {
		return addActivityType(name, activities, indicators, true, null);
	}


	/**
	 * Add an activity type to the dashboard.
	 *
	 * @param name                     name to show in the dashboard
	 * @param activities               List of activity names to include in this type
	 * @param indicators               List of indicators to show
	 * @param countMultipleOccurrences Whether multiple occurrences of the same activity for one person should be counted.
	 *                                 Can be used to count home or workplaces only once.
	 * @param refCsv                   Reference CSV file to compare the activities to. Can be null.
	 */
	public GunmaActivityDashboard addActivityType(String name, List<String> activities, List<Indicator> indicators, boolean countMultipleOccurrences, @Nullable String refCsv) {

		activityMapping.put(name, String.join(",", activities));
		refCsvs.put(name, refCsv);

		if (!countMultipleOccurrences) {
			countSingleOccurrencesSet.add(name);
		}

		this.indicators = indicators;
		return this;
	}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Activities";
		header.description = "Displays the activities by type and location.";

		List<String> args = new ArrayList<>(List.of("--id-column", ID_COLUMN));
		args.add("--activity-mapping");
		args.add(activityMapping.entrySet().stream()
			.map(e -> "%s=%s".formatted(e.getKey(), e.getValue()))
			.collect(Collectors.joining(";")));

		if (!countSingleOccurrencesSet.isEmpty()) {
			args.add("--single-occurrence");
			args.add(String.join(";", countSingleOccurrencesSet));
		}

		args.add("--use-km2");
//		args.add("true");

		for (Map.Entry<String, String> activity : activityMapping.entrySet()) {

			String activityName = StringUtils.capitalize(activity.getKey());

//			layout.row("category_header_" + activity.getKey())
//				.el(TextBlock.class, (viz, data) -> {
//					viz.content = "## **" + activityName + "**" +
//						"\n\n ;
//					viz.backgroundColor = "transparent";
//				});

			layout.row("frontsheet_" + activity.getKey()).el(Table.class, (viz, data) -> {


//				String[] argsFrontsheet = List.of("--ref-file", "resources/activities_" + activity.getKey() + "_per_region-ref.csv", "--sim-file", "output-1pct/analysis/activity/activities_" + activity.getKey() + "_per_region.csv").toArray(new String[0]);

				viz.dataset = data.computeWithPlaceholder(ActivityCountAnalysisFrontsheet.class, "activities_%s_frontsheet.csv", activity.getKey());
				viz.height = 1.;
//				viz.style = "topsheet";

			});

			for (Indicator ind : Indicator.values()) {

				if (indicators.contains(ind)) {
					addPlotsForIndicator(layout, activity, ind, activityName, args);
				}
			}
		}
	}

	private void addPlotsForIndicator(Layout layout, Map.Entry<String, String> activity, Indicator ind, String activityName, List<String> args) {
		addIntroductionRow(layout, activity, ind);

		Layout.Row row = layout.row(activity.getKey() + "_" + ind.name)
			.el(MapPlot.class, (viz, data) -> {
				viz.title = "Simulated %s Activities (%s)".formatted(activityName, ind.displayName);
				viz.height = 8.;
				String shp = data.resource(shpFile);
				viz.setShape(shp, ID_COLUMN);
				if (projection != null) {
					viz.projection = projection;
				}
				viz.addDataset("activities", data.computeWithPlaceholder(ActivityCountAnalysis.class,
					"activities_%s_per_region.csv", activity.getKey(), args.toArray(new String[0])));
				viz.display.fill.columnName = ind.name;
				viz.display.fill.dataset = "activities";
				viz.display.fill.join = ID_COLUMN;
				if (ind == Indicator.RELATIVE_DENSITY) {
					viz.display.fill.setColorRamp(ColorScheme.RdBu, 11, false, "0.2, 0.25, 0.33, 0.5, 0.67, 1.5, 2.0, 3.0, 4.0, 5.0");
				} else if (ind == Indicator.COUNTS) {
					viz.display.fill.setColorRamp(ColorScheme.Viridis, 6, true, "3,30,300,3000,30000");
				} else if (ind == Indicator.DENSITY) {
					viz.display.fill.setColorRamp(ColorScheme.Viridis, 10, true, "100,200,300,400,500,600,700,800,900");

				}

				// Needs to use custom shape file
				if (shpFile != null)
					data.shp(ActivityCountAnalysis.class, shpFile);
			});


		if (refCsvs.get(activity.getKey()) != null) {
			row.el(MapPlot.class, (viz, data) -> {

				viz.title = "Reference %s Activities (%s)".formatted(activityName, ind.displayName);
				viz.height = 8.;

				String shp = data.resource(shpFile);
				viz.setShape(shp, ID_COLUMN);
				if (projection != null) {
					viz.projection = projection;
				}

				viz.addDataset("activities", data.resource(refCsvs.get(activity.getKey())));

				viz.display.fill.dataset = "activities";
				viz.display.fill.join = ID_COLUMN;

//							BackgroundLayer layer = addGunmaOutlineBackgroundLayer(data);
//							viz.addBackgroundLayer("gunma_outline", layer);

				if (ind == Indicator.RELATIVE_DENSITY) {
					viz.display.fill.columnName = "relative_density";
					viz.display.fill.setColorRamp(ColorScheme.RdBu, 11, false, "0.2, 0.25, 0.33, 0.5, 0.67, 1.5, 2.0, 3.0, 4.0, 5.0");
				} else if (ind == Indicator.DENSITY) {
					viz.display.fill.columnName = "density";
					viz.display.fill.setColorRamp(ColorScheme.Viridis, 10, true, "100,200,300,400,500,600,700,800,900");

				} else {
					viz.display.fill.columnName = "count";
					viz.display.fill.setColorRamp(ColorScheme.Viridis, 6, true, "3,30,300,3000,30000");


				}
			});

			addDifferencePlot(layout, activity, ind, activityName);

		}

//					layout.tab(activityName).add("category_header_" + activity.getKey());
		layout.tab(activityName).add("frontsheet_" + activity.getKey());
		layout.tab(activityName).add(activity.getKey() + "_" + ind.name + "_description");
		layout.tab(activityName).add(activity.getKey() + "_" + ind.name);
		if (refCsvs.get(activity.getKey()) != null) {
			layout.tab(activityName).add(activity.getKey() + "_" + ind.name + "_diff");
		}
	}

	private void addDifferencePlot(Layout layout, Map.Entry<String, String> activity, Indicator ind, String activityName) {
		layout.row(activity.getKey() + "_" + ind.name + "_diff")
			.el(MapPlot.class, (viz, data) -> {
				viz.title = "Simulated - Ref %s Activities (%s)".formatted(activityName, ind.displayName);
				viz.height = 8.;
				String shp = data.resource(shpFile);
				viz.setShape(shp, ID_COLUMN);
				if (projection != null) {
					viz.projection = projection;
				}

				viz.addDataset("diff", data.computeWithPlaceholder(ActivityCountAnalysisFrontsheet.class,
					"activities_%s_diff.csv", activity.getKey()));

//								viz.addDataset("sim", data.computeWithPlaceholder(ActivityCountAnalysis.class,
//									"activities_%s_per_region.csv", activity.getKey(), args.toArray(new String[0])));
//								viz.addDataset("ref", data.resource(refCsvs.get(activity.getKey())));


				viz.display.fill.columnName = ind.name;
				viz.display.fill.dataset = "diff";
//								viz.display.fill.diff = "sim - ref";
				viz.display.fill.join = ID_COLUMN;
				if (ind == Indicator.RELATIVE_DENSITY) {
					viz.display.fill.setColorRamp(ColorScheme.RdBu, 8, false, "-.75,-.5,-.25,0.0,.25,.5,.75");
				} else if (ind == Indicator.COUNTS) {
					viz.display.fill.setColorRamp(ColorScheme.RdBu, 9, true, "-100000,-10000,-1000,-100,100,1000,10000,100000");
				} else if (ind == Indicator.DENSITY) {
					viz.display.fill.setColorRamp(ColorScheme.RdBu, 10, true, "-100,-75,-50,-25,0,25,50,75,100");

				}

//								BackgroundLayer layer = addGunmaOutlineBackgroundLayer(data);
//								viz.addBackgroundLayer("gunma_outline", layer);

				// Needs to use custom shape file
				if (shpFile != null)
					data.shp(ActivityCountAnalysis.class, shpFile);
			});
	}

	private static void addIntroductionRow(Layout layout, Map.Entry<String, String> activity, Indicator ind) {
		layout.row(activity.getKey() + "_" + ind.name + "_description")
			.el(TextBlock.class, (viz, data) -> {
				String txt = "## **" + ind.displayName + "** \n\n";
				if (ind == Indicator.COUNTS) {
					txt+= "Shows the total number of activities per municipality in Gunma Prefecture. \n The reference data includes trips that begin in other prefectures but end in Gunma. So far, the simulated data only includes activities that are performed by agents whose home location is in Gunma.";
				} else if (ind == Indicator.DENSITY) {
					txt+= "Shows the density of activities per region (number of activities divided by area (in km2).";
				} else if (ind == Indicator.RELATIVE_DENSITY) {
					txt += "Shows the relative density of activities per region (density / mean(density)).";
				}
				viz.content = txt;
				viz.backgroundColor = "transparent";
			});
	}

	private static @NonNull BackgroundLayer addGunmaOutlineBackgroundLayer(Data data) {
		BackgroundLayer layer = new BackgroundLayer(data.resource("resources/gunma_2450.shp"));
//		layer.setOnTop(true);
		layer.setBorderColor("red");
		layer.setBorderWidth(4);
//		layer.setFill("transparent");
		return layer;
	}

	/**
	 * Metric to show in the dashboard.
	 */
	public enum Indicator {
		COUNTS("count", "Counts"),
		DENSITY("density", "Density"),
		RELATIVE_DENSITY("relative_density", "Relative Density");

		private final String name;
		private final String displayName;

		Indicator(String name, String displayName) {
			this.name = name;
			this.displayName = displayName;
		}
	}
}


























