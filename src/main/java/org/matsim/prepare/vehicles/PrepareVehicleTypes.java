package org.matsim.prepare.vehicles;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
	name = "prepare-vehicle-types",
	description = "Generate Vehicles File"
)
public class PrepareVehicleTypes implements MATSimAppCommand {
	@CommandLine.Option(names = "--output", description = "Path to output vehicles file", required = true)
	private Path output;

	public static void main(String[] args) {
		new PrepareVehicleTypes().execute(args);
	}



	@Override
	public Integer call() throws Exception {

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		VehicleType car = VehicleUtils.createVehicleType(Id.createVehicleTypeId("car"), "car");
		car.setLength(7.5);
		car.setWidth(1.0);
		car.setPcuEquivalents(1.0);

		VehicleType ride = VehicleUtils.createVehicleType(Id.createVehicleTypeId("ride"), "car");
		ride.setLength(7.5);
		ride.setWidth(1.0);
		ride.setPcuEquivalents(1.0);

		VehicleType bike = VehicleUtils.createVehicleType(Id.createVehicleTypeId("bike"), "bike");
		bike.setLength(2.0);
		bike.setWidth(1.0);
		bike.setPcuEquivalents(0.2);
		// from Berlin
		bike.setMaximumVelocity(2.98);


		scenario.getVehicles().addVehicleType(car);
		scenario.getVehicles().addVehicleType(ride);
		scenario.getVehicles().addVehicleType(bike);

		new MatsimVehicleWriter(scenario.getVehicles()).writeFile(output.toString());


		return 0;
	}
}
