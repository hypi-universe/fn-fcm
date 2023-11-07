# Google Places

This function provides access to Google's places API.

## Usage

You can use it by by adding a function to your Hypi `Query` or `Mutation` types under the schema.
If you don't have these types, add them, if you do, modify them. For example

```graphql
type Query {
    findGooglePlaces(action: String, radius: Int!, lat: Float!, long: Float!): Json @fn(name: "google-places", version: "v1.1", src: "01E8TQXPF01QR7QYFZA038DM2P", env: ["GOOGLE_PLACES_KEY"])
}
```

This example shows the required parameters for this function. Any of the arguments listed below can be freely added.
The name `findGooglePlaces` is arbitrary, you can name it anything you like.
The return type is `Json` but you can create a custom type and return that instead. 
Note that the structure of the custom type must match the structure returned from this function.

`GOOGLE_PLACES_KEY` env is required. You create an environment variable in your Hypi app with this name and provide the value on each instance that uses this function.
`action` - see arguments below

## Env keys

* `GOOGLE_PLACES_KEY` is required - it is the API key for the Google Places API

## Arguments

* `action: String` - indicates what action to take. Supported actions are 
  * `details` see https://developers.google.com/maps/documentation/places/web-service/details
    * `placeid` - the Google place ID
    * `fields` - the list of fields needed for the requested place
  * `photo` see https://developers.google.com/maps/documentation/places/web-service/photos
    * `photoreference` the reference obtained from the `search-nearby` action
    * `maxheight` - see maxwidth
    * `maxwidth` - Both the maxheight and maxwidth properties accept an integer between 1 and 1600.
  * `search-nearby-next-page` see https://developers.google.com/maps/documentation/places/web-service/search-nearby#pagetoken
    * `nextpagetoken: String` - pagination token returned by the Google places API, used to fetch the next set of results
  * `search-nearby` see https://developers.google.com/maps/documentation/places/web-service/search-nearby
    * `lat: Float` - The latitude
    * `long: Float` - THe longitude
    * `radius: Int` - meters from lat/long - max is 50K
    * `keyword: String` -
    * `opennow: Boolean` - only return places that are opened now
    * `rankby: String` - MUST be one of the supported values of either `PROMINENCE` OR `DISTANCE`
    * `minprice: String` - see max price
    * `maxprice: String` - MUST be one of the supported values of `FREE`, `INEXPENSIVE`,`MODERATE`,`EXPENSIVE`,`VERY_EXPENSIVE`
    * `type: String` - MUST be one of the supported values of
      * `ACCOUNTING`
      * `AIRPORT`
      * `AMUSEMENT_PARK`
      * `AQUARIUM`
      * `ART_GALLERY`
      * `ATM`
      * `BAKERY`
      * `BANK`
      * `BAR`
      * `BEAUTY_SALON`
      * `BICYCLE_STORE`
      * `BOOK_STORE`
      * `BOWLING_ALLEY`
      * `BUS_STATION`
      * `CAFE`
      * `CAMPGROUND`
      * `CAR_DEALER`
      * `CAR_RENTAL`
      * `CAR_REPAIR`
      * `CAR_WASH`
      * `CASINO`
      * `CEMETERY`
      * `CHURCH`
      * `CITY_HALL`
      * `CLOTHING_STORE`
      * `CONVENIENCE_STORE`
      * `COURTHOUSE`
      * `DENTIST`
      * `DEPARTMENT_STORE`
      * `DOCTOR`
      * `DRUGSTORE`
      * `ELECTRICIAN`
      * `ELECTRONICS_STORE`
      * `EMBASSY`
      * `@Deprecated`
      * `ESTABLISHMENT`
      * `@Deprecated`
      * `FINANCE`
      * `FIRE_STATION`
      * `FLORIST`
      * `@Deprecated`
      * `FOOD`
      * `FUNERAL_HOME`
      * `FURNITURE_STORE`
      * `GAS_STATION`
      * `@Deprecated`
      * `GENERAL_CONTRACTOR`
      * `GROCERY_OR_SUPERMARKET`
      * `GYM`
      * `HAIR_CARE`
      * `HARDWARE_STORE`
      * `@Deprecated`
      * `HEALTH`
      * `HINDU_TEMPLE`
      * `HOME_GOODS_STORE`
      * `HOSPITAL`
      * `INSURANCE_AGENCY`
      * `JEWELRY_STORE`
      * `LAUNDRY`
      * `LAWYER`
      * `LIBRARY`
      * `LIGHT_RAIL_STATION`
      * `LIQUOR_STORE`
      * `LOCAL_GOVERNMENT_OFFICE`
      * `LOCKSMITH`
      * `LODGING`
      * `MEAL_DELIVERY`
      * `MEAL_TAKEAWAY`
      * `MOSQUE`
      * `MOVIE_RENTAL`
      * `MOVIE_THEATER`
      * `MOVING_COMPANY`
      * `MUSEUM`
      * `NIGHT_CLUB`
      * `PAINTER`
      * `PARK`
      * `PARKING`
      * `PET_STORE`
      * `PHARMACY`
      * `PHYSIOTHERAPIST`
      * `@Deprecated`
      * `PLACE_OF_WORSHIP`
      * `PLUMBER`
      * `POLICE`
      * `POST_OFFICE`
      * `PRIMARY_SCHOOL`
      * `REAL_ESTATE_AGENCY`
      * `RESTAURANT`
      * `ROOFING_CONTRACTOR`
      * `RV_PARK`
      * `SCHOOL`
      * `SECONDARY_SCHOOL`
      * `SHOE_STORE`
      * `SHOPPING_MALL`
      * `SPA`
      * `STADIUM`
      * `STORAGE`
      * `STORE`
      * `SUBWAY_STATION`
      * `SUPERMARKET`
      * `SYNAGOGUE`
      * `TAXI_STAND`
      * `TOURIST_ATTRACTION`
      * `TRAIN_STATION`
      * `TRANSIT_STATION`
      * `TRAVEL_AGENCY`
      * `UNIVERSITY`
      * `VETERINARY_CARE`
      * `ZOO`

# Build & Release

1. Make sure you've logged into the Hypi container register by running `docker login hcr.hypi.app -u hypi` and enter a token from your Hypi account as the password
2. Build the JAR and copy the dependencies `mvn clean package`
3. Build the docker image `docker build . -t hcr.hypi.app/google-places:v1.1`
4. Deploy the function `docker push hcr.hypi.app/google-places:v1.1`

`google-places` is the function name and `v1.1` is the version. Both are important for using the function later.

As one command:

```shell 
mvn clean package && docker build . -t hcr.hypi.app/google-places:v1.1 && docker push hcr.hypi.app/google-places:v1.1
```
