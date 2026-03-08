package org.matsim.prepare.facilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.*;
import org.matsim.run.Activities;
import picocli.CommandLine;
import tech.tablesaw.api.*;

import java.nio.file.Path;
import java.util.*;

@CommandLine.Command(
	name = "facilities-filter",
	description = "Filter MATSim facilities according to facilities visited in plans file"
)
public class FacilitiesFilter implements MATSimAppCommand {


	private static final Logger log = LogManager.getLogger(FacilitiesFilter.class);
	@CommandLine.Option(names = "--input", required = true, description = "Path to input facility file")
	private Path input;

	@CommandLine.Option(names = "--plans", required = true, description = "Path to plans file")
	private Path plans;


	@CommandLine.Option(names = "--output", required = true, description = "Path to output facility file")
	private Path output;


	static void main(String[] args) {
		new FacilitiesFilter().execute(args);
	}

	@Override
	public Integer call() throws Exception {


		// read population
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile(plans.toString());

		// Collect all activity facilities used in population
		Set<Id<ActivityFacility>> relevantFacilities = new HashSet<>();
		for (Person person : scenario.getPopulation().getPersons().values()) {
			List<Activity> activities = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities);
			for (Activity activity : activities) {
				if (!activity.getType().equals(Activities.home.name())) {
					relevantFacilities.add(activity.getFacilityId());
				}
			}
		}

		// Read full facilities file
		new MatsimFacilitiesReader(scenario).readFile(input.toString());
		ActivityFacilities facilitiesFull = scenario.getActivityFacilities();
		ActivityFacilities facilitiesFiltered = FacilitiesUtils.createActivityFacilities();

		for (Id<ActivityFacility> facilityId : relevantFacilities) {
			ActivityFacility facility = facilitiesFull.getFacilities().get(facilityId);
			if (facility != null) {
				facilitiesFiltered.addActivityFacility(facility);

			}
		}


		log.info("Filtered {} facilities out of {}, writing to {}", facilitiesFiltered.getFacilities().size(), facilitiesFull.getFacilities().size(), output);

		FacilitiesWriter writer = new FacilitiesWriter(facilitiesFiltered);
		writer.write(output.toString());

		return 0;
	}



}
