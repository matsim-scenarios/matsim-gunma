package org.matsim.run.scoring.experimental;

import com.google.common.primitives.Longs;
import com.google.inject.Inject;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.rng.RestorableUniformRandomProvider;
import org.apache.commons.rng.core.RandomProviderDefaultState;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.GumbelDistribution;
import org.apache.commons.statistics.distribution.NormalDistribution;
import org.apache.commons.statistics.distribution.TruncatedNormalDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.analysis.population.Category;
import org.matsim.application.analysis.population.TripAnalysis;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.ModeUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.pt.PtConstants;
import org.matsim.pt.config.TransitConfigGroup;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scoring parameters for {@link AdvancedScoringConfigGroup}. This makes use of the standard scoring and adds persons specific differences.
 */
public class IndividualPersonScoringParameters implements ScoringParametersForPerson {

	private static final Logger log = LogManager.getLogger(IndividualPersonScoringParameters.class);
	/**
	 * Header for info file.
	 */
	final Set<String> header = new LinkedHashSet<>();
	/**
	 * This map contains debug information to be written out after first iteration.
	 */
	final Map<Id<Person>, Object2DoubleMap<String>> info = new ConcurrentHashMap<>();
	/**
	 * Cache instances of {@link ActivityUtilityParameters} for each subpopulation.
	 */
	private final Map<String, Map<String, ActivityUtilityParameters>> actUtils = new ConcurrentHashMap<>();
	/**
	 * Cache instances of {@link ScoringParameters} for each person.
	 */
	private final IdMap<Person, ScoringParameters> cache;
	/**
	 * Cache and reuse distance group arrays.
	 */
	private final Map<DoubleList, DistanceGroup[]> distGroups = new ConcurrentHashMap<>();

	/**
	 * Categories from config group.
	 */
	private final Map<String, Category> categories;

	/**
	 * Thread-local random number generator.
	 */
	private final ThreadLocal<Context> rnd;
	private final Scenario scenario;
	private final ScoringConfigGroup basicScoring;
	private final TransitConfigGroup transitConfig;
	private final AdvancedScoringConfigGroup scoring;
	/**
	 * Average income of all agents with this attribute. Can be NaN if not used.
	 */
	private final double globalAvgIncome;

	@Inject
	public IndividualPersonScoringParameters(Scenario scenario) {
		this.scenario = scenario;
		this.basicScoring = scenario.getConfig().scoring();
		this.scoring = ConfigUtils.addOrGetModule(scenario.getConfig(), AdvancedScoringConfigGroup.class);
		this.transitConfig = scenario.getConfig().transit();
		this.globalAvgIncome = computeAvgIncome(scenario.getPopulation());
		this.categories = Category.fromConfigParams(this.scoring.getScoringParameters());
		this.cache = new IdMap<>(Person.class, scenario.getPopulation().getPersons().size());

		// Create uncorrelated seed from the global seed
		SplittableRandom rng = new SplittableRandom(scenario.getConfig().global().getRandomSeed());
		for (int i = 0; i < rng.nextInt(); i++) {
			rng.nextLong();
		}

		byte[] seed = Longs.toByteArray(rng.nextLong());
		this.rnd = ThreadLocal.withInitial(() -> new Context(seed));
	}

	static DistanceGroup[] calcDistanceGroups(List<Integer> dists, DoubleList distUtils) {

		// Nothing to do if no distance groups are defined.
		if (dists == null || dists.isEmpty() || distUtils.isEmpty()) {
			return null;
		}

		List<Integer> copy = new ArrayList<>(dists);

		if (copy.get(0) != 0)
			copy.add(0, 0);

		// Effectively no distance groups present
		if (copy.size() <= 1) {
			return null;
		}

		// No delta for distance groups
		if (distUtils.doubleStream().allMatch(d -> d == 0))
			return null;

		DistanceGroup[] groups = new DistanceGroup[copy.size()];

		if (groups.length - 1 != distUtils.size()) {
			log.error("Distance groups: {}, utils: {}", dists, distUtils);
			throw new IllegalArgumentException("Distance groups and distance utilities must have the same size.");
		}


		for (int i = 0; i < copy.size() - 1; i++) {

			double dist = copy.get(i);
			double nextDist = copy.get(i + 1);

			double constant = i == 0 ? 0 : distUtils.getDouble(i - 1);
			double next = distUtils.getDouble(i);


			groups[i] = new DistanceGroup(dist, constant, (next - constant) / (nextDist - dist));
		}

		// Last open ended dist group
		int lastDist = copy.getLast();
		double lastUtil = distUtils.getDouble(distUtils.size() - 1);
		groups[copy.size() - 1] = new DistanceGroup(lastDist, lastUtil, lastUtil / lastDist);

		return groups;
	}

	private double computeAvgIncome(Population population) {
		if (scoring.incomeDependent == AdvancedScoringConfigGroup.IncomeDependentScoring.none)
			return Double.NaN;

		log.info("reading income attribute using {} of all agents and compute global average.\n" +
			"Make sure to set this attribute only to appropriate agents (i.e. true 'persons' and not freight agents) \n" +
			"Income values <= 0 are ignored. Agents that have negative or 0 income will use the marginalUtilityOfMoney in their subpopulation's scoring params..", PersonUtils.class);
		OptionalDouble averageIncome = population.getPersons().values().stream()
			//consider only agents that have a specific income provided
			.filter(person -> PersonUtils.getIncome(person) != null)
			.mapToDouble(PersonUtils::getIncome)
			.filter(dd -> dd > 0)
			.average();

		if (averageIncome.isEmpty()) {
			throw new IllegalArgumentException("you have enabled income dependent scoring but there is not a single income attribute in the population! " +
				"If you are not aiming for person-specific marginalUtilityOfMoney, better use other PersonScoringParams, e.g. SubpopulationPersonScoringParams, which have higher performance." +
				"Otherwise, please provide income attributes in the population...");
		} else {
			log.info("global average income is {}", averageIncome);
			return averageIncome.getAsDouble();
		}
	}

	@Override
	public ScoringParameters getScoringParameters(Person person) {

		return this.cache.computeIfAbsent(person.getId(), id -> {

			String subpopulation = PopulationUtils.getSubpopulation(person);
			ScoringConfigGroup.ScoringParameterSet scoringParameters = basicScoring.getScoringParameters(subpopulation);

			// Activity params can be reused per subpopulation
			Map<String, ActivityUtilityParameters> activityParams = actUtils.computeIfAbsent(subpopulation, k -> {
				Map<String, ActivityUtilityParameters> ap = new TreeMap<>();
				for (ScoringConfigGroup.ActivityParams params : scoringParameters.getActivityParams()) {
					ActivityUtilityParameters.Builder factory = new ActivityUtilityParameters.Builder(params);
					ap.put(params.getActivityType(), factory.build());
				}

				// The code to add this activity type is always copied between different scoring implementations
				// it might not be actually needed anymore (because default staging activities are also added elsewhere)
				// but it's not clear if it's safe to remove it.
				if (transitConfig.isUseTransit()) {
					ScoringConfigGroup.ActivityParams transitActivityParams = new ScoringConfigGroup.ActivityParams(PtConstants.TRANSIT_ACTIVITY_TYPE);
					transitActivityParams.setTypicalDuration(120.0);
					transitActivityParams.setOpeningTime(0.);
					transitActivityParams.setClosingTime(0.);
					ActivityUtilityParameters.Builder modeParamsBuilder = new ActivityUtilityParameters.Builder(transitActivityParams);
					modeParamsBuilder.setScoreAtAll(false);
					ap.put(PtConstants.TRANSIT_ACTIVITY_TYPE, modeParamsBuilder.build());
				}

				return ap;
			});

			ScoringParameters.Builder builder = new ScoringParameters.Builder(basicScoring,
				scoringParameters, activityParams, scenario.getConfig().scenario());

			Double personalIncome = PersonUtils.getIncome(person);
			// Income dependent scoring might be disabled
			if (!Double.isNaN(globalAvgIncome) && personalIncome != null) {
				if (personalIncome != 0) {
					builder.setMarginalUtilityOfMoney(scoringParameters.getMarginalUtilityOfMoney() *
						Math.pow(globalAvgIncome / personalIncome, this.scoring.incomeExponent));
				} else {
					log.warn("You have set income to {} for person {}. This is invalid and gets ignored.Instead, the marginalUtilityOfMoney is derived from the subpopulation's scoring parameters.", personalIncome, person);
				}
			}

			Map<String, DistanceGroupModeUtilityParameters.DeltaBuilder> deltaParams = new HashMap<>();

			this.rnd.get().setSeed(person);

			for (AdvancedScoringConfigGroup.ScoringParameters parameter : scoring.getScoringParameters()) {

				if (Category.matchAttributesWithConfig(person.getAttributes(), parameter, categories)) {
					for (Map.Entry<String, AdvancedScoringConfigGroup.ModeParams> mode : parameter.getAllModeEntries()) {

						DistanceGroupModeUtilityParameters.DeltaBuilder b =
							deltaParams.computeIfAbsent(mode.getKey(), k -> new DistanceGroupModeUtilityParameters.DeltaBuilder());

						b.addUtilsDistance(mode.getValue());
						addDeltaParams(this.rnd.get(), b, mode.getValue());
					}
				}
			}

			Map<String, Map<ModeUtilityParameters.Type, Double>> modeTasteVariations = PersonUtils.getModeTasteVariations(person);
			if (modeTasteVariations == null) {
				modeTasteVariations = new LinkedHashMap<>();
			}

			for (Map.Entry<String, DistanceGroupModeUtilityParameters.DeltaBuilder> mode : deltaParams.entrySet()) {
				ModeUtilityParameters params = builder.getModeParameters(mode.getKey());
				DistanceGroupModeUtilityParameters.DeltaBuilder delta = mode.getValue();

				// This may happen for PT params
				if (params == null) {
					params = new ModeUtilityParameters(0, 0, 0, 0, 0, 0);
				}

				// These arrays are re-used if possible
				DistanceGroup[] groups = distGroups.computeIfAbsent(delta.getPerDistGroup(), k -> calcDistanceGroups(scoring.distGroups, k));

				// This may overwrite the preferences with the ones stored
				loadPreferences(mode.getKey(), delta, person, modeTasteVariations);

				DistanceGroupModeUtilityParameters p = new DistanceGroupModeUtilityParameters(params, delta, groups);
				builder.setModeParameters(mode.getKey(), p);

				// Collect final adjustments information
				Object2DoubleMap<String> values = info.computeIfAbsent(person.getId(), k -> new Object2DoubleOpenHashMap<>());

				// Write the overall constants, but only if they are different to the base values
				if (delta.constant != 0) {
					values.put(mode.getKey() + "_constant", p.constant);
					modeTasteVariations.computeIfAbsent(mode.getKey(), (k) -> new LinkedHashMap<>())
						.put(ModeUtilityParameters.Type.constant, delta.constant);
				}

				if (delta.dailyUtilityConstant != 0) {
					values.put(mode.getKey() + "_dailyConstant", p.dailyUtilityConstant);
					modeTasteVariations.computeIfAbsent(mode.getKey(), (k) -> new LinkedHashMap<>())
						.put(ModeUtilityParameters.Type.dailyUtilityConstant, delta.dailyUtilityConstant);
				}

				if (delta.marginalUtilityOfTraveling_util_hr != 0) {
					values.put(mode.getKey() + "_marginalUtilityOfTraveling_util_hr", p.marginalUtilityOfTraveling_s * 3600);
					modeTasteVariations.computeIfAbsent(mode.getKey(), (k) -> new LinkedHashMap<>())
						.put(ModeUtilityParameters.Type.marginalUtilityOfTraveling_s, delta.marginalUtilityOfTraveling_util_hr / 3600);
				}

				if (groups != null) {
					for (DistanceGroup group : groups) {
						values.put("%s_dist_%.0f".formatted(mode.getKey(), group.dist()), group.utilPerM());
					}
				}

				header.addAll(values.keySet());
			}

			if (!modeTasteVariations.isEmpty()) {
				PersonUtils.setModeTasteVariations(person, modeTasteVariations);
			}

			return builder.build();
		});
	}

	private void loadPreferences(String mode, DistanceGroupModeUtilityParameters.DeltaBuilder delta, Person person, Map<String, Map<ModeUtilityParameters.Type, Double>> modeTasteVariations) {

		boolean isRefPerson = person.getAttributes().getAttribute(TripAnalysis.ATTR_REF_ID) != null;

		if (scoring.loadPreferences == AdvancedScoringConfigGroup.LoadPreferences.none ||
			(isRefPerson && scoring.loadPreferences == AdvancedScoringConfigGroup.LoadPreferences.skipRefPersons)) {
			return;
		}

		Map<ModeUtilityParameters.Type, Double> existing = modeTasteVariations.get(mode);

		// Use attributes if they are present
		if (existing.containsKey(ModeUtilityParameters.Type.constant))
			delta.constant = existing.get(ModeUtilityParameters.Type.constant);

		if (existing.containsKey(ModeUtilityParameters.Type.dailyUtilityConstant))
			delta.dailyUtilityConstant = existing.get(ModeUtilityParameters.Type.dailyUtilityConstant);

		if (existing.containsKey(ModeUtilityParameters.Type.marginalUtilityOfTraveling_s))
			delta.marginalUtilityOfTraveling_util_hr = existing.get(ModeUtilityParameters.Type.marginalUtilityOfTraveling_s) * 3600;
	}

	/**
	 * Compute or retrieve delta params for person.
	 */
	private void addDeltaParams(Context ctx, DistanceGroupModeUtilityParameters.DeltaBuilder delta, AdvancedScoringConfigGroup.ModeParams params) {

		ContinuousDistribution.Sampler normal = ctx.normal.createSampler(ctx.rnd());
		ContinuousDistribution.Sampler tn = ctx.tn.createSampler(ctx.rnd());
		ContinuousDistribution.Sampler tn2 = ctx.tn2.createSampler(ctx.rnd());

		switch (params.varConstant) {
			case fixed -> delta.constant += params.deltaConstant;
			case normal -> delta.constant += normal.sample() * params.deltaConstant;
			case logNormal -> delta.constant += Math.exp(normal.sample()) * params.deltaConstant;
			case truncatedNormal -> delta.constant += tn.sample() * params.deltaConstant;
			case truncatedNormalS2 -> delta.constant += tn2.sample() * params.deltaConstant;
			default -> throw new IllegalArgumentException("Unsupported varConstant: " + params.varConstant);
		}

		switch (params.varDailyConstant) {
			case fixed -> delta.dailyUtilityConstant += params.deltaDailyConstant;
			case normal -> delta.dailyUtilityConstant += normal.sample() * params.deltaDailyConstant;
			case logNormal -> delta.dailyUtilityConstant += Math.exp(normal.sample()) * params.deltaDailyConstant;
			case truncatedNormal -> delta.dailyUtilityConstant += tn.sample() * params.deltaDailyConstant;
			case truncatedNormalS2 -> delta.dailyUtilityConstant += tn2.sample() * params.deltaDailyConstant;
			default -> throw new IllegalArgumentException("Unsupported varDailyConstant: " + params.varDailyConstant);
		}

		switch (params.varMarginalUtilityOfTraveling_util_hr) {
			case fixed -> delta.marginalUtilityOfTraveling_util_hr += params.deltaMarginalUtilityOfTraveling_util_hr;
			case normal -> delta.marginalUtilityOfTraveling_util_hr += normal.sample() * params.deltaMarginalUtilityOfTraveling_util_hr;
			case logNormal -> delta.marginalUtilityOfTraveling_util_hr += Math.exp(normal.sample()) * params.deltaMarginalUtilityOfTraveling_util_hr;
			case truncatedNormal -> delta.marginalUtilityOfTraveling_util_hr += tn.sample() * params.deltaMarginalUtilityOfTraveling_util_hr;
			case truncatedNormalS2 -> delta.marginalUtilityOfTraveling_util_hr += tn2.sample() * params.deltaMarginalUtilityOfTraveling_util_hr;
			default -> throw new IllegalArgumentException("Unsupported varDailyConstant: " + params.varMarginalUtilityOfTraveling_util_hr);
		}
	}

	/**
	 * Thread-local context for random number generation. This makes generation thread-safe and consistent independently of threads and order of persons.
	 */
	private record Context(NormalDistribution normal, TruncatedNormalDistribution tn, TruncatedNormalDistribution tn2,
						   GumbelDistribution gumbel,
						   byte[] seed, RestorableUniformRandomProvider rnd) {

		Context(byte[] seed) {
			this(NormalDistribution.of(0, 1),
				TruncatedNormalDistribution.of(0, 1, 0, Double.POSITIVE_INFINITY),
				TruncatedNormalDistribution.of(0, 1, -2, 2),
				// Zero mean gumbel, with euler gamma as location parameter
				GumbelDistribution.of(-0.5772156649015329, 1),
				seed,
				RandomSource.KISS.create());
		}

		/**
		 * Set the state of rnd specific to person and configured global seed.
		 */
		void setSeed(Person p) {

			byte[] state = new byte[20];
			byte[] person = p.getId().toString().getBytes();

			// Reverse, because the more significant bytes are at the end
			ArrayUtils.reverse(person);

			System.arraycopy(seed, 0, state, 0, 8);
			System.arraycopy(person, 0, state, 8, Math.min(person.length, 12));

			rnd.restoreState(new RandomProviderDefaultState(state));

			// Warm up the generator
			for (int i = 0; i < 100; i++) {
				rnd.nextLong();
			}
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Context context)) return false;
			return Objects.deepEquals(seed, context.seed) && Objects.equals(normal, context.normal) && Objects.equals(gumbel, context.gumbel) && Objects.equals(tn, context.tn) && Objects.equals(tn2, context.tn2) && Objects.equals(rnd, context.rnd);
		}

		@Override
		public int hashCode() {
			return Objects.hash(normal, tn, tn2, gumbel, Arrays.hashCode(seed), rnd);
		}
	}
}
