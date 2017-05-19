package com.segment.analytics.android.integrations.amplitude;

import com.amplitude.api.AmplitudeClient;
import com.amplitude.api.Revenue;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;

import org.json.JSONObject;

import static com.segment.analytics.internal.Utils.isNullOrEmpty;

/**
 * Amplitude is an event tracking and segmentation tool for your mobile apps. By analyzing the
 * actions your users perform you can gain a better understanding of how they use your app.
 *
 * @see <a href="https://amplitude.com">Amplitude</a>
 * @see <a href="https://segment.com/docs/integrations/amplitude/">Amplitude Integration</a>
 * @see <a href="https://github.com/amplitude/Amplitude-Android">Amplitude Android SDK</a>
 */
public class AmplitudeIntegration extends Integration<AmplitudeClient> {
  public static final Factory FACTORY =
      new Factory() {
        @Override
        public Integration<?> create(ValueMap settings, Analytics analytics) {
          return new AmplitudeIntegration(Provider.REAL, analytics, settings);
        }

        @Override
        public String key() {
          return AMPLITUDE_KEY;
        }
      };
  private static final String AMPLITUDE_KEY = "Amplitude";
  private static final String VIEWED_EVENT_FORMAT = "Viewed %s Screen";

  final AmplitudeClient amplitude;
  final Logger logger;
  // mutable for testing.
  boolean trackAllPages;
  boolean trackCategorizedPages;
  boolean trackNamedPages;
  boolean useLogRevenueV2;

  // Using PowerMockito fails with https://cloudup.com/c5JPuvmTCaH. So we introduce a provider
  // abstraction to mock what AmplitudeClient.getInstance() returns.
  interface Provider {
    AmplitudeClient get();

    Provider REAL =
        new Provider() {
          @Override
          public AmplitudeClient get() {
            return AmplitudeClient.getInstance();
          }
        };
  }

  AmplitudeIntegration(Provider provider, Analytics analytics, ValueMap settings) {
    amplitude = provider.get();
    trackAllPages = settings.getBoolean("trackAllPages", false);
    trackCategorizedPages = settings.getBoolean("trackCategorizedPages", false);
    trackNamedPages = settings.getBoolean("trackNamedPages", false);
    useLogRevenueV2 = settings.getBoolean("useLogRevenueV2", false);
    logger = analytics.logger(AMPLITUDE_KEY);

    String apiKey = settings.getString("apiKey");
    amplitude.initialize(analytics.getApplication(), apiKey);
    logger.verbose("AmplitudeClient.getInstance().initialize(context, %s);", apiKey);

    amplitude.enableForegroundTracking(analytics.getApplication());
    logger.verbose("AmplitudeClient.getInstance().enableForegroundTracking(context);");

    boolean trackSessionEvents = settings.getBoolean("trackSessionEvents", false);
    amplitude.trackSessionEvents(trackSessionEvents);
    logger.verbose("AmplitudeClient.getInstance().trackSessionEvents(%s);", trackSessionEvents);
  }

  @Override
  public AmplitudeClient getUnderlyingInstance() {
    return amplitude;
  }

  @Override
  public void identify(IdentifyPayload identify) {
    super.identify(identify);

    String userId = identify.userId();
    amplitude.setUserId(userId);
    logger.verbose("AmplitudeClient.getInstance().setUserId(%s);", userId);

    JSONObject traits = identify.traits().toJsonObject();
    amplitude.setUserProperties(traits);
    logger.verbose("AmplitudeClient.getInstance().setUserProperties(%s);", traits);
  }

  @Override
  public void screen(ScreenPayload screen) {
    super.screen(screen);
    if (trackAllPages) {
      event(String.format(VIEWED_EVENT_FORMAT, screen.event()), screen.properties());
    } else if (trackCategorizedPages && !isNullOrEmpty(screen.category())) {
      event(String.format(VIEWED_EVENT_FORMAT, screen.category()), screen.properties());
    } else if (trackNamedPages && !isNullOrEmpty(screen.name())) {
      event(String.format(VIEWED_EVENT_FORMAT, screen.name()), screen.properties());
    }
  }

  @Override
  public void track(TrackPayload track) {
    super.track(track);
    event(track.event(), track.properties());
  }

  private void event(String name, Properties properties) {
    JSONObject propertiesJSON = properties.toJsonObject();
    amplitude.logEvent(name, propertiesJSON);
    logger.verbose("AmplitudeClient.getInstance().logEvent(%s, %s);", name, propertiesJSON);

    // use containsKey since revenue can have negative values
    if (properties.containsKey("revenue")) {
      if (useLogRevenueV2) {
        double price = properties.getDouble("price", -1);
        int quantity = properties.getInt("quantity", 1);

        // if no price, fallback to using revenue
        if (!properties.containsKey("price")) {
          price = properties.getDouble("revenue", -1);
          quantity = 1;
        }

        Revenue ampRevenue = new Revenue().setPrice(price).setQuantity(quantity);
        if (properties.containsKey("productId")) {
          ampRevenue.setProductId(properties.getString("productId"));
        }
        if (properties.containsKey("revenueType")) {
          ampRevenue.setRevenueType(properties.getString("revenueType"));
        }
        if (properties.containsKey("receipt") && properties.containsKey("receiptSignature")) {
          ampRevenue.setReceipt(
              properties.getString("receipt"), properties.getString("receiptSignature"));
        }
        ampRevenue.setEventProperties(propertiesJSON);
        amplitude.logRevenueV2(ampRevenue);
        logger.verbose("AmplitudeClient.getInstance().logRevenueV2(%s, %s);", price, quantity);
      } else {
        // fallback to using logRevenue v1
        double revenue = properties.getDouble("revenue", -1);
        String productId = properties.getString("productId");
        int quantity = properties.getInt("quantity", 0);
        String receipt = properties.getString("receipt");
        String receiptSignature = properties.getString("receiptSignature");
        amplitude.logRevenue(productId, quantity, revenue, receipt, receiptSignature);
        logger.verbose(
            "AmplitudeClient.getInstance().logRevenue(%s, %s, %s, %s, %s);",
            productId, quantity, revenue, receipt, receiptSignature);
      }
    }
  }

  @Override
  public void group(GroupPayload group) {
    String groupId = group.groupId();
    amplitude.setGroup("[Segment] Group", groupId);
    logger.verbose("AmplitudeClient.getInstance().setGroup([Segment] Group, %s);", groupId);
  }

  @Override
  public void flush() {
    super.flush();

    amplitude.uploadEvents();
    logger.verbose("AmplitudeClient.getInstance().uploadEvents();");
  }

  @Override
  public void reset() {
    super.reset();

    amplitude.regenerateDeviceId();
    logger.verbose("AmplitudeClient.getInstance().regenerateDeviceId();");
  }
}
