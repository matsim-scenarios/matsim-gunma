package org.matsim.run;

import org.matsim.application.MATSimApplication;

/**
 * Wrapper for OpenGunmaPolicyScenario.
 */
public final class RunOpenGunmaPolicyScenario extends MATSimApplication {

	private RunOpenGunmaPolicyScenario() {
	}

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(OpenGunmaPolicyScenario.class, args);
	}
}
