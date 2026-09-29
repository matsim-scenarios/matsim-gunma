package org.matsim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Takes a plans file and filters Population by age, producing a new plans file.
 */


public final class FilterPopulationByAge {

	private FilterPopulationByAge() {
		// do nothing
	}
	static void main() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		new PopulationReader(scenario).readFile("input/v1.8/gunma-v1.8-100pct-plans.xml.gz");

		Set<Id<Person>> personsToRemove = new HashSet<>();
		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (person.getAttributes().getAttribute("age") == null ||
				(int) person.getAttributes().getAttribute("age") < 85){

//					person.getAttributes().getAttribute("zone") != "10383") consider including 10382

				personsToRemove.add(person.getId());
			}

//				if(!String.valueOf(person.getAttributes().getAttribute("zone")).equals("10383")){
//					personsToRemove.add(person.getId());
//				}
		}

		for (Id<Person> personId : personsToRemove) {
			scenario.getPopulation().getPersons().remove(personId);
		}


		new PopulationWriter(scenario.getPopulation()).write("input/v1.8/gunma-v1.8-100pct-plans-filtered85.xml.gz");



	}
}
