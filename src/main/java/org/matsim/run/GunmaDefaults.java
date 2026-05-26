package org.matsim.run;

/**
 * Shared scenario metadata and small repo-wide helpers.
 */
public final class GunmaDefaults {

	/** Current scenario input/output version identifier. */
	public static final String VERSION = "1.8";
	/** Coordinate reference system used by the scenario. */
	public static final String CRS = "EPSG:2450";
	/** Scaling factor used when all travellers are converted to car. */
	public static final int CAR_FACTOR = 100 / 70;
	/** Flexible activity types used during preparation. */
	public static final java.util.Set<String> FLEXIBLE_ACTS = java.util.Set.of(Activities.other.name());

	private GunmaDefaults() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Returns the canonical path to the base MATSim config file for the current scenario version.
	 */
	public static String configPath() {
		return "input/v" + VERSION + "/gunma-v" + VERSION + "-config.xml";
	}

	/**
	 * Returns the canonical network file name for the current scenario version.
	 */
	public static String networkFile() {
		return "gunma-v" + VERSION + "-network.xml.gz";
	}

	/**
	 * Returns the canonical facilities file name for the current scenario version.
	 */
	public static String facilitiesFile() {
		return "gunma-v" + VERSION + "-100pct-facilities.xml.gz";
	}

	/**
	 * Returns the canonical plans file name for the current scenario version.
	 */
	public static String plansFile() {
		return "gunma-v" + VERSION + "-100pct-plans.xml.gz";
	}

	/**
	 * Returns the canonical vehicle types file name for the current scenario version.
	 */
	public static String vehicleTypesFile() {
		return "gunma-v" + VERSION + "-vehicleTypes.xml";
	}

	/**
	 * Rounds a floating-point value to two decimal places using half-even rounding.
	 *
	 * @param x value to round
	 * @return rounded value
	 */
	public static double roundNumber(double x) {
		return java.math.BigDecimal.valueOf(x).setScale(2, java.math.RoundingMode.HALF_EVEN).doubleValue();
	}
}
