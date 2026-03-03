package org.matsim.prepare.population;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
	name = "amend-start-time-commuters",
	description = "Replace undefined start times"
)
public class AmendStartTimeCommuters implements MATSimAppCommand{

	private static final Logger log = LogManager.getLogger(AmendStartTimeCommuters.class);

	@CommandLine.Option(names = "--input", description = "Path to input population.", required = true)
	private Path input;

	@CommandLine.Option(names = "--output", description = "Path to output population", required = true)
	private Path output;

	public static void main(String[] args) {
		new AmendStartTimeCommuters().execute(args);
	}


	@Override
	public Integer call() throws Exception {

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile(input.toString());

		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (person.getId().toString().startsWith("commuter_")) {

				// skip first activity, since we don't need starting time here...

//				Plan plan = person.getSelectedPlan();
//				for (Activity activity : TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities)) {
//					if (activity.getStartTime().isUndefined()) {
//						TripStructureUtils.Trip prevTrip = TripStructureUtils.findTripEndingAtActivity(activity, plan).get;
//					}
//				}

				for (int i = 1; i < person.getSelectedPlan().getPlanElements().size(); i++) {
					PlanElement planElement = person.getSelectedPlan().getPlanElements().get(i);


					if (planElement instanceof Activity) {

						Activity activity = (Activity) planElement;

						if (!TripStructureUtils.isStageActivityType(activity.getType()) && activity.getStartTime().isUndefined()) {
							Leg prevLeg = (Leg) person.getSelectedPlan().getPlanElements().get(i - 1);
							double startTime = prevLeg.getDepartureTime().seconds() + prevLeg.getTravelTime().seconds();
							((Activity) planElement).setStartTime(startTime);
						}
					}
				}
			}
		}

		new PopulationWriter(scenario.getPopulation()).write(output.toString());

		return 0;
	}
}
