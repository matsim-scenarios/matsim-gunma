package org.matsim.run;

/**
 * Shared scenario metadata and canonical file names.
 */
public final class OpenGunmaDefaults {

	public static final String VERSION = "1.7";
	public static final String CRS = "EPSG:2450";

	private OpenGunmaDefaults() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static String configPath() {
		return "input/v" + VERSION + "/gunma-v" + VERSION + "-config.xml";
	}

	public static String networkFile() {
		return "gunma-v" + VERSION + "-network.xml.gz";
	}

	public static String facilitiesFile() {
		return "gunma-v" + VERSION + "-100pct-facilities.xml.gz";
	}

	public static String plansFile() {
		return "gunma-v" + VERSION + "-100pct-plans.xml.gz";
	}

	public static String vehicleTypesFile() {
		return "gunma-v" + VERSION + "-vehicleTypes.xml";
	}
}
