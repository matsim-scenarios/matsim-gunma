package org.matsim.run.scoring.experimental;

import com.google.common.collect.Iterables;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.*;

/**
 * Stores scoring parameters for {@link AdvancedScoringModule}.
 */
@SuppressWarnings({"checkstyle:VisibilityModifier", "checkstyle:MemberName"})
public final class AdvancedScoringConfigGroup extends ReflectiveConfigGroup {

	private static final String GROUP_NAME = "advancedScoring";

	@Parameter
	@Comment("The distance groups if marginal utility of distance is adjusted. In meters.")
	public List<Integer> distGroups;

	@Parameter
	@Comment("Enable income dependent marginal utility of money.")
	public IncomeDependentScoring incomeDependent = IncomeDependentScoring.avgByPersonalIncome;

	@Parameter
	@Comment("Exponent for (global_income / personal_income) ** x.")
	public double incomeExponent = 1;

	@Parameter
	@Comment("Define how to load existing preferences.")
	public LoadPreferences loadPreferences = LoadPreferences.none;

	@Parameter
	@Comment("Scale for pseudo random errors. 0 disables it completely.")
	public double pseudoRamdomScale = 0;

	private final List<ScoringParameters> scoringParameters = new ArrayList<>();

	public AdvancedScoringConfigGroup() {
		super(GROUP_NAME);
	}

	/**
	 * Return the defined scoring parameters.
	 */
	public List<ScoringParameters> getScoringParameters() {
		return Collections.unmodifiableList(scoringParameters);
	}

	@Override
	public ConfigGroup createParameterSet(String type) {
		if (type.equals(ScoringParameters.GROUP_NAME)) {
			return new ScoringParameters();
		} else {
			throw new IllegalArgumentException("Unsupported parameter set type: " + type);
		}
	}

	@Override
	public void addParameterSet(ConfigGroup set) {
		if (set instanceof ScoringParameters p) {
			super.addParameterSet(set);
			scoringParameters.add(p);
		} else {
			throw new IllegalArgumentException("Unsupported parameter set class: " + set);
		}
	}

	/**
	 * Different options for income dependent scoring.
	 */
	public enum IncomeDependentScoring {
		none,
		avgByPersonalIncome
	}

	/**
	 * Define how existing preferences are loaded.
	 */
	public enum LoadPreferences {
		none,
		skipMissing,
		skipRefPersons
	}

	/**
	 * Variate values with random draw from specific distribution.
	 */
	public enum VariationType {
		fixed, normal, logNormal, truncatedNormal, truncatedNormalS2, gumbel
	}

	/**
	 * Scoring parameters for a specific group of agents.
	 * This group allows arbitrary attributes to be defined, which are matched against person attributes.
	 */
	public static final class ScoringParameters extends ReflectiveConfigGroup {

		private static final String GROUP_NAME = "scoringParameters";

		/**
		 * Params per mode.
		 */
		private final Map<String, ModeParams> modeParams = new HashMap<>();

		/**
		 * Scoring parameter for individual pt types. This requires that transit lines have a "simple_route_type" attribute.
		 */
		private final Map<String, ModeParams> ptParams = new HashMap<>();

		public ScoringParameters() {
			super(GROUP_NAME, true);
		}

		public Map<String, ModeParams> getModeParams() {
			return modeParams;
		}

		public Map<String, ModeParams> getPtParams() {
			return ptParams;
		}

		/**
		 * Returns mode params and pt params.
		 *
		 */
		public Iterable<Map.Entry<String, ModeParams>> getAllModeEntries() {
			return Iterables.concat(modeParams.entrySet(), ptParams.entrySet());
		}

		@Override
		public ConfigGroup createParameterSet(final String type) {
			return switch (type) {
				case ModeParams.GROUP_NAME -> new ModeParams(false);
				case ModeParams.PT_GROUP_NAME -> new ModeParams(true);
				default -> throw new IllegalArgumentException(type);
			};
		}

		@Override
		public void addParameterSet(ConfigGroup set) {
			if (set instanceof ModeParams p) {
				super.addParameterSet(set);

				if (set.getName().equals(ModeParams.PT_GROUP_NAME))
					ptParams.put(p.mode, p);
				else if (set.getName().equals(ModeParams.GROUP_NAME))
					modeParams.put(p.mode, p);
			} else {
				throw new IllegalArgumentException("Unsupported parameter set class: " + set);
			}
		}

		/**
		 * Retrieve mode parameters.
		 */
		public ModeParams getOrCreateModeParams(String mode) {
			if (!modeParams.containsKey(mode)) {
				ModeParams p = new ModeParams(false);
				p.mode = mode;

				addParameterSet(p);
				return p;
			}

			return modeParams.get(mode);
		}

		/**
		 * Retrieve mode parameters for a specific pt type.
		 */
		public ModeParams getOrCreatePtParams(String ptType) {
			if (!ptParams.containsKey(ptType)) {
				ModeParams p = new ModeParams(true);
				p.mode = ptType;

				addParameterSet(p);
				return p;
			}

			return ptParams.get(ptType);
		}

	}

	/**
	 * Stores mode specific parameters and also attributes to whom to apply this specification.
	 */
	public static final class ModeParams extends ReflectiveConfigGroup {

		private static final String GROUP_NAME = "modeParams";
		private static final String PT_GROUP_NAME = "ptParams";

		@Parameter
		@Comment("The mode for which the parameters are defined.")
		public String mode;

		@Parameter
		@Comment("[utils/leg] alternative-specific constant.")
		public double deltaConstant;

		@Parameter
		@Comment("Variation of the constant across individuals.")
		public VariationType varConstant = VariationType.fixed;

		@Parameter
		@Comment("[utils/day] if the mode is used at least once.")
		public double deltaDailyConstant;

		@Parameter
		@Comment("Variation of the daily constant across individuals.")
		public VariationType varDailyConstant = VariationType.fixed;

		@Parameter
		@Comment("[utils/hour] for using the mode.")
		public double deltaMarginalUtilityOfTraveling_util_hr;

		@Parameter
		@Comment("Variation of the marginal utility of traveling across individuals.")
		public VariationType varMarginalUtilityOfTraveling_util_hr = VariationType.fixed;

		@Parameter
		@Comment("Total delta utility per dist group.")
		public List<Double> deltaPerDistGroup;

		/*
		Unused options:

		@Parameter
		@Comment("Marginal utility of distance calculated as beta_dist * (dist/ref_dist)^exp_dist.")
		public double betaDist = 0;

		@Parameter
		@Comment("Reference mean distance.")
		public double refDist;

		@Parameter
		@Comment("Exponent controlling non-linearity of distance utility.")
		public double expDist = 0;
		 */

		public ModeParams(boolean isPt) {
			super(isPt ? PT_GROUP_NAME : GROUP_NAME);
		}
	}
}
