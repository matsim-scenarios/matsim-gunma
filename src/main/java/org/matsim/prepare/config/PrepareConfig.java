package org.matsim.prepare.config;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.vsp.scoring.RideScoringParamsFromCarParams;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.*;
import org.matsim.run.OpenGunmaScenario;

import java.util.List;
import java.util.Map;



/**
 * Create Base Config for Open Gunma Scenario.
 */
public final class PrepareConfig {

	private PrepareConfig(){
		throw new UnsupportedOperationException("Utility class");
	}

	static void main() {
		Config config = ConfigUtils.createConfig();

		// ############
		// global
		config.global().setCoordinateSystem(OpenGunmaScenario.CRS);
		config.global().setInsistingOnDeprecatedConfigVersion(false);
		config.global().setNumberOfThreads(16);

		// ############
		// transit
		config.transit().setUseTransit(false);

		// ############
		// controller
		config.controller().setOutputDirectory("output");
		config.controller().setCompressionType(ControllerConfigGroup.CompressionType.gzip);
		config.controller().setRunId("gunma");

		// ############
		// network
		config.network().setInputFile("gunma-v" + OpenGunmaScenario.VERSION + "-network.xml.gz");

		// ############
		// facilities
		config.facilities().setInputFile("gunma-v" + OpenGunmaScenario.VERSION + "-100pct-facilities.xml.gz");
		config.facilities().setFacilitiesSource(FacilitiesConfigGroup.FacilitiesSource.fromFile);

		// ############
		//plans
		config.plans().setRemovingUnneccessaryPlanAttributes(true);
		config.plans().setInputFile("gunma-v" + OpenGunmaScenario.VERSION + "-100pct-plans.xml.gz");


		// ############
		// vehicle type
		config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);
		config.vehicles().setVehiclesFile("gunma-v" + OpenGunmaScenario.VERSION + "-vehicleTypes.xml");

		// ############
		// qsim
		config.qsim().setTrafficDynamics(QSimConfigGroup.TrafficDynamics.kinematicWaves);
		config.qsim().setUsingTravelTimeCheckInTeleportation(true);
		config.qsim().setUsePersonIdForMissingVehicleId(false);

		// ############
		// replanning
		config.replanning().setFractionOfIterationsToDisableInnovation(0.8);

		// ############
		// time mutation
		config.timeAllocationMutator().setAffectingDuration(false);
		config.timeAllocationMutator().setMutateAroundInitialEndTimeOnly(false);
		//  kn: "irgendetwas großes.  Mindestens qsim.endTime" --> 3 days
		config.timeAllocationMutator().setLatestActivityEndTime(3 * 24 * 60 * 60.);



		// ############
		// replanning
		config.subtourModeChoice().setModes(List.of(TransportMode.car, TransportMode.walk, TransportMode.bike, TransportMode.ride).toArray(new String[0]));
		config.subtourModeChoice().setConsiderCarAvailability(true);
		config.changeMode().setModes(List.of(TransportMode.car).toArray(new String[0]));


		// ############
		// routing
		config.routing().setAccessEgressType(RoutingConfigGroup.AccessEgressType.accessEgressModeToLink);
		config.routing().removeTeleportedModeParams(TransportMode.pt);

		// ############
		// scoring

		config.scoring().removeParameterSet(config.scoring().getActivityParams("pt interaction"));


		// 0.8 recommended by VSP consistency checker
		config.scoring().setFractionOfIterationsToStartScoreMSA(0.8);

		// following params are from Luo et al.
		config.scoring().setMarginalUtilityOfMoney(0.00555);
		config.scoring().setPerforming_utils_hr(3.102);
		config.scoring().setLateArrival_utils_hr(-9.306);
		config.scoring().setEarlyDeparture_utils_hr(0.0);
		config.scoring().setMarginalUtlOfWaiting_utils_hr(0.0);
		config.scoring().setMarginalUtlOfWaitingPt_utils_hr(0.0);


		// delete default mode configurations
		Map<String, ScoringConfigGroup.ModeParams> modes = config.scoring().getScoringParameters(null).getModes();
		for (ScoringConfigGroup.ModeParams modeParams : modes.values()) {
			config.scoring().getScoringParameters(null).removeParameterSet(modeParams);
		}

		// Mode Params from Luo et al. (added 0.174 to all ASCs so that walk's ASC is brought to zero)

		// car
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

		// write config
		ConfigUtils.writeConfig(config, "input/v" + OpenGunmaScenario.VERSION + "/gunma-v" + OpenGunmaScenario.VERSION + "-config.xml");

	}

}
