package app.hypi.fn;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import io.hypi.arc.base.JSON;
import io.hypi.arc.base.http.HttpParams;
import io.hypi.arc.base.http.HypiHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

public class Main {
  private static final Logger log = LoggerFactory.getLogger(Main.class);
  private static final String actions = "[send,send-multiple,send-to-topic,subscribe,unsubscribe]";
  private static final HypiHttpClient http = new HypiHttpClient(false);
  private static final Map<String, FirebaseApp> instances = new ConcurrentHashMap<>();

  public Object invoke(Map<String, Object> input) throws Exception {
    String svcAccStr = getStrInput(input, "env", "FCM_SVC_ACC_JSON", "Missing environment variable FCM_SVC_ACC_JSON");
    String action = getStrInput(input, "args", "action", "Missing argument action");

    GoogleCredentials credentials;
    try {
      credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(svcAccStr.getBytes(UTF_8)));
    } catch (IOException e) {
      log.error("Cant initialize GoogleCredentials from stream", e);
      throw new IllegalStateException("Cant initialize GoogleCredentials from stream" + " , " + e.getMessage());
    }
    FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
    var app = instances.computeIfAbsent("hypi-fcm-fn" + svcAccStr.hashCode(), k -> FirebaseApp.initializeApp(options, k));
    var fcm = FirebaseMessaging.getInstance(app);
    switch (action) {
      case "send" -> {
        return sendToSingleDevice(
            fcm,
            (Map<String, Object>) ofNullable(input.get("args"))
                .map(v -> ((Map) v).get("message"))
                .map(v -> v instanceof String ? JSON.parse((String) v) : v)
                .orElseThrow(() -> new IllegalArgumentException("Require message object not provided")),
            //If the client has a token then allow them to send to it
            ofNullable(getStrInput(input, "args", "token", null)).orElseGet(() -> {
              try {
                return findToken(
                    getStrInput(input, "env", "hypi.token", "Required Hypi token not provided"),
                    getStrInput(input, "env", "hypi.domain", "Required Hypi token not provided"),
                    getStrInput(input, "args", "token_src_type", "Required String argument token_src_type"),
                    getStrInput(input, "args", "token_src_field", "Required String argument token_src_field"),
                    getStrInput(input, "args", "token_src_id", "Required String argument token_src_id")
                );
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        );
      }
      case "subscribe" -> {
        return subscribe(
            fcm,
            getStrInput(input, "args", "topic", "Required String argument topic"),
            singletonList(getStrInput(input, "args", "token", "Required String argument token"))
        );
      }
      case "unsubscribe" -> {
        return unsubscribe(
            fcm,
            getStrInput(input, "args", "topic", "Required String argument topic"),
            singletonList(getStrInput(input, "args", "token", "Required String argument token"))
        );
      }
      case "send-to-topic" -> {
        return sendToTopic(
            fcm,
            (Map<String, Object>) ofNullable(input.get("args"))
                .map(v -> ((Map) v).get("message"))
                .map(v -> v instanceof String ? JSON.parse((String) v) : v)
                .orElseThrow(() -> new IllegalArgumentException("Required message object not provided")),
            getStrInput(input, "args", "topic", "Required String argument topic")
        );
      }
      default ->
          throw new UnsupportedOperationException(format("%s is not one of the supported actions %s", action, actions));
    }
  }

  private String findToken(String token, String instanceDomain, String type, String field, String id) throws ExecutionException, InterruptedException, IOException {
    var entity = http.post(
        "https://api.hypi.app/graphql",
        HttpParams.params()
            .setStringBody(format("{\n" +
                "  \"variables\": {},\n" +
                "  \"query\": \"{\\n  get(type: %s, id: \\\"%s\\\") {\\n    ... on %s {\\n      %s\\n    }\\n  }\\n}\\n\"\n" +
                "}", type, id, type, field
            ))
            .setHeaders(Map.of(
                "Authorization", token,
                "hypi-domain", instanceDomain
            ))
    ).get().getEntity();
    if (entity == null) return null;
    JsonNode node = JSON.parse(entity.getContent().readAllBytes());
    return ofNullable(node.get("data")).map(v -> v.get("get")).map(v -> v.get(field)).map(JsonNode::asText).orElse(null);
  }

  private static String getStrInput(Map<String, Object> input, String args, String arg, String errMsg) {
    var found = ofNullable(input.get(args)).filter(v -> v instanceof Map).map(v -> {
      if (arg.contains(".")) {
        for (String k : arg.split("\\.")) {
          if (v instanceof Map) v = ((Map<?, ?>) v).get(k);
          else throw new IllegalArgumentException(format("%s not found, specifically %s not provided", arg, k));
        }
        return v;
      } else {
        return ((Map) v).get(arg);
      }
    }).filter(v -> v instanceof String).map(v -> (String) v);
    if (found.isPresent()) return found.get();
    if (errMsg != null) throw new IllegalArgumentException(errMsg);
    return null;
  }

  /**
   * @return message ID
   */
  public String sendToSingleDevice(FirebaseMessaging messaging, Map<String, Object> pushMessage, String token) {
    Message.Builder message = buildPushMsg(pushMessage);
    message.setToken(token);

    log.info("Sending notification to token {}.", token);
    try {
      return messaging.send(message.build());
    } catch (FirebaseMessagingException e) {

      log.error("Failed to send notification to token " + token, e);
      throw new IllegalStateException(format("FCM code %s, with reason: %s", e.getMessagingErrorCode(), e.getMessage()), e);
    }
  }

  /**
   * @return FailedTokens list
   */
  public BatchResponse sendToMultipleDevice(FirebaseMessaging messaging, Map<String, Object> pushMessage, List<String> tokens) {

    MulticastMessage.Builder message = MulticastMessage.builder();
    buildMsgData(pushMessage, message::putAllData);
    buildNotification(pushMessage, notification -> message.setNotification(notification.build()));
    buildAndroid(pushMessage, android -> message.setAndroidConfig(android.build()));
    buildWeb(pushMessage, web -> message.setWebpushConfig(web.build()));
    buildApns(pushMessage, apns -> message.setApnsConfig(apns.build()));

    message.addAllTokens(tokens);

    log.info("Starting sending notification to tokens.");
    try {
      return messaging.sendEachForMulticast(message.build());
    } catch (FirebaseMessagingException e) {

      log.error("Failed to send notification to tokens", e);
      throw new IllegalStateException(format("FCM code %s, with reason: %s", e.getMessagingErrorCode(), e.getMessage()), e);
    }
  }

  /**
   * @return message ID
   */
  public String sendToTopic(FirebaseMessaging messaging, Map<String, Object> pushMessage, String topic) {
    Message.Builder message = buildPushMsg(pushMessage);
    message.setTopic(topic);
    log.info("Starting sending notification to topic {}.", topic);
    try {
      return messaging.send(message.build());
    } catch (FirebaseMessagingException e) {
      log.error("Failed to send notification to topic " + topic, e);
      throw new IllegalStateException(format("FCM code %s, with reason: %s", e.getMessagingErrorCode(), e.getMessage()), e);
    }
  }

  public TopicManagementResponse subscribe(FirebaseMessaging messaging, String topic, List<String> tokens) {
    log.info("Starting subscribe tokens {} to topic {}.", tokens, topic);
    try {
      return messaging.subscribeToTopic(tokens, topic);
    } catch (FirebaseMessagingException e) {
      log.error("Failed to subscribe to topic " + topic, e);
      throw new IllegalStateException(format("FCM code %s, with reason: %s", e.getMessagingErrorCode(), e.getMessage()), e);
    }
  }

  public TopicManagementResponse unsubscribe(FirebaseMessaging messaging, String topic, List<String> tokens) {
    log.info("Starting unsubscribe tokens {} to topic {}.", tokens, topic);
    try {
      return messaging.unsubscribeFromTopic(tokens, topic);
    } catch (FirebaseMessagingException e) {
      log.error("Failed to unsubscribe from topic " + topic, e.getMessage());
      throw new IllegalStateException(format("FCM code %s, with reason: %s", e.getMessagingErrorCode(), e.getMessage()), e);
    }
  }

  private Message.Builder buildPushMsg(Map<String, Object> pushMessage) {
    Message.Builder message = Message.builder();
    buildMsgData(pushMessage, message::putAllData);
    buildNotification(pushMessage, notification -> message.setNotification(notification.build()));
    buildAndroid(pushMessage, android -> message.setAndroidConfig(android.build()));
    buildWeb(pushMessage, web -> message.setWebpushConfig(web.build()));
    buildApns(pushMessage, apns -> message.setApnsConfig(apns.build()));
    return message;
  }

  private void buildApns(Map<String, Object> pushMessage, Consumer<ApnsConfig.Builder> onApns) {
    Map<String, Object> apnsMap = (Map<String, Object>) pushMessage.get("apns");
    if (apnsMap != null && !apnsMap.isEmpty()) {
      ApnsConfig.Builder apns = ApnsConfig.builder();
      ofNullable(apnsMap.get("headers")).ifPresent(hdr -> ((Map) hdr).forEach((k, v) -> {
        if (v != null) {
          apns.putHeader(k.toString(), v.toString());
        }
      }));
      ofNullable(apnsMap.get("payload")).ifPresent(apsV -> {
        Map<String, Object> apsMap = (Map<String, Object>) apsV;
        Aps.Builder aps = Aps.builder();
        ofNullable(apsMap.get("alert")).ifPresent(alert -> aps.setAlert(alert.toString()));
        ofNullable(apsMap.get("badge")).ifPresent(alert -> aps.setBadge(Integer.parseInt(alert.toString())));
        ofNullable(apsMap.get("sound")).ifPresent(alert -> aps.setSound(alert.toString()));
        ofNullable(apsMap.get("category")).ifPresent(alert -> aps.setCategory(alert.toString()));
        ofNullable(apsMap.get("contentAvailable")).ifPresent(alert -> aps.setContentAvailable((boolean) alert));
        ofNullable(apsMap.get("mutableContent")).ifPresent(alert -> aps.setMutableContent((boolean) alert));
        ofNullable(apsMap.get("threadId")).ifPresent(alert -> aps.setThreadId((alert.toString())));
        apns.setAps(aps.build());
      });
      ofNullable(apnsMap.get("fcmOptions")).ifPresent(optsV -> {
        Map<String, Object> optsMap = (Map<String, Object>) optsV;
        if (!optsMap.isEmpty()) {
          ApnsFcmOptions.Builder opts = ApnsFcmOptions.builder();
          ofNullable(optsMap.get("analyticsLabel")).ifPresent(v -> opts.setAnalyticsLabel(v.toString()));
          ofNullable(optsMap.get("image")).ifPresent(v -> opts.setImage(v.toString()));
          apns.setFcmOptions(opts.build());
        }
      });
      onApns.accept(apns);
    }
  }

  private void buildWeb(Map<String, Object> pushMessage, Consumer<WebpushConfig.Builder> onWeb) {
    Map<String, Object> webpushCfg = (Map<String, Object>) pushMessage.get("webpush");
    if (webpushCfg != null && !webpushCfg.isEmpty()) {
      WebpushConfig.Builder web = WebpushConfig.builder();
      Map<String, Object> headersMap = (Map<String, Object>) webpushCfg.get("headers");
      if (headersMap != null && !headersMap.isEmpty()) {
        headersMap.forEach((k, v) -> {
          if (v != null) {
            web.putHeader(k, v.toString());
          }
        });
      }
      Map<String, Object> dataMap = (Map<String, Object>) webpushCfg.get("data");
      if (dataMap != null && !dataMap.isEmpty()) {
        dataMap.forEach((k, v) -> {
          if (v != null) {
            web.putData(k, v.toString());
          }
        });
      }
      Map<String, Object> notificationMap = (Map<String, Object>) webpushCfg.get("notification");
      if (notificationMap != null && !notificationMap.isEmpty()) {
        WebpushNotification.Builder wpn = WebpushNotification.builder();
        ofNullable(notificationMap.get("body")).ifPresent(v -> wpn.setBody((String) v));
        ofNullable(notificationMap.get("image")).ifPresent(v -> wpn.setImage((String) v));
        ofNullable(notificationMap.get("icon")).ifPresent(v -> wpn.setIcon((String) v));
        ofNullable(notificationMap.get("tag")).ifPresent(v -> wpn.setTag((String) v));
        ofNullable(notificationMap.get("badge")).ifPresent(v -> wpn.setBadge((String) v));
        ofNullable(notificationMap.get("language")).ifPresent(v -> wpn.setLanguage((String) v));
        ofNullable(notificationMap.get("requireInteraction")).ifPresent(v -> wpn.setRequireInteraction(Boolean.parseBoolean(v.toString())));
        ofNullable(notificationMap.get("silent")).ifPresent(v -> wpn.setSilent(Boolean.parseBoolean(v.toString())));
        ofNullable(notificationMap.get("renotify")).ifPresent(v -> wpn.setRenotify(Boolean.parseBoolean(v.toString())));
        ofNullable(notificationMap.get("timestamp")).ifPresent(v -> wpn.setTimestampMillis(Long.parseLong(v.toString())));
        ofNullable(notificationMap.get("direction")).ifPresent(v -> wpn.setDirection(WebpushNotification.Direction.valueOf(v.toString())));
        web.setNotification(wpn.build());
      }
      onWeb.accept(web);
    }
  }

  private void buildAndroid(Map<String, Object> pushMessage, Consumer<AndroidConfig.Builder> onAndroid) {
    Map<String, Object> androidCfg = (Map<String, Object>) pushMessage.get("android");
    if (androidCfg != null && !androidCfg.isEmpty()) {
      AndroidConfig.Builder android = AndroidConfig.builder();
      ofNullable(androidCfg.get("collapseKey")).ifPresent(v -> android.setCollapseKey(v.toString()));
      ofNullable(androidCfg.get("priority")).ifPresent(v -> {//
        android.setPriority(v.toString().toLowerCase().contentEquals("normal") ? AndroidConfig.Priority.NORMAL : AndroidConfig.Priority.HIGH);
      });
      ofNullable(androidCfg.get("ttl")).ifPresent(v -> android.setTtl(Long.parseLong(v.toString())));
      ofNullable(androidCfg.get("restrictedPackageName")).ifPresent(v -> android.setRestrictedPackageName(v.toString()));
      ofNullable(androidCfg.get("directBootOk")).ifPresent(v -> android.setDirectBootOk(Boolean.parseBoolean(v.toString())));

      ofNullable(androidCfg.get("notification")).ifPresent(androidNotificationMap -> {
        AndroidNotification.Builder androidNotification = AndroidNotification.builder();
        Map<String, Object> anm = (Map<String, Object>) androidNotificationMap;
        ofNullable(anm.get("title")).ifPresent(v -> androidNotification.setTitle(v.toString()));
        ofNullable(anm.get("body")).ifPresent(v -> androidNotification.setBody(v.toString()));
        ofNullable(anm.get("icon")).ifPresent(v -> androidNotification.setIcon(v.toString()));
        ofNullable(anm.get("color")).ifPresent(v -> androidNotification.setColor(v.toString()));
        ofNullable(anm.get("sound")).ifPresent(v -> androidNotification.setSound(v.toString()));
        ofNullable(anm.get("tag")).ifPresent(v -> androidNotification.setTag(v.toString()));
        ofNullable(anm.get("clickAction")).ifPresent(v -> androidNotification.setClickAction(v.toString()));
        ofNullable(anm.get("bodyLocKey")).ifPresent(v -> androidNotification.setBodyLocalizationKey(v.toString()));
        //ofNullable(anm.get("bodyLocArgs")).ifPresent(v -> androidNotification.setloc(v.toString()));
        ofNullable(anm.get("titleLocKey")).ifPresent(v -> androidNotification.setTitleLocalizationKey(v.toString()));
        ofNullable(anm.get("channelId")).ifPresent(v -> androidNotification.setChannelId(v.toString()));
        ofNullable(anm.get("ticker")).ifPresent(v -> androidNotification.setTicker(v.toString()));
        ofNullable(anm.get("sticky")).ifPresent(v -> androidNotification.setSticky(Boolean.parseBoolean(v.toString())));
        ofNullable(anm.get("eventTime")).ifPresent(v -> androidNotification.setEventTimeInMillis(Long.parseLong(v.toString())));
        ofNullable(anm.get("localOnly")).ifPresent(v -> androidNotification.setLocalOnly(Boolean.parseBoolean(v.toString())));
        ofNullable(anm.get("notificationPriority")).ifPresent(v -> androidNotification.setPriority(AndroidNotification.Priority.valueOf(v.toString())));
        ofNullable(anm.get("defaultSound")).ifPresent(v -> androidNotification.setDefaultSound(Boolean.parseBoolean(v.toString())));
        ofNullable(anm.get("defaultVibrateTimings")).ifPresent(v -> androidNotification.setDefaultVibrateTimings(Boolean.parseBoolean(v.toString())));
        ofNullable(anm.get("defaultLightSettings")).ifPresent(v -> androidNotification.setDefaultLightSettings(Boolean.parseBoolean(v.toString())));
        ofNullable(anm.get("vibrateTimings")).ifPresent(v -> {
          Object[] arr = ((Object[]) v);
          long[] timings = new long[arr.length];
          for (int i = 0; i < arr.length; i++) {
            timings[i] = (long) arr[i];
          }
          androidNotification.setVibrateTimingsInMillis(timings);
        });
        ofNullable(anm.get("visibility")).ifPresent(v -> {//
          androidNotification.setVisibility(AndroidNotification.Visibility.valueOf(v.toString()));
        });
        ofNullable(anm.get("notificationCount")).ifPresent(v -> {//
          androidNotification.setNotificationCount(Integer.parseInt(v.toString()));
        });
        ofNullable(anm.get("lightSettings")).ifPresent(v -> {
          Map<String, Object> lsm = (Map<String, Object>) v;
          if (lsm.isEmpty()) {
            LightSettings.Builder lightSettings = LightSettings.builder();
            ofNullable(anm.get("lightOnDuration")).ifPresent(lo -> lightSettings.setLightOnDurationInMillis(Long.parseLong(lo.toString())));
            ofNullable(anm.get("lightOffDuration")).ifPresent(lo -> lightSettings.setLightOffDurationInMillis(Long.parseLong(lo.toString())));
            androidNotification.setLightSettings(lightSettings.build());
          }
        });
        ofNullable(anm.get("image")).ifPresent(v -> androidNotification.setImage(v.toString()));
        android.setNotification(androidNotification.build());
      });

      ofNullable(androidCfg.get("fcmOptions")).ifPresent(opts -> {
        AndroidFcmOptions.Builder fcmOptions = AndroidFcmOptions.builder();
        ofNullable(((Map) opts).get("analyticsLabel")).ifPresent(v -> fcmOptions.setAnalyticsLabel((String) v));
        android.setFcmOptions(fcmOptions.build());
      });

      onAndroid.accept(android);
    }
  }

  private void buildNotification(Map<String, Object> pushMessage, Consumer<Notification.Builder> onNotification) {
    Map<String, Object> notificationMsg = (Map<String, Object>) pushMessage.get("notification");
    if (notificationMsg != null && !notificationMsg.isEmpty()) {
      Notification.Builder notification = Notification.builder();
      ofNullable(notificationMsg.get("title")).ifPresent(o -> notification.setTitle(o.toString()));
      ofNullable(notificationMsg.get("body")).ifPresent(o -> notification.setBody(o.toString()));
      ofNullable(notificationMsg.get("image")).ifPresent(o -> notification.setImage(o.toString()));
      onNotification.accept(notification);
    }
  }

  private void buildMsgData(Map<String, Object> pushMessage, Consumer<Map<String, String>> onData) {
    Map<String, Object> dataMsg = (Map<String, Object>) pushMessage.get("data");
    if (dataMsg != null) {
      Map<String, String> data = dataMsg.entrySet().stream().filter(e -> e.getValue() != null).collect(toMap(Map.Entry::getKey, Object::toString));
      onData.accept(data);
    }
  }
}
