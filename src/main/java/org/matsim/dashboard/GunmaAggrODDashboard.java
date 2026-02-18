package org.matsim.dashboard;

import org.matsim.facilities.FacilityAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.AggregateOD;
import org.matsim.simwrapper.viz.BackgroundLayer;
import org.matsim.simwrapper.viz.MapPlot;

import java.util.List;

/**
 * OD Flows in Gunma scenario.
 */
public class GunmaAggrODDashboard implements Dashboard {


	private final String odFlowsFile;
	private final String odFlowsOutsideFile;
	private final String odFlowsOutsidePrefectureFile;
	private final String shpFile;
	private final String crs;
	private final String prefectureShpFile;

	public GunmaAggrODDashboard(String shpFile, String crs, String odFlowsFile, String odFlowsOutsideFile, String  odFlowsOutsidePrefectureFile, String prefectureShpFile) {
		this.shpFile = shpFile;
		this.crs = crs;
		this.odFlowsFile = odFlowsFile;
		this.odFlowsOutsideFile = odFlowsOutsideFile;
		this.odFlowsOutsidePrefectureFile = odFlowsOutsidePrefectureFile;
		this.prefectureShpFile = prefectureShpFile;
	}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "OD Flows for Work Trips";
		header.tab = "OD Work";
		header.description = "Origin-Destination Flows for Work Trips (Clipped to 50/100 km envelope around Gunma)";
		header.fullScreen = false;


		double height = 15.0;

		// ###### TAB 1: OD Flows within Gunma Prefecture ######
		layout.row("within-gunma-1")
			.el(AggregateOD.class, (viz, data) -> {
				viz.title = "Simulated OD Flows";
				viz.height = height;
				viz.shpFile = data.resource(shpFile);
				viz.dbfFile = data.resource(shpFile.replace(".shp", ".dbf"));
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsFile);
				viz.idColumn = "id";
				viz.scaleFactor = 1.0;
			}).el(AggregateOD.class, (viz, data) -> {
				viz.title = "Reference OD Flows";
				viz.height = height;
				viz.shpFile = data.resource(shpFile);
				viz.dbfFile = data.resource(shpFile.replace(".shp", ".dbf"));
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsFile.replace("sim", "ref"));
				viz.idColumn = "id";
				viz.scaleFactor = 1.0;
			});

		layout.row("within-gunma-2")
			.el(AggregateOD.class, (viz, data) -> {
				viz.title = "Difference Plot: Simulated - Reference (Absolute Value)";
				viz.height = height;
				viz.shpFile = data.resource(shpFile);
				viz.dbfFile = data.resource(shpFile.replace(".shp", ".dbf"));
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsFile.replace("sim", "diff"));
				viz.idColumn = "id";
				viz.scaleFactor = 1.0;
			})
		;

// ###### TAB 2: OD Flows outside of Gunma Prefecture (Municipality) ######
		layout.row("outside-gunma-1")
			.el(AggregateOD.class, (viz, data) -> {
				viz.title = "Simulated OD Flows";
				viz.height = height;
				viz.shpFile = data.resource(shpFile);
				viz.dbfFile = data.resource(shpFile.replace(".shp", ".dbf"));
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsOutsideFile);
				viz.idColumn = "id";
				viz.scaleFactor = 1.0;
			}).el(AggregateOD.class, (viz, data) -> {
				viz.title = "Reference OD Flows";
				viz.height = height;
				viz.shpFile = data.resource(shpFile);
				viz.dbfFile = data.resource(shpFile.replace(".shp", ".dbf"));
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsOutsideFile.replace("sim", "ref"));
				viz.idColumn = "id";
				viz.scaleFactor = 1.0;
			});

		layout.row("outside-gunma-2")
			.el(AggregateOD.class, (viz, data) -> {
				viz.title = "Difference Plot: Simulated - Reference (Absolute Value)";
				viz.height = height;
				viz.shpFile = data.resource(shpFile);
				viz.dbfFile = data.resource(shpFile.replace(".shp", ".dbf"));
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsOutsideFile.replace("sim", "diff"));
				viz.idColumn = "id";
				viz.scaleFactor = 1.0;

			})
		;

		// ###### TAB 3: OD Flows outside of Gunma Prefecture (Prefecture) ######

		layout.row("outside-pref-gunma-1")
			.el(AggregateOD.class, (viz, data) -> {
				viz.title = "Simulated OD Flows";
				viz.height = height;
				viz.shpFile = data.resource(prefectureShpFile);
				viz.dbfFile = data.resource(prefectureShpFile.replace(".shp", ".dbf"));
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsOutsidePrefectureFile);
				viz.idColumn = "id";
				viz.scaleFactor = 1.0;
			}).el(AggregateOD.class, (viz, data) -> {
				viz.title = "Reference OD Flows";
				viz.height = height;
				viz.shpFile = data.resource(prefectureShpFile);
				viz.dbfFile = data.resource(prefectureShpFile.replace(".shp", ".dbf"));
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsOutsidePrefectureFile.replace("sim", "ref"));
				viz.idColumn = "id";
				viz.scaleFactor = 1.0;
			});

		layout.row("outside-pref-gunma-2")
			.el(AggregateOD.class, (viz, data) -> {
				viz.title = "Difference Plot: Simulated - Reference (Absolute Value)";
				viz.height = height;
				viz.shpFile = data.resource(prefectureShpFile);
				viz.dbfFile = data.resource(prefectureShpFile.replace(".shp", ".dbf"));
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsOutsidePrefectureFile.replace("sim", "diff"));
				viz.idColumn = "id";
				viz.scaleFactor = 1.0;

			})
		;



		layout.tab("Within Gunma")
			.add("within-gunma-1")
			.add("within-gunma-2");

		layout.tab("Outside Gunma")
			.add("outside-gunma-1")
			.add("outside-gunma-2");

		layout.tab("Outside Gunma - Prefecture")
			.add("outside-pref-gunma-1")
			.add("outside-pref-gunma-2");


	}

}
