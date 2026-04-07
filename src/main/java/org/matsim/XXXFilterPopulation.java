//package org.matsim;
//
//import org.matsim.api.core.v01.Id;
//import org.matsim.api.core.v01.Scenario;
//import org.matsim.api.core.v01.population.Person;
//import org.matsim.api.core.v01.population.PopulationWriter;
//import org.matsim.core.config.ConfigUtils;
//import org.matsim.core.population.io.PopulationReader;
//import org.matsim.core.scenario.ScenarioUtils;
//import org.matsim.facilities.MatsimFacilitiesReader;
//
//import java.util.HashSet;
//import java.util.Set;
//
//public class XXXFilterPopulation {
//	static void main() {
//		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
//
//		new PopulationReader(scenario).readFile("/Users/jakob/gunma-v1.6-100pct-plans.xml.gz");
//
//		Set<Id<Person>> personsToRemove = new HashSet<>();
//		for (Person person : scenario.getPopulation().getPersons().values()) {
//			if (person.getAttributes().getAttribute("age") == null ||
//				(int) person.getAttributes().getAttribute("age") < 85){
//
////					person.getAttributes().getAttribute("zone") != "10383") consider including 10382
//
//				personsToRemove.add(person.getId());
//			}
//
////				if(!String.valueOf(person.getAttributes().getAttribute("zone")).equals("10383")){
////					personsToRemove.add(person.getId());
////				}
//		}
//
//		for (Id<Person> personId : personsToRemove) {
//			scenario.getPopulation().getPersons().remove(personId);
//		}
//
//
//		new PopulationWriter(scenario.getPopulation()).write("/Users/jakob/gunma-v1.6-100pct-plans-filtered85.xml.gz");
//
//
//
//	}
//}
