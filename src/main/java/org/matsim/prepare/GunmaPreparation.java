//package org.matsim.prepare;
//
//import org.matsim.application.MATSimApplication;
//import org.matsim.application.prepare.CreateLandUseShp;
//import org.matsim.application.prepare.network.CleanNetwork;
//import org.matsim.application.prepare.network.CreateNetworkFromSumo;
//import org.matsim.application.prepare.population.DownSamplePopulation;
//import org.matsim.application.prepare.population.MergePopulations;
//import org.matsim.application.prepare.population.SplitActivityTypesDuration;
//import org.matsim.application.prepare.pt.CreateTransitScheduleFromGtfs;
//import org.matsim.core.config.ConfigUtils;
//import org.matsim.dashboard.GunmaSimwrapperRunner;
//import org.matsim.prepare.counts.CreateCountsFromJarticData;
//import org.matsim.prepare.counts.CreateCountsFromMlitData;
//import org.matsim.prepare.facilities.CreateMATSimFacilitiesGunma;
//import org.matsim.prepare.facilities.FacilitiesFilter;
//import org.matsim.prepare.opt.RunCountOptimization;
//import org.matsim.prepare.opt.SelectPlansFromIndex;
//import org.matsim.prepare.population.AmendStartTimeCommuters;
//import org.matsim.prepare.population.CreateGunmaCommuterPopulation;
//import org.matsim.prepare.population.CreateGunmaPopulation;
//import org.matsim.prepare.population.InitLocationChoice;
//import org.matsim.prepare.population.LookupJisZone;
//import org.matsim.prepare.population.RunActivitySampling;
//import org.matsim.prepare.population.SplitMorningEveningActivities;
//import org.matsim.prepare.vehicles.PrepareVehicleTypes;
//import org.matsim.run.GunmaDefaults;
//import picocli.CommandLine;
//
//@CommandLine.Command(header = ":: Gunma Preparation ::", mixinStandardHelpOptions = true)
//@MATSimApplication.Prepare({
//	GunmaSimwrapperRunner.class,
//	CreateLandUseShp.class,
//	CreateGunmaPopulation.class,
//	CreateGunmaCommuterPopulation.class,
//	MergePopulations.class,
//	DownSamplePopulation.class,
//	CreateNetworkFromSumo.class,
//	CreateTransitScheduleFromGtfs.class,
//	CleanNetwork.class,
//	RunActivitySampling.class,
//	InitLocationChoice.class,
//	CreateMATSimFacilitiesGunma.class,
//	FacilitiesFilter.class,
//	LookupJisZone.class,
//	CreateCountsFromMlitData.class,
//	CreateCountsFromJarticData.class,
//	RunCountOptimization.class,
//	SelectPlansFromIndex.class,
//	SplitActivityTypesDuration.class,
//	AmendStartTimeCommuters.class,
//	PrepareVehicleTypes.class,
//	SplitMorningEveningActivities.class
//})
//public class GunmaPreparation extends MATSimApplication {
//
//	public GunmaPreparation() {
//		super(ConfigUtils.loadConfig(GunmaDefaults.configPath()));
//	}
//
//	public static void main(String[] args) {
//		MATSimApplication.run(GunmaPreparation.class, args);
//	}
//}
