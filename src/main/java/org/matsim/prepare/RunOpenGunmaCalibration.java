package org.matsim.prepare;

/**
 * Deprecated compatibility wrapper. Use {@link OpenGunmaPreparation}.
 */
@Deprecated
public final class RunOpenGunmaCalibration {

	private RunOpenGunmaCalibration() {
	}

	public static void main(String[] args) {
		OpenGunmaPreparation.main(args);
	}
}
