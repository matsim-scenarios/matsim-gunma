package org.matsim.accessibility;

import org.apache.commons.io.FileUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.application.ApplicationUtils;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.AccessibilityFromEvents;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.FacilitiesConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.dashboard.AccessibilityDashboardGunma;
import org.matsim.run.OpenGunmaScenario;
import org.matsim.simwrapper.SimWrapper;
import org.matsim.simwrapper.SimWrapperConfigGroup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs offline accessibility analysis for Gunma Prefecture in Japan.
 */
public final class RunGunmaAccessibility {

	static final String coordinateSystem = "EPSG:2450";
	static final String dirToCopy = "../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.6/2026-03-13-base/";

	static final String outputDir = "../public-svn/matsim/scenarios/countries/jp/gunma/gunma-v1.6/2026-03-17b-base-accessibility-supermarket/";
	//	static final List<String> relevantPois = List.of("supermarket", "public_bath", "hospital", "shinkansen", "middle");
	static final List<String> relevantPois = List.of("supermarket", "shinkansen");

	private static final boolean personBased = true;

	private RunGunmaAccessibility() {
		// prevent instantiation
	}


	static void main(String[] args) throws IOException {

		FileUtils.copyDirectory(new File(dirToCopy), new File(outputDir));

		String mapCenterString = "139.0266,36.5211";

		calculateAccessibility();


		// CREATE DASHBOARD
//		createDashboard(mapCenterString);
	}

	private static void calculateAccessibility() {
		AccessibilityConfigGroup accConfig = new AccessibilityConfigGroup();

		if (personBased) {

			accConfig.setPersonBased(true);
			accConfig.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromPopulation);


		} else {
			accConfig.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromShapeFile);

			accConfig.setShapeFileCellBasedAccessibility("../shared-svn/projects/matsim-gunma/data/raw/01_shapefiles/gunma_2450/gunma_2450.shp");
			// extents of gunma from qgis -- this really shouldn't be neccessary if we use shp file...
			accConfig.setBoundingBoxLeft(-8874.3893231801757793);
			accConfig.setBoundingBoxBottom(104516.0981667207088321);
			accConfig.setBoundingBoxRight(-1513.2262783532476078);
			accConfig.setBoundingBoxTop(117287.2301061319012661);

		}

		accConfig.setTileSize_m(500);



		accConfig.setTimeOfDay(8 * 3600.);

		List<Modes4Accessibility> accModes = List.of(Modes4Accessibility.car, Modes4Accessibility.teleportedWalk);
		for (Modes4Accessibility mode : Modes4Accessibility.values()) {
			accConfig.setComputingAccessibilityForMode(mode, accModes.contains(mode));
		}

		// Accessibility Calculation
		String eventsFile = ApplicationUtils.matchInput("output_events.xml.gz", Path.of(outputDir)).toString();
		String populationFile = ApplicationUtils.matchInput("output_plans.xml.gz", Path.of(outputDir)).toString();

		String networkFile = "input/v" + OpenGunmaScenario.VERSION + "/gunma-v" + OpenGunmaScenario.VERSION + "-network.xml.gz";

		String poisFile = "../shared-svn/projects/matsim-gunma/data/processed/01_shapefiles/osm_buffer5km/pois.xml";
		final Config config = ConfigUtils.createConfig();

		config.plans().setInputFile(populationFile);
		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(outputDir);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		config.routing().setRoutingRandomness(0.);
		config.facilities().setInputFile(poisFile);

		config.global().setCoordinateSystem("EPSG:2450");

		config.network().setInputFile(networkFile);
		config.addModule(accConfig);

		MutableScenario scenario = (MutableScenario) ScenarioUtils.loadScenario(config);

		//		== Bounding box from persons home coords ==
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;

		for (Person person : scenario.getPopulation().getPersons().values()) {
			Double x = (Double) person.getAttributes().getAttribute("home_x");
			Double y = (Double) person.getAttributes().getAttribute("home_y");
			if (x != null && y != null) {
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
		}
		accConfig.setBoundingBoxBottom(minY).setBoundingBoxTop(maxY)
			.setBoundingBoxLeft(minX).setBoundingBoxRight(maxX);


		Set<Id<Person>> commutersIds = scenario.getPopulation().getPersons().keySet().stream().filter(id -> id.toString().startsWith("commuter_")).collect(Collectors.toSet());
		commutersIds.forEach(id -> scenario.getPopulation().removePerson(id));


		AccessibilityFromEvents.Builder builder = new AccessibilityFromEvents.Builder(scenario, eventsFile, relevantPois);
		builder.build().run();
	}

	private static void createDashboard(String mapCenterString) {

		final Config config = ConfigUtils.createConfig();
		config.controller().setOutputDirectory(outputDir);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);


		//CONFIG
		config.controller().setLastIteration(0);
		config.controller().setWritePlansInterval(-1);
		config.controller().setWriteEventsInterval(-1);
		config.global().setCoordinateSystem(coordinateSystem);



		config.facilities().setFacilitiesSource(FacilitiesConfigGroup.FacilitiesSource.none);

		//simwrapper
		SimWrapperConfigGroup group = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		group.setSampleSize(0.001);
		if (mapCenterString != null) {
			group.defaultParams().setMapCenter(mapCenterString);
		}
		group.setDefaultDashboards(SimWrapperConfigGroup.DefaultDashboardsMode.disabled);


		SimWrapper sw = SimWrapper.create(config)
			.addDashboard(new AccessibilityDashboardGunma(coordinateSystem, relevantPois, List.of(Modes4Accessibility.car)));

		boolean append = true;
		try {
			sw.generate(Path.of(outputDir), append);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		sw.run(Path.of(outputDir));

	}
}
