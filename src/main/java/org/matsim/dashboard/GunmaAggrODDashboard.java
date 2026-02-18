package org.matsim.dashboard;

import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.AggregateOD;

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
	private final String idColumn = "id";
	private final double height = 15.0;
	private final String shpFileDbf;
	private final String prefectureShpFileDbf;

	public GunmaAggrODDashboard(String shpFile, String crs, String odFlowsFile, String odFlowsOutsideFile, String odFlowsOutsidePrefectureFile, String prefectureShpFile) {
		this.shpFile = shpFile;
		this.shpFileDbf = shpFile.replace(".shp", ".dbf");
		this.crs = crs;
		this.odFlowsFile = odFlowsFile;
		this.odFlowsOutsideFile = odFlowsOutsideFile;
		this.odFlowsOutsidePrefectureFile = odFlowsOutsidePrefectureFile;
		this.prefectureShpFile = prefectureShpFile;
		prefectureShpFileDbf = prefectureShpFile.replace(".shp", ".dbf");

	}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "OD Flows for Work Trips";
		header.tab = "OD Work";
		header.description = "Origin-Destination Flows for Work Trips (Clipped to 50/100 km envelope around Gunma)";
		header.fullScreen = false;


		// ###### TAB 1: OD Flows within Gunma Prefecture ######
		createTab(layout, "within-gunma", shpFile, shpFileDbf, odFlowsFile);

		// ###### TAB 2: OD Flows outside of Gunma Prefecture (Municipality) ######
		createTab(layout, "outside-gunma", shpFile, shpFileDbf, odFlowsOutsideFile);

		// ###### TAB 3: OD Flows outside of Gunma Prefecture (Prefecture) ######
		createTab(layout, "outside-pref-gunma", prefectureShpFile, prefectureShpFileDbf, odFlowsOutsidePrefectureFile);



	}

	private void createTab(Layout layout, String name, String shpFile2, String shpFileDbf2, String odFlowsFile2) {

		layout.row(name + "-1")
			.el(AggregateOD.class, (viz, data) -> {
				viz.title = "Simulated OD Flows";
				viz.height = height;
				viz.shpFile = data.resource(shpFile2);
				viz.dbfFile = data.resource(shpFileDbf2);
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsFile2);
				viz.idColumn = idColumn;
				viz.scaleFactor = 1.0;
			}).el(AggregateOD.class, (viz, data) -> {
				viz.title = "Reference OD Flows";
				viz.height = height;
				viz.shpFile = data.resource(shpFile2);
				viz.dbfFile = data.resource(shpFileDbf2);
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsFile2.replace("sim", "ref"));
				viz.idColumn = idColumn;
				viz.scaleFactor = 1.0;
			});

		layout.row(name + "-2")
			.el(AggregateOD.class, (viz, data) -> {
				viz.title = "Difference Plot: Simulated - Reference (Absolute Value)";
				viz.height = height;
				viz.shpFile = data.resource(shpFile2);
				viz.dbfFile = data.resource(shpFileDbf2);
				viz.projection = crs;
				viz.csvFile = data.resource(odFlowsFile2.replace("sim", "diff"));
				viz.idColumn = idColumn;
				viz.scaleFactor = 1.0;
			})
		;

		layout.tab(name)
			.add(name + "-1")
			.add(name + "-2");
	}

}
