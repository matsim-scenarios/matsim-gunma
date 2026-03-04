package org.matsim.run;

import org.matsim.application.MATSimApplication;


/**
 * Wrapper for OpenGunmaScenario.
 */
public final class RunOpenGunmaScenario extends MATSimApplication {

	private RunOpenGunmaScenario(){
	}

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(OpenGunmaScenario.class, args);
	}

}
