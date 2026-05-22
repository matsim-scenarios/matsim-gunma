package org.matsim.run.scoring.experimental;

import jakarta.inject.Singleton;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.TasteVariationsConfigParameterSet;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.scoring.PseudoRandomScoringModule;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.run.scoring.TransitRouteToMode;

/**
 * Module to bind components needed for advanced scoring functionality configured by {@link AdvancedScoringConfigGroup}.
 */
public class AdvancedScoringModule extends AbstractModule {

	@Override
	public void install() {

		AdvancedScoringConfigGroup config = ConfigUtils.addOrGetModule(getConfig(), AdvancedScoringConfigGroup.class);

		bind(ScoringParametersForPerson.class).to(IndividualPersonScoringParameters.class).in(Singleton.class);
		bind(TransitRouteToMode.class).in(Singleton.class);

		addControlerListenerBinding().to(AdvancedScoringOutputWriter.class).in(Singleton.class);

		bindScoringFunctionFactory().to(AdvancedScoringFunctionFactory.class).in(Singleton.class);

		install(new PseudoRandomScoringModule(TasteVariationsConfigParameterSet.VariationType.normal, config.pseudoRamdomScale));

	}
}
