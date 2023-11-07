package app.hypi.fn;

import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlaceType;
import com.google.maps.model.PriceLevel;
import com.google.maps.model.RankBy;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class Main {
  private static final String supportedOperations = "[search-nearby,search-nearby-next-page]";

  public Object invoke(Map<String, Object> input) throws IOException, InterruptedException, ApiException {
    String apiKey = ofNullable(input.get("env"))
        .filter(v -> v instanceof Map)
        .map(v -> ((Map) v).get("GOOGLE_PLACES_KEY"))
        .map(Object::toString)
        .orElseThrow(() -> new IllegalArgumentException("Missing environment variable GOOGLE_PLACES_KEY"));
    var ref = new Object() {
      public Object maxPrice;
      public Object minPrice;
      public Object type;
      public Object rankBy;
      public Object openNow;
      public Object keyword;
      public String nextPageToken;
      Object lat;
      Object lng;
      Object radius;
      String action;
    };
    ofNullable(input.get("args"))
        .filter(v -> v instanceof Map)
        .map(v -> (Map) v)
        .ifPresent(args -> {
          ref.lat = args.get("lat");
          ref.lng = args.get("long");
          ref.action = requireNonNull(
              (String) args.get("action"),
              "action parameter not provided and is required. " +
                  "It must be onf of " + supportedOperations
          );
          ref.nextPageToken = (String) args.get("nextpagetoken");
          ref.radius =  args.get("radius");
          ref.keyword =  args.get("keyword");
          ref.openNow =  args.get("openNow");
          ref.rankBy =  args.get("rankby");
          ref.type =  args.get("type");
          ref.minPrice =  args.get("minprice");
          ref.maxPrice =  args.get("maxprice");
        });
    var ctx = new GeoApiContext.Builder().apiKey(apiKey).build();
    switch (ref.action) {
      case "search-nearby" -> {
        //https://developers.google.com/maps/documentation/places/web-service/search-nearby
        requireNonNull(ref.lat, "lat parameter not provided and is required");
        requireNonNull(ref.lng, "long parameter not provided and is required");
        if (ref.lat instanceof Float) ref.lat = ((Float) ref.lat).doubleValue();
        else if (ref.lat instanceof Double) ref.lat = (Double) ref.lat;
        else if (ref.lat instanceof Number) ref.lat = ((Number) ref.lat).doubleValue();
        else throw new IllegalArgumentException("lat MUST be a number (float or double)");
        if (ref.lng instanceof Float) ref.lng = ((Float) ref.lng).doubleValue();
        else if (ref.lng instanceof Double) ref.lng = (Double) ref.lng;
        else if (ref.lng instanceof Number) ref.lng = ((Number) ref.lng).doubleValue();
        else throw new IllegalArgumentException("long MUST be a number (float or double)");

        if (ref.radius instanceof Number) ref.radius = ((Number) ref.radius).intValue();
        LatLng ll = new LatLng();
        ll.lat = (double) ref.lat;
        ll.lng = (double) ref.lng;
        var req = PlacesApi.nearbySearchQuery(ctx, ll);
        ofNullable(ref.radius).map(v -> (int) v).ifPresent(req::radius);
        ofNullable(ref.keyword).map(Object::toString).ifPresent(req::keyword);
        ofNullable(ref.openNow).map(v -> (Boolean) v).ifPresent(req::openNow);
        ofNullable(ref.rankBy).map(v -> v.toString().toUpperCase()).map(RankBy::valueOf).ifPresent(req::rankby);
        ofNullable(ref.type).map(v -> v.toString().toUpperCase()).map(PlaceType::valueOf).ifPresent(req::type);
        ofNullable(ref.minPrice).map(v -> v.toString().toUpperCase()).map(PriceLevel::valueOf).ifPresent(req::minPrice);
        ofNullable(ref.maxPrice).map(v -> v.toString().toUpperCase()).map(PriceLevel::valueOf).ifPresent(req::maxPrice);
        return req.await();
      }
      case "search-nearby-next-page" -> {
        return PlacesApi.nearbySearchNextPage(ctx,
            requireNonNull(ref.nextPageToken, "search-nearby-next-page action requires the nextPageToken parameter")
        ).await();
      }
      default -> throw new UnsupportedOperationException();
    }
  }

  public static void main(String[] args) {
    System.out.println("Hello World");
  }
}