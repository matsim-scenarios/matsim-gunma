package org.matsim.run;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PermissibleModesCalculatorImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.run.scoring.experimental.AdvancedScoringConfigGroup;
import org.matsim.run.scoring.experimental.IndividualPersonScoringParameters;
import picocli.CommandLine;

/** Small regression fixtures; main also permits validation against the bundled runtime. */
public class V19CalibrationTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Test
    public void configurationAndAgeScoring() {
        GunmaBaseScenario app = new GunmaBaseScenario();
        Config config = ConfigUtils.loadConfig(GunmaDefaults.configPath(), new AdvancedScoringConfigGroup());
        config.controller().setLastIteration(123);
        config.scoring().getModes().get("car").setConstant(-3.25);
        app.prepareConfig(config);
        check(config.controller().getLastIteration() == 123, "Configured iterations overwritten");
        check(config.scoring().getModes().get("car").getConstant() == -3.25, "Configured ASC overwritten");
        new CommandLine(app).parseArgs("--1pct");
        app.prepareConfig(config);
        check(config.qsim().getFlowCapFactor() == .01, "Sample capacity");
        check(config.plans().getInputFile().contains("v1.8-1pct-plans"), "Reused sample plans");
        int[] ages = {17, 18, 29, 30, 44, 45, 64, 65, 74, 75, 84, 85};
        String[] groups = {"0-17", "18-29", "18-29", "30-44", "30-44", "45-64",
            "45-64", "65-74", "65-74", "75-84", "75-84", "85+"};
        for (int i = 0; i < ages.length; i++) check(GunmaBaseScenario.ageGroup(ages[i]).equals(groups[i]), "Age boundary " + ages[i]);

        AdvancedScoringConfigGroup advanced = ConfigUtils.addOrGetModule(config, AdvancedScoringConfigGroup.class);
        advanced.incomeDependent = AdvancedScoringConfigGroup.IncomeDependentScoring.none;
        AdvancedScoringConfigGroup.ScoringParameters group = new AdvancedScoringConfigGroup.ScoringParameters();
        group.addParam("age_group", "75-84");
        AdvancedScoringConfigGroup.ModeParams mode = new AdvancedScoringConfigGroup.ModeParams(false);
        mode.mode = "car";
        mode.deltaConstant = 1.5;
        group.addParameterSet(mode);
        advanced.addParameterSet(group);
        Scenario scenario = ScenarioUtils.createScenario(config);
        Person person = person(scenario, "gunma_scoring", 75);
        person.getAttributes().putAttribute("age_group", "75-84");
        double constant = new IndividualPersonScoringParameters(scenario).getScoringParameters(person).modeParams.get("car").constant;
        check(Math.abs(constant - (-3.25 + 1.5)) < 1e-9, "Age delta not added to overall ASC");
    }

    private static Person person(Scenario scenario, String id, Integer age) {
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId(id));
        if (age != null) PersonUtils.setAge(person, age);
        PopulationUtils.putSubpopulation(person, "person");
        PersonUtils.setCarAvail(person, "always");
        PersonUtils.setLicence(person, "yes");
        Plan plan = PopulationUtils.createPlan();
        plan.addActivity(PopulationUtils.createActivityFromCoord("home", new org.matsim.api.core.v01.Coord(0, 0)));
        plan.addLeg(PopulationUtils.createLeg("car"));
        plan.addActivity(PopulationUtils.createActivityFromCoord("work", new org.matsim.api.core.v01.Coord(1, 1)));
        person.addPlan(plan);
        Plan alternative = PopulationUtils.createPlan();
        PopulationUtils.copyFromTo(plan, alternative);
        person.addPlan(alternative);
        scenario.getPopulation().addPerson(person);
        return person;
    }

    @Test
    public void baselineAndPolicy() {
        for (String policy : new String[]{"noCarAvailOver75base", "noCarAvailOver75policy"}) {
            GunmaPolicyScenario app = new GunmaPolicyScenario();
            new CommandLine(app).parseArgs("--policy-case", policy);
            Config config = ConfigUtils.createConfig();
            config.subtourModeChoice().setConsiderCarAvailability(true);
            config.subtourModeChoice().setModes(new String[]{"car", "walk", "ride", "bike"});
            Scenario scenario = ScenarioUtils.createScenario(config);
            Person younger = person(scenario, "gunma_74", 74);
            Person older = person(scenario, "gunma_75", 75);
            Person oldest = person(scenario, "gunma_85", 85);
            Person commuter = person(scenario, "commuter_85", 85);
            Person missing = person(scenario, "gunma_missing", null);
            // No network is necessary for the common setup on this fixture.
            app.prepareScenario(scenario);
            boolean restricted = policy.endsWith("policy");
            for (Person p : new Person[]{older, oldest}) {
                check(PersonUtils.getCarAvail(p).equals(restricted ? "never" : "always"), "Driving access");
                check(p.getPlans().size() == (restricted ? 1 : 2), "Retained plans");
                check(PopulationUtils.getLegs(p.getSelectedPlan()).getFirst().getMode().equals(restricted ? "walk" : "car"), "Initial car trip");
                var modes = new PermissibleModesCalculatorImpl(config).getPermissibleModes(p.getSelectedPlan());
                check(modes.contains("car") != restricted, "Replanning driving access");
                check(modes.contains("ride"), "Passenger travel removed");
            }
            for (Person p : new Person[]{younger, commuter, missing}) {
                check(PersonUtils.getCarAvail(p).equals("always"), "Unaffected person access changed");
            }
        }
    }

    public static void main(String[] args) {
        V19CalibrationTest test = new V19CalibrationTest();
        test.configurationAndAgeScoring();
        test.baselineAndPolicy();
        System.out.println("v1.9 Java regression fixtures passed");
    }
}
