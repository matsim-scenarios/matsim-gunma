package org.matsim.run.scoring;

import com.google.inject.Inject;
import org.matsim.api.core.v01.IdMap;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

/**
 * Used to lookup the mode of a transit route.
 */
public final class TransitRouteToMode {

	private final IdMap<TransitRoute, String> routeToMode = new IdMap<>(TransitRoute.class);

	@Inject
	public TransitRouteToMode(){}

//	@Inject
//	public TransitRouteToMode(TransitSchedule transitSchedule) {
//		for (TransitLine line : transitSchedule.getTransitLines().values()) {
//			for (TransitRoute route : line.getRoutes().values()) {
//				Object type = route.getAttributes().getAttribute("simple_route_type");
//				if (type != null) {
//					routeToMode.put(route.getId(), type.toString().intern());
//				}
//			}
//		}
//	}

	/**
	 * Get the "submode" of the given transit route. It may be scored additionally to the main mode.
	 */
	public String getMode(DefaultTransitPassengerRoute pt) {
		return routeToMode.get(pt.getRouteId());
	}
}
