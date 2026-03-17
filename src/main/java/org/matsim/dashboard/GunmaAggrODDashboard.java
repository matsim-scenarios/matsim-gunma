package org.matsim.dashboard;

import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.viz.AggregateOD;

import java.util.ArrayList;
import java.util.List;

/**
 * OD Flows in Gunma scenario.
 */
public class GunmaAggrODDashboard implements Dashboard {

	private final String crs;
	private final String idColumn = "id";
	private final double height = 15.0;


	private final List<String> titles = new ArrayList<>();
	private final List<String> odFlowsFiles = new ArrayList<>();
	private final List<String> shpFiles = new ArrayList<>();
	private final List<String> shpFilesDbf = new ArrayList<>();

	public GunmaAggrODDashboard(String crs) {
		this.crs = crs;

	}

	public final GunmaAggrODDashboard addTab(String title, String odFlowsFile, String shpFile) {
		titles.add(title);
		odFlowsFiles.add(odFlowsFile);
		shpFiles.add(shpFile);
		shpFilesDbf.add(shpFile.replace(".shp", ".dbf"));

		return this;
	}

	@Override
	public void configure(Header header, Layout layout, SimWrapperConfigGroup configGroup) {

		header.title = "OD Flows for Work Trips";
		header.tab = "OD Work";
		header.description = "Origin-Destination Flows for Work Trips (Clipped to 50/100 km envelope around Gunma)";
		header.fullScreen = false;


		for (int i = 0; i < titles.size(); i++) {
			createTab(layout, titles.get(i), shpFiles.get(i), shpFilesDbf.get(i), odFlowsFiles.get(i));
		}

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
