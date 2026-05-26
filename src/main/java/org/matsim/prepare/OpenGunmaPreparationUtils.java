package org.matsim.prepare;

import org.matsim.api.core.v01.Coord;
import org.matsim.run.Activities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Shared preparation-specific constants and helpers.
 */
public final class OpenGunmaPreparationUtils {

	/**
	 * Scaling factor if all persons use car (~70% share).
	 */
	public static final int CAR_FACTOR = 100 / 70;

	/**
	 * Flexible activities, which need to be known for location choice and during generation.
	 * A day can not end on a flexible activity.
	 */
	public static final Set<String> FLEXIBLE_ACTS = Set.of(Activities.other.name());

	private OpenGunmaPreparationUtils() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Round to two digits.
	 */
	public static double roundNumber(double x) {
		return BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
	}

	/**
	 * Round coordinates to sufficient precision.
	 */
	public static Coord roundCoord(Coord coord) {
		return new Coord(roundNumber(coord.getX()), roundNumber(coord.getY()));
	}
}
