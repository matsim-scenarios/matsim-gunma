//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.matsim.prepare.population;

import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.application.options.SampleOptions;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
	name = "population-attribute",
	description = {"Generates statistics of the population attributes."}
)
@CommandSpec(
	requirePopulation = true,
	produces = {"amount_per_age_group.csv", "amount_per_age_group_men.csv", "amount_per_age_group_women.csv", "amount_per_sex_group.csv", "total_agents.csv", "average_income_per_age_group.csv"}
)
public class GunmaPopulationAttributeAnalysis implements MATSimAppCommand {
	private static final Logger log = LogManager.getLogger(GunmaPopulationAttributeAnalysis.class);
	@Mixin
	private final InputOptions input = InputOptions.ofCommand(GunmaPopulationAttributeAnalysis.class);
	@Mixin
	private final OutputOptions output = OutputOptions.ofCommand(GunmaPopulationAttributeAnalysis.class);
	@CommandLine.Mixin
	private SampleOptions sample;


	Object2IntSortedMap<String> amountPerAgeGroup =
		new Object2IntAVLTreeMap<>(
			Comparator.comparingInt(k ->
				k.equals("75+") ? 75 : Integer.parseInt(k.split("-")[0])
			)
		);

	Object2IntSortedMap<String> amountPerAgeGroupMen =
		new Object2IntAVLTreeMap<>(
			Comparator.comparingInt(k ->
				k.equals("75+") ? 75 : Integer.parseInt(k.split("-")[0])
			)
		);

	Object2IntSortedMap<String> amountPerAgeGroupWomen =
		new Object2IntAVLTreeMap<>(
			Comparator.comparingInt(k ->
				k.equals("75+") ? 75 : Integer.parseInt(k.split("-")[0])
			)
		);

	private final Map<String, Integer> amountPerSexGroup = new HashMap();
	private final Map<Integer, List<Double>> averageIncomeOverAge = new HashMap();
	private Integer totalAgents = 0;
	private final List<Double> allIncomes = new ArrayList();
	private final List<Integer> allAges = new ArrayList();

	public static void main(String[] args) {
		(new GunmaPopulationAttributeAnalysis()).execute(args);
	}

	@Override
	public Integer call() throws Exception {
		Population population = this.input.getPopulation();

//		Config config = ConfigUtils.loadConfig(ApplicationUtils.matchInput("output_config.xml", input.getRunDirectory()).toString());
//		sample = config

		for (Person person : population.getPersons().values()) {

			if (person.getId().toString().startsWith("commuter")) {
				continue;
			}

//			Integer var4 = this.totalAgents;
			this.totalAgents = this.totalAgents + 1;
			if (person.getAttributes().getAsMap().containsKey("age") && person.getAttributes().getAsMap().containsKey("income") && person.getAttributes().getAsMap().containsKey("sex")) {
				this.splitIncomeOverAgeAndSex((Integer) person.getAttributes().getAttribute("age"), (Double) person.getAttributes().getAttribute("income"));

			}

			Map<String, Object> map = person.getAttributes().getAsMap();


//			Double income = null;
			String sex = null;
			Integer age = null;
			for (Map.Entry<String, Object> entry : map.entrySet()) {

				if (entry.getKey().equals("income")) {
					this.allIncomes.add(Double.valueOf(entry.getValue().toString()));
				}

//				if (((String)entry.getKey()).equals("vehicles")) {
//					log.info(((PersonVehicles)entry.getValue()).getModeVehicles().toString());
//				} else {
//					log.info(entry.getValue().toString());
//				}

				if (entry.getKey().equals("sex")) {

					sex = (String) entry.getValue();
					this.splitAgentsIntoSex(sex);
				}

				if (entry.getKey().equals("age")) {

					age = (Integer) entry.getValue();
//					this.splitAgentsIntoAgeGroup(age);
//					this.allAges.add(age);
				}
			}


			this.splitAgentsIntoAgeGroup(age, sex);
			this.allAges.add(age);
		}


		// Print age groups
		printAgeGroupByGender("amount_per_age_group.csv", this.amountPerAgeGroup);

		// Print age group - men
		printAgeGroupByGender("amount_per_age_group_men.csv", this.amountPerAgeGroupMen);

		// Print age groups - women
		printAgeGroupByGender("amount_per_age_group_women.csv", this.amountPerAgeGroupWomen);

		try (CSVPrinter printer = new CSVPrinter(IOUtils.getBufferedWriter(this.output.getPath("average_income_per_age_group.csv").toString()), CSVFormat.DEFAULT)) {
			printer.printRecord("Age", "avg. Income");

			for (Map.Entry<Integer, List<Double>> entry : this.averageIncomeOverAge.entrySet()) {

				printer.printRecord(entry.getKey(), this.calculateMeanFromDoubleArray((List) entry.getValue()));
			}
		} catch (IOException ex) {
			log.error(ex);
		}
		DecimalFormat df = new DecimalFormat("###0.0#");

		try (CSVPrinter printer = new CSVPrinter(IOUtils.getBufferedWriter(this.output.getPath("total_agents.csv").toString()), CSVFormat.DEFAULT)) {
			printer.printRecord(new Object[]{
				"Total Agents (simulated, scaled)",
//				"Sim (unscaled): " + this.totalAgents,
				(int) (this.totalAgents * sample.getUpscaleFactor()),
				"user-group"});
			printer.printRecord(new Object[]{
				"Total Agents (reference)",
//				"Sim (unscaled): " + this.totalAgents,
				1939110,
				"user-group"});
			printer.printRecord(new Object[]{
				"Average Age (simulated)",
				df.format(this.calculateMeanFromIntegerArray(this.allAges)),
				"birthday-cake"}
			);

			printer.printRecord(new Object[]{
				"Average Age (reference)",
				48.39,
				"birthday-cake"}
			);
//			printer.printRecord(new Object[]{"Average Income (simulated vs. ref)",
//				df.format(this.calculateMeanFromDoubleArray(this.allIncomes)) + " vs. ?",
//				"money-check-dollar"});

		} catch (IOException ex) {
			log.error(ex);
		}

			// Reference values

//		try (CSVPrinter printer = new CSVPrinter(IOUtils.getBufferedWriter(this.output.getPath("total_agents-ref.csv").toString()), CSVFormat.DEFAULT)) {
//
//			printer.printRecord(new Object[]{
//			"Total Agents | reference",
////				"Sim (unscaled): " + this.totalAgents,
//			1939110,
//			"user-group"});
//			printer.printRecord(new Object[]{
//				"Average Age | reference",
//				48.39,
//				"birthday-cake"}
//			);
//			printer.printRecord(new Object[]{"Average Income | reference",
//				(new DecimalFormat("#.0#")).format(this.calculateMeanFromDoubleArray(this.allIncomes)),
//				"money-check-dollar"});
//		} catch (IOException ex) {
//			log.error(ex);
//		}

		try (CSVPrinter printer = new CSVPrinter(IOUtils.getBufferedWriter(this.output.getPath("amount_per_sex_group.csv").toString()), CSVFormat.DEFAULT)) {
			printer.printRecord(this.amountPerSexGroup.keySet());
			printer.printRecord(this.amountPerSexGroup.values());
		} catch (IOException ex) {
			log.error(ex);
		}

		return 0;
	}

	private void printAgeGroupByGender(String filename, Object2IntSortedMap<String> dataMap) {
		try (CSVPrinter printer = new CSVPrinter(IOUtils.getBufferedWriter(this.output.getPath(filename).toString()), CSVFormat.DEFAULT)) {
			printer.printRecord("Age", "n");
			ObjectIterator var24 = dataMap.object2IntEntrySet().iterator();

			while (var24.hasNext()) {

				Object2IntMap.Entry entry = (Object2IntMap.Entry) var24.next();
				printer.printRecord(String.valueOf(entry.getKey()), entry.getIntValue() * sample.getUpscaleFactor());
			}

		} catch (IOException ex) {
			log.error(ex);
		}
	}

	private double calculateMeanFromIntegerArray(List<Integer> allAges) {
		Double sum = (double) 0.0F;

		for (Integer income : allAges) {
			sum = sum + (double) income;
		}

		return (double) Math.round(sum / (double) allAges.size() * (double) 100.0F) / (double) 100.0F;
	}

	private double calculateMeanFromDoubleArray(List<Double> value) {
		Double sum = (double) 0.0F;

		for (Double income : value) {
			sum = sum + income;
		}

		return (double) Math.round(sum / (double) value.size() * (double) 100.0F) / (double) 100.0F;
	}

	private void splitIncomeOverAgeAndSex(int age, double income) {
		int roundedAge = Math.round((float) ((age - 1) / 10 + 1)) * 10;
		double roundedIncome = (double) (Math.round((income - (double) 1.0F) / (double) 100.0F + (double) 1.0F) * 100L);

		if (!this.averageIncomeOverAge.containsKey(roundedAge)) {
			this.averageIncomeOverAge.put(roundedAge, new ArrayList());
		}

		((List) this.averageIncomeOverAge.get(roundedAge)).add(roundedIncome);
	}

	private void splitAgentsIntoSex(String sex) {
		if (sex.equals("m")) {
			sex = "Male";
		}

		if (sex.equals("f")) {
			sex = "Female";
		}

		if (this.amountPerSexGroup.containsKey(sex)) {
			this.amountPerSexGroup.put(sex, (Integer) this.amountPerSexGroup.get(sex) + 1);
		} else {
			this.amountPerSexGroup.put(sex, 1);
		}

	}

	private void splitAgentsIntoAgeGroup(Integer age, String sex) {

		String label;

		if (age >= 75) {
			label = "75+";
		} else {
			int lower = (age / 5) * 5;
			int upper = lower + 4;
			label = lower + "-" + upper;
		}

		if (this.amountPerAgeGroup.containsKey(label)) {
			this.amountPerAgeGroup.put(label, this.amountPerAgeGroup.getInt(label) + 1);
		} else {
			this.amountPerAgeGroup.put(label, 1);
		}

		if (sex.equals("m")) {
			if (this.amountPerAgeGroupMen.containsKey(label)) {
				this.amountPerAgeGroupMen.put(label, this.amountPerAgeGroupMen.getInt(label) + 1);
			} else {
				this.amountPerAgeGroupMen.put(label, 1);
			}
		} else if (sex.equals("f")) {
			if (this.amountPerAgeGroupWomen.containsKey(label)) {
				this.amountPerAgeGroupWomen.put(label, this.amountPerAgeGroupWomen.getInt(label) + 1);
			} else {
				this.amountPerAgeGroupWomen.put(label, 1);
			}
		}

	}
}
