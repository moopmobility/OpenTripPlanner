package org.opentripplanner.routing.algorithm;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.filterchain.ItineraryListFilterChain;
import org.opentripplanner.routing.algorithm.mapping.RoutingRequestToFilterChainMapper;
import org.opentripplanner.routing.algorithm.mapping.RoutingResponseMapper;
import org.opentripplanner.routing.algorithm.raptor.router.FilterTransitWhenDirectModeIsEmpty;
import org.opentripplanner.routing.algorithm.raptor.router.TransitRouter;
import org.opentripplanner.routing.algorithm.raptor.router.street.DirectFlexRouter;
import org.opentripplanner.routing.algorithm.raptor.router.street.DirectStreetRouter;
import org.opentripplanner.routing.algorithm.raptor.transit.mappers.DateMapper;
import org.opentripplanner.routing.api.request.RoutingRequest;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingResponse;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.routing.framework.DebugTimingAggregator;
import org.opentripplanner.standalone.server.Router;
import org.opentripplanner.transit.raptor.api.request.SearchParams;
import org.opentripplanner.util.OTPFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Does a complete transit search, including access and egress legs.
 * <p>
 * This class has a request scope, hence the "Worker" name.
 */
public class RoutingWorker {
    private static final Logger LOG = LoggerFactory.getLogger(RoutingWorker.class);

    /** An object that accumulates profiling and debugging info for inclusion in the response. */
    public final DebugTimingAggregator debugTimingAggregator = new DebugTimingAggregator();

    private final RoutingRequest request;
    private final Router router;
    private final FilterTransitWhenDirectModeIsEmpty emptyDirectModeHandler;
    private final ZonedDateTime searchStartTime;
    private SearchParams raptorSearchParamsUsed = null;
    private Itinerary firstRemovedItinerary = null;

    public RoutingWorker(Router router, RoutingRequest request, ZoneId zoneId) {
        this.debugTimingAggregator.startedCalculating();
        this.request = request;
        this.router = router;
        this.emptyDirectModeHandler = new FilterTransitWhenDirectModeIsEmpty(request.modes);
        this.searchStartTime = DateMapper.asStartOfService(request.getDateTimeCurrentPage(), zoneId);
    }

    public RoutingResponse route() {
        LOG.debug("RoutingWorker input");
        LOG.debug("PageCursor        : " + request.pageCursor);
        LOG.debug("Request sw        : " + request.searchWindow);
        LOG.debug("Request org. time : " + request.getDateTimeOriginalSearch());
        LOG.debug("Request page time : " + request.getDateTimeCurrentPage());

        // Adjust the 'dateTime' if "goto next page"(the page cursor is set)
        // The date-time is used for many thing like finding the days to search,
        // but the transit search is using the cursor[if exist], not the datetime.
        if(request.pageCursor != null) {
            Instant dateTimeCurrentPage = request.getDateTimeCurrentPage();
            request.setDateTime(dateTimeCurrentPage);
            LOG.debug("Request dateTime={} set from pageCursor.", dateTimeCurrentPage);
        }

        // If no direct mode is set, then we set one.
        // See {@link FilterTransitWhenDirectModeIsEmpty}
        request.modes.directMode = emptyDirectModeHandler.resolveDirectMode();

        this.debugTimingAggregator.finishedPrecalculating();

        var itineraries = Collections.synchronizedList(new ArrayList<Itinerary>());
        var routingErrors = Collections.synchronizedSet(new HashSet<RoutingError>());

        if (OTPFeature.ParallelRouting.isOn()) {
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> routeDirectStreet(itineraries, routingErrors)),
                    CompletableFuture.runAsync(() -> routeDirectFlex(itineraries, routingErrors)),
                    CompletableFuture.runAsync(() -> routeTransit(itineraries, routingErrors))
            ).join();
        } else {
            // Direct street routing
            routeDirectStreet(itineraries, routingErrors);

            // Direct flex routing
            routeDirectFlex(itineraries, routingErrors);

            // Transit routing
            routeTransit(itineraries, routingErrors);
        }

        debugTimingAggregator.finishedRouting();

        Instant filterOnLatestDepartureTime = null;

        // Filter itineraries away that depart after the latest-departure-time for depart after
        // search. These itineraries are a result of time-shifting the access leg and is needed for
        // the raptor to prune the results. These itineraries are often not ideal, but if they
        // pareto optimal for the "next" window, they will appear when a "next" search is performed.
        if(!request.arriveBy
            && raptorSearchParamsUsed != null
            && raptorSearchParamsUsed.isSearchWindowSet()
            && raptorSearchParamsUsed.isEarliestDepartureTimeSet()
        ) {
            int ldt = raptorSearchParamsUsed.earliestDepartureTime()
                    + raptorSearchParamsUsed.searchWindowInSeconds();
            filterOnLatestDepartureTime = searchStartTime.plusSeconds(ldt).toInstant();
        }


        // Filter itineraries
        ItineraryListFilterChain filterChain = RoutingRequestToFilterChainMapper.createFilterChain(
            request,
            filterOnLatestDepartureTime,
            emptyDirectModeHandler.removeWalkAllTheWayResults(),
            it -> firstRemovedItinerary = it
        );

        List<Itinerary> filteredItineraries = filterChain.filter(itineraries);

        routingErrors.addAll(filterChain.getRoutingErrors());

        LOG.debug("Return TripPlan with {} filtered itineraries out of {} total.", filteredItineraries.size(), itineraries.size());

        this.debugTimingAggregator.finishedFiltering();

        // Restore original directMode.
        request.modes.directMode = emptyDirectModeHandler.originalDirectMode();

        return RoutingResponseMapper.map(
                request,
                searchStartTime,
                raptorSearchParamsUsed,
                firstRemovedItinerary,
                filteredItineraries,
                routingErrors,
                debugTimingAggregator
        );
    }

    private void routeDirectStreet(
            List<Itinerary> itineraries,
            Collection<RoutingError> routingErrors
    ) {
        debugTimingAggregator.startedDirectStreetRouter();
        try {
            itineraries.addAll(DirectStreetRouter.route(router, request));
        } catch (RoutingValidationException e) {
            routingErrors.addAll(e.getRoutingErrors());
        } finally {
            debugTimingAggregator.finishedDirectStreetRouter();
        }
    }

    private void routeDirectFlex(
            List<Itinerary> itineraries,
            Collection<RoutingError> routingErrors
    ) {
        if (!OTPFeature.FlexRouting.isOn()) {
            return;
        }

        debugTimingAggregator.startedDirectFlexRouter();
        try {
            itineraries.addAll(DirectFlexRouter.route(router, request));
        } catch (RoutingValidationException e) {
            routingErrors.addAll(e.getRoutingErrors());
        } finally {
            debugTimingAggregator.finishedDirectFlexRouter();
        }
    }

    private void routeTransit(
            List<Itinerary> itineraries,
            Collection<RoutingError> routingErrors
    ) {
        debugTimingAggregator.startedTransitRouting();
        try {
            var transitResults = TransitRouter.route(
                    request,
                    router,
                    searchStartTime,
                    debugTimingAggregator
            );
            raptorSearchParamsUsed = transitResults.getSearchParams();
            itineraries.addAll(transitResults.getItineraries());
        } catch (RoutingValidationException e) {
            routingErrors.addAll(e.getRoutingErrors());
        } finally {
            debugTimingAggregator.finishedTransitRouter();
        }
    }
}
