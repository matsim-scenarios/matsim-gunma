package org.matsim.dashboard;

import jakarta.annotation.Nullable;
import org.matsim.prepare.population.GunmaPopulationAttributeAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.viz.PieChart;
import org.matsim.simwrapper.viz.Plotly;
import org.matsim.simwrapper.viz.Tile;
import tech.tablesaw.plotly.traces.BarTrace;

import java.util.Map;

/**
 * Shows attributes distribution of the population.
 */
public class GunmaPopulationAttributeDashboard implements Dashboard {

	private static final String SOURCE_COLUMN = "source";

	private final String ageRef;
	private final String ageMenRef;
	private final String ageWomenRef;


	public GunmaPopulationAttributeDashboard() {
		this(null, null, null);
	}

	public GunmaPopulationAttributeDashboard(@Nullable String ageRef, @Nullable String ageMenRef, @Nullable String ageWomenRef) {
		this.ageRef = ageRef;
		this.ageMenRef = ageMenRef;
		this.ageWomenRef = ageWomenRef;
	}
	@Override
	public void configure(Header header, Layout layout, SimWrapperConfigGroup configGroup) {

		header.title = "Population";
		header.description = "Analyze the sociodemographic characteristics of the population";

		layout.row("key-indicators-sim")
			.el(Tile.class, (viz, data) -> {
				viz.title = "";
				viz.dataset = data.compute(GunmaPopulationAttributeAnalysis.class, "total_agents.csv");
				viz.height = 0.1;
//				viz.width = 0.5;
			});


		layout.row("sex")
//			.el(Bar.class, (viz, data) -> {
//				viz.title = "Agents per age group";
//				viz.stacked = false;
//				viz.dataset = data.compute(GunmaPopulationAttributeAnalysis.class, "amount_per_age_group.csv");
//				viz.x = "Age";
//				viz.xAxisName = "Age (≤)";
//				viz.yAxisName = "Amount";
//			})
			.el(PieChart.class, (viz, data) -> {
				viz.title = "Agents per sex group | simulated";
				viz.dataset = data.compute(GunmaPopulationAttributeAnalysis.class, "amount_per_sex_group.csv");
				viz.useLastRow = true;
			})
			.el(PieChart.class, (viz, data) -> {
				viz.title = "Agents per sex group | reference";
				viz.dataset = data.resource("resources/amount_per_sex_group-ref.csv");
				viz.useLastRow = true;
			});

//		layout.row("third")
//			.el(Bar.class, (viz, data) -> {
//				viz.title = "Average Income per Age Group";
//				viz.stacked = false;
//				viz.dataset = data.compute(GunmaPopulationAttributeAnalysis.class, "average_income_per_age_group.csv");
//				viz.x = "Age";
//				viz.xAxisName = "Age (≤)";
//				viz.yAxisName = "avg. Income";
//			});

		layout.row("age-all").el(Plotly.class, (viz, data) -> {

			viz.title = "Age Distribution";
			viz.description = "Population by age group.";
			viz.width = 2d;

			Plotly.DataSet ds = viz.addDataset(data.compute(GunmaPopulationAttributeAnalysis.class, "amount_per_age_group.csv"));
			viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).build(), ds.mapping()
				.x("Age")
				.y("n")
				.name(SOURCE_COLUMN)
			);

			if (ageRef != null) {
				ds.constant(SOURCE_COLUMN, "sim");

				viz.addDataset(data.resource(ageRef))
					.constant(SOURCE_COLUMN, "ref");

				viz.multiIndex = Map.of("Age", SOURCE_COLUMN);
				viz.mergeDatasets = true;
			}
		});

		layout.row("age-men").el(Plotly.class, (viz, data) -> {
			viz.title = "Age Distribution for Men";
			viz.description = "Sim data scaled up 100%";
			viz.width = 2d;

			Plotly.DataSet ds = viz.addDataset(data.compute(GunmaPopulationAttributeAnalysis.class, "amount_per_age_group_men.csv"));
			viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).build(), ds.mapping()
				.x("Age")
				.y("n")
				.name(SOURCE_COLUMN)
			);

			if (ageMenRef != null) {
				ds.constant(SOURCE_COLUMN, "sim");

				viz.addDataset(data.resource(ageMenRef))
					.constant(SOURCE_COLUMN, "ref");

				viz.multiIndex = Map.of("Age", SOURCE_COLUMN);
				viz.mergeDatasets = true;
			}
		});

		layout.row("age-women").el(Plotly.class, (viz, data) -> {
			viz.title = "Age Distribution for Women";
			viz.description = "Sim data scaled up 100%";
			viz.width = 2d;

			Plotly.DataSet ds = viz.addDataset(data.compute(GunmaPopulationAttributeAnalysis.class, "amount_per_age_group_women.csv"));
			viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).build(), ds.mapping()
				.x("Age")
				.y("n")
				.name(SOURCE_COLUMN)
			);

			if (ageWomenRef != null) {
				ds.constant(SOURCE_COLUMN, "sim");

				viz.addDataset(data.resource(ageWomenRef))
					.constant(SOURCE_COLUMN, "ref");

				viz.multiIndex = Map.of("Age", SOURCE_COLUMN);
				viz.mergeDatasets = true;
			}
		});
	}
}
