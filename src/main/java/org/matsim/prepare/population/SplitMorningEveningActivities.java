package org.matsim.prepare.population;


import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.run.Activities;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
	name = "split-morning-evening-acts",
	description = "Splits Morning and Evening Activities"
)


public class SplitMorningEveningActivities implements MATSimAppCommand {

	@CommandLine.Option(names = "--input", description = "Path to input population", required = true)
	private Path input;

	@CommandLine.Option(names = "--output", description = "Output population", required = true)
	private Path output;


	public static void main(String[] args) {
		new SplitMorningEveningActivities().execute(args);
	}


	@Override
	public Integer call() throws Exception {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		new PopulationReader(scenario).readFile(input.toString());

		PopulationFactory factory = scenario.getPopulation().getFactory();
		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (person.getSelectedPlan().getPlanElements().isEmpty()) {

				Coord homeLoc = new Coord((double) person.getAttributes().getAttribute(Attributes.HOME_X), (double) person.getAttributes().getAttribute(Attributes.HOME_Y));
				Activity homeAct = factory.createActivityFromCoord(Activities.home.name(), homeLoc);
				person.getSelectedPlan().addActivity(homeAct);
			}
		}
		org.matsim.contrib.vsp.scenario.Activities.changeWrapAroundActsIntoMorningAndEveningActs(scenario);

		new PopulationWriter(scenario.getPopulation()).write(output.toString());

		return 0;
	}
}
