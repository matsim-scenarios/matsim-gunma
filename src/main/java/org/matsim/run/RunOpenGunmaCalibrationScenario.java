package org.matsim.run;

import org.matsim.application.MATSimApplication;

/**
 * Wrapper for OpenGunmaCalibrationScenario.
 */
public final class RunOpenGunmaCalibrationScenario extends MATSimApplication {

	private RunOpenGunmaCalibrationScenario() {
	}

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(OpenGunmaCalibrationScenario.class, args);
	}
}
