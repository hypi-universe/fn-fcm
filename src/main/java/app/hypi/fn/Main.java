package app.hypi.fn;

import io.hypi.arc.base.JSON;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class Main {
  private static final String supportedOperations = "[search-nearby,search-nearby-next-page,details]";
  OkHttpClient client = new OkHttpClient.Builder().readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build();

  public Object invoke(Map<String, Object> input) throws Exception {
    String apiKey = ofNullable(input.get("env")).filter(v -> v instanceof Map).map(v -> ((Map) v).get("GOOGLE_PLACES_KEY")).map(Object::toString).orElseThrow(() -> new IllegalArgumentException("Missing environment variable GOOGLE_PLACES_KEY"));
    var ref = new Object() {
      public List<String> includedTypes, excludedTypes, includedPrimaryTypes, excludedPrimaryTypes;
      public Object rankPreference;
      public String nextPageToken;
      Object lat;
      Object lng;
      Object radius;
      String action;
      String placeId;
      List<String> fields;
      String photoName;
      public Integer maxHeightPx;
      public Integer maxWidthPx;
      public Integer maxResultCount;
    };
    ofNullable(input.get("args")).filter(v -> v instanceof Map).map(v -> (Map) v).ifPresent(args -> {
      ref.lat = args.get("lat");
      ref.lng = args.get("long");
      ref.action = requireNonNull((String) args.get("action"), "action parameter not provided and is required. " + "It must be onf of " + supportedOperations);
      ref.nextPageToken = (String) args.get("next_page_token");
      ref.radius = args.get("radius");
      ref.rankPreference = args.get("rank_preference");
      ref.includedTypes = ofNullable(args.get("included_types")).filter(v -> v instanceof List).map(v -> (List) v).orElse(emptyList());
      ref.includedPrimaryTypes = ofNullable(args.get("included_primary_types")).filter(v -> v instanceof List).map(v -> (List) v).orElse(emptyList());
      ref.excludedTypes = ofNullable(args.get("excluded_types")).filter(v -> v instanceof List).map(v -> (List) v).orElse(emptyList());
      ref.excludedPrimaryTypes = ofNullable(args.get("excluded_primary_types")).filter(v -> v instanceof List).map(v -> (List) v).orElse(emptyList());
      //place details params
      ref.placeId = (String) args.get("place_id");
      ref.fields = ofNullable(args.get("fields")).filter(v -> v instanceof List).map(v -> (List) v).orElse(emptyList());
      //photo params
      ref.photoName = (String) args.get("photo_name");
      ref.maxHeightPx = ofNullable(args.get("max_height_px")).map(v -> ((Number) v).intValue()).orElse(null);
      ref.maxWidthPx = ofNullable(args.get("max_width_px")).map(v -> ((Number) v).intValue()).orElse(null);
      ref.maxResultCount = ofNullable(args.get("maxresultcount")).map(v -> ((Number) v).intValue()).orElse(null);
    });
    var req = new Request.Builder();
    req.addHeader("X-Goog-Api-Key", apiKey);
    switch (ref.action) {
      case "search-nearby" -> {
        req.url("https://places.googleapis.com/v1/places:searchNearby");
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

        if (ref.radius instanceof Number) ref.radius = ((Number) ref.radius).doubleValue();
        var body = new LinkedHashMap<>();
        ofNullable(ref.fields).ifPresent(v -> req.addHeader("X-Goog-FieldMask", String.join(",", v)));
        body.put("locationRestriction", Map.of("circle", new LinkedHashMap<>() {
          {
            put("center", Map.of("latitude", ref.lat, "longitude", ref.lng));
            ofNullable(ref.radius).ifPresent(radius -> put("radius", radius));
          }
        }));
        ofNullable(ref.includedTypes).ifPresent(v -> body.put("includedTypes", v));
        ofNullable(ref.includedPrimaryTypes).ifPresent(v -> body.put("includedPrimaryTypes", v));
        ofNullable(ref.excludedTypes).ifPresent(v -> body.put("excludedTypes", v));
        ofNullable(ref.excludedPrimaryTypes).ifPresent(v -> body.put("excludedPrimaryTypes", v));
        ofNullable(ref.maxResultCount).map(Object::toString).ifPresent(v -> body.put("maxResultCount", v));
        ofNullable(ref.rankPreference).ifPresent(v -> body.put("rankPreference", v));

        return buildResponse(client.newCall(req.post(RequestBody.create(JSON.bytes(body))).build()));
      }
      case "details" -> {
        req.url("https://places.googleapis.com/v1/places/" + ref.placeId);
        ofNullable(ref.fields).ifPresent(v -> req.addHeader("X-Goog-FieldMask", String.join(",", v)));
        requireNonNull(ref.placeId, "place_id parameter is required but is not provided");
        return buildResponse(client.newCall(req.get().build()));
      }
      case "photos" -> {
        requireNonNull(ref.photoName, "photo_name parameter is required but is not provided");
        if (ref.maxHeightPx == null && ref.maxWidthPx == null) {
          throw new IllegalArgumentException("At least one of max_height_px OR max_width_px parameters and neither has been provided");
        }
        StringBuilder urlBuf = new StringBuilder(format("https://places.googleapis.com/v1/%s/media?skipHttpRedirect=true", ref.photoName));
        ofNullable(ref.maxHeightPx).ifPresent(v -> urlBuf.append("&maxHeightPx=").append(v));
        ofNullable(ref.maxWidthPx).ifPresent(v -> urlBuf.append("&maxWidthPx=").append(v));
        return buildResponse(client.newCall(req.url(urlBuf.toString()).build()));
      }
      default -> throw new UnsupportedOperationException();
    }
  }

  private Object buildResponse(Call call) throws IOException {
    try (var res = call.execute()) {
//      var hdrs = new LinkedHashMap<>();
//      for (var header : res.headers()) {
//        hdrs.put(header.getFirst(), header.getSecond());
//      }
      var entity = res.body();
//      Map<String, Object> body = null;
      if (entity != null) {
//        body = JSON.parse(entity.string());
        return JSON.parse(entity.string());
      } else {
        return null;
      }
//      Map<String, Object> finalBody = body;
//      return new LinkedHashMap<>() {
//        {
//          put("headers", hdrs);
//          put("body", finalBody);
//        }
//      };
    }
  }

  public static void main(String[] args) {
    System.out.println("Hello World");
  }
}
