package org.matsim.run;

import jakarta.annotation.Nullable;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.application.MATSimApplication;
import org.matsim.application.analysis.CheckPopulation;
import org.matsim.application.analysis.traffic.LinkStats;
import org.matsim.application.options.SampleOptions;
import org.matsim.application.prepare.CreateLandUseShp;
import org.matsim.application.prepare.network.CreateNetworkFromSumo;
import org.matsim.application.prepare.population.*;
import org.matsim.application.prepare.pt.CreateTransitScheduleFromGtfs;
import org.matsim.contrib.vsp.scoring.RideScoringParamsFromCarParams;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.*;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.SimWrapperModule;
import picocli.CommandLine;

import java.util.*;

@CommandLine.Command(header = ":: Open Gunma Scenario ::", version = RunGunmaScenario.VERSION, mixinStandardHelpOptions = true)
@MATSimApplication.Prepare({
		CreateNetworkFromSumo.class, CreateTransitScheduleFromGtfs.class, TrajectoryToPlans.class, GenerateShortDistanceTrips.class,
		MergePopulations.class, DownSamplePopulation.class, ExtractHomeCoordinates.class,
		CreateLandUseShp.class, ResolveGridCoordinates.class, FixSubtourModes.class, AdjustActivityToLinkDistances.class, XYToLinks.class
})
@MATSimApplication.Analysis({
		LinkStats.class, CheckPopulation.class
})
public class RunGunmaScenario extends MATSimApplication {

	static final String VERSION = "1.4";

	@CommandLine.Mixin
	private final SampleOptions sample = new SampleOptions(25, 10, 1);

	@CommandLine.Option(names = "--plan-selector",
		description = "Plan selector to use.",
		defaultValue = DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta)
	private String planSelector;


	private final boolean removePt = true;


	public RunGunmaScenario(@Nullable Config config) {
		super(config);
	}

	public RunGunmaScenario() {
		super(ConfigUtils.loadConfig("input/v" + OpenGunmaScenario.VERSION + "/gunma-v" + OpenGunmaScenario.VERSION + "-config.xml"));
	}

	public static void main(String[] args) {
		MATSimApplication.run(RunGunmaScenario.class, args);
	}

	@Nullable
	@Override
	protected Config prepareConfig(Config config) {

		// Add all activity types with time bins


		for (long ii = 600; ii <= 97200; ii += 600) {

			for (String act : List.of(Activities.home.name(), Activities.other.name(), Activities.education.name())) {
				config.scoring()
					.addActivityParams(new ScoringConfigGroup.ActivityParams(act + "_" + ii).setTypicalDuration(ii));
			}

			config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("work_" + ii).setTypicalDuration(ii)
					.setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
//			config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("business_" + ii).setTypicalDuration(ii)
//					.setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
//			config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("leisure_" + ii).setTypicalDuration(ii)
//					.setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));
//
//			config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("shop_daily_" + ii).setTypicalDuration(ii)
//					.setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
//			config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("shop_other_" + ii).setTypicalDuration(ii)
//					.setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
		}

		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("car interaction").setTypicalDuration(60));
		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("other").setTypicalDuration(600 * 3));

//		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("freight_start").setTypicalDuration(60 * 15));
//		config.scoring().addActivityParams(new ScoringConfigGroup.ActivityParams("freight_end").setTypicalDuration(60 * 15));

		config.controller().setOutputDirectory(sample.adjustName(config.controller().getOutputDirectory()));
		config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));
		config.controller().setRunId(sample.adjustName(config.controller().getRunId()));

		config.qsim().setFlowCapFactor(sample.getSize() / 100.0);
		config.qsim().setStorageCapFactor(sample.getSize() / 100.0);

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);
		config.routing().setAccessEgressType(RoutingConfigGroup.AccessEgressType.accessEgressModeToLink);

		// TODO: Config options
		config.controller().setLastIteration(10);

		// consistency checker
		config.global().setInsistingOnDeprecatedConfigVersion(false);
		config.facilities().setInputFile("gunma-v" + OpenGunmaScenario.VERSION + "-facilities.xml");
		config.facilities().setFacilitiesSource(FacilitiesConfigGroup.FacilitiesSource.fromFile);

		config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);
		config.vehicles().setVehiclesFile("gunma-v" + OpenGunmaScenario.VERSION + "-vehicleTypes.xml");

		config.qsim().setTrafficDynamics(QSimConfigGroup.TrafficDynamics.kinematicWaves);
		config.qsim().setUsingTravelTimeCheckInTeleportation(true);
		config.qsim().setUsePersonIdForMissingVehicleId(false);

		config.replanning().setFractionOfIterationsToDisableInnovation(0.8);

		config.timeAllocationMutator().setAffectingDuration(false);

		config.plans().setRemovingUnneccessaryPlanAttributes(true);


		Map<String, ScoringConfigGroup.ModeParams> modes = config.scoring().getScoringParameters(null).getModes();

		for (ScoringConfigGroup.ModeParams modeParams : modes.values()) {

			config.scoring().getScoringParameters(null).removeParameterSet(modeParams);
		}


		// SCORING
		// 0.8 recommended by VSP consistency checker
		config.scoring().setFractionOfIterationsToStartScoreMSA(0.8);

		//from Luo (added 0.174 to all ASCs so that walk's ASC is brought to zero (see VSP consistency checker))
		config.scoring().setMarginalUtilityOfMoney(0.00555);
		config.scoring().setPerforming_utils_hr(3.102);
		config.scoring().setLateArrival_utils_hr(-9.306);
		config.scoring().setEarlyDeparture_utils_hr(0.0);
		config.scoring().setMarginalUtlOfWaiting_utils_hr(0.0);

		// car


//		config.scoring().getScoringParametersPerSubpopulation()
		ScoringConfigGroup.ModeParams carParams = config.scoring().getOrCreateModeParams(TransportMode.car);
		carParams.setConstant(0.174);
		carParams.setMarginalUtilityOfTraveling(0.0);
		carParams.setMarginalUtilityOfDistance(0.0);
		carParams.setMonetaryDistanceRate(-0.0068);

		config.scoring().addModeParams(carParams);

		// bike
		ScoringConfigGroup.ModeParams bikeParams = config.scoring().getOrCreateModeParams(TransportMode.bike);
		bikeParams.setConstant(-1.795);
		bikeParams.setMarginalUtilityOfTraveling(-0.708);
		bikeParams.setMarginalUtilityOfDistance(0.0);
		bikeParams.setMonetaryDistanceRate(0.0);

		config.scoring().addModeParams(bikeParams);

		// walk
		ScoringConfigGroup.ModeParams walkParams = config.scoring().getOrCreateModeParams(TransportMode.walk);
		walkParams.setConstant(0.0);
		walkParams.setMarginalUtilityOfTraveling(-5.338);
		walkParams.setMarginalUtilityOfDistance(0.0);
		walkParams.setMonetaryDistanceRate(0.0);

		config.scoring().addModeParams(walkParams);

		// ride

		RideScoringParamsFromCarParams.setRideScoringParamsBasedOnCarParams(config.scoring(), 1.0);



		// experienced plans somehow throw out the home-only plans of non-mobile agents
		config.plans().setInputFile("gunma-v" + OpenGunmaScenario.VERSION + "-1pct.plans-initial.xml.gz");
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);


		for (String subpopulation : List.of("person", "commuter2gunma")) {
			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(planSelector)
					.setWeight(1.0)
					.setSubpopulation(subpopulation)
			);

			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute)
					.setWeight(0.15)
					.setSubpopulation(subpopulation)
			);

			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator)
					.setWeight(0.15)
					.setSubpopulation(subpopulation)
			);
			config.replanning().addStrategySettings(
				new ReplanningConfigGroup.StrategySettings()
					.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice)
					.setWeight(0.15)
					.setSubpopulation(subpopulation)
			);
		}

		if (removePt){

			config.routing().removeTeleportedModeParams(TransportMode.pt);
			config.changeMode().setModes(List.of(TransportMode.car).toArray(new String[0]));

			config.scoring().removeParameterSet(config.scoring().getActivityParams("pt interaction"));
			config.subtourModeChoice().setModes(List.of(TransportMode.car, TransportMode.walk, TransportMode.bike).toArray(new String[0]));

		}

		// Simwrapper
		SimWrapperConfigGroup simWrapperConfigGroup = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		simWrapperConfigGroup.setSampleSize(sample.getSample());

//		ConfigUtils.writeConfig(config, "confixxxx.xml");

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {

		if (removePt) {

			Set<Id<Person>> ptPersons = new HashSet<>();
			outer:
			for (Person person : scenario.getPopulation().getPersons().values()) {
				for (Leg leg : TripStructureUtils.getLegs(person.getSelectedPlan())) {

					if (leg.getMode().equals(TransportMode.pt)) {
						ptPersons.add(person.getId());
						continue outer;
					}
				}
			}

			for (Id<Person> personId : ptPersons) {

				scenario.getPopulation().getPersons().remove(personId);
			}
		}

	}

	@Override
	protected void prepareControler(Controler controler) {

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {



			}
		});

		controler.addOverridingModule(new SimWrapperModule());

	}
}
