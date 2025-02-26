# Transmodel GraphQL API

## Contact Info

- Entur, Norway


## Changelog

- Initial version of Transmodel Graph QL API (September 2019)
- Added support for multimodal StopPlaces (November 2019)
- Fix bug querying stopPlaces [#3591](https://github.com/opentripplanner/OpenTripPlanner/pull/3591)
- Fix the field bikesAllowed [#3586](https://github.com/opentripplanner/OpenTripPlanner/pull/3586)
- Add triangle factors for bicycle routing [#3585](https://github.com/opentripplanner/OpenTripPlanner/pull/3585)
- Fix correct type for BookingArrangementType#latestBookingDay

## Documentation

This is the official Entur OTP2 API. The terminology is based on the Transmodel(NeTEx) with some 
limitations/simplification. It provides both a routing API (trip query) and index API for transit 
data. 

Entur provide a [GraphQL explorer](https://api.entur.io/graphql-explorer) where you may browse the
GraphQL schema and try your own queries.

After enabling this feature (see below), the endpoint is available at: `http://localhost:8080/otp/routers/default/transmodel/index/graphql`
 
### OTP2 Official GraphQL API (Not available) 

We **plan** to make a new offical OTP2 API, replacing the REST API. The plan is to base the new API
on this API and the [Legacy GraphQL Api](LegacyGraphQLApi.md). The new API will most likely have 2 
"translations": A GTFS version and a Transmodel version, we will try to keep the semantics the same.  
 
 
### Configuration

To enable this you need to add the feature `SandboxAPITransmodelApi`.
 
 
