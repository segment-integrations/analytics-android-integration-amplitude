package com.segment.analytics.android.integrations.amplitude;

import android.app.Application;
import com.amplitude.api.AmplitudeClient;
import com.amplitude.api.Revenue;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.core.tests.BuildConfig;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;
import com.segment.analytics.test.GroupPayloadBuilder;
import com.segment.analytics.test.IdentifyPayloadBuilder;
import com.segment.analytics.test.ScreenPayloadBuilder;
import com.segment.analytics.test.TrackPayloadBuilder;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static com.segment.analytics.Utils.createTraits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 18, manifest = Config.NONE)
public class AmplitudeTest {
  @Mock Application application;
  @Mock AmplitudeClient amplitude;
  @Mock Analytics analytics;
  AmplitudeIntegration integration;

  AmplitudeIntegration.Provider mockProvider = new AmplitudeIntegration.Provider() {
    @Override public AmplitudeClient get() {
      return amplitude;
    }
  };

  @Before public void setUp() {
    initMocks(this);

    when(analytics.getApplication()).thenReturn(application);
    when(analytics.logger("Amplitude")).thenReturn(Logger.with(VERBOSE));

    integration =
        new AmplitudeIntegration(mockProvider, analytics, new ValueMap().putValue("apiKey", "foo"));

    Mockito.reset(amplitude);
  }

  @Test public void initialize() {
    integration = new AmplitudeIntegration(mockProvider, analytics,
        new ValueMap().putValue("apiKey", "foo")
            .putValue("trackAllPages", true)
            .putValue("trackCategorizedPages", false)
            .putValue("trackNamedPages", true));

    assertThat(integration.trackAllPages).isTrue();
    assertThat(integration.trackCategorizedPages).isFalse();
    assertThat(integration.trackNamedPages).isTrue();

    verify(amplitude).initialize(application, "foo");
    verify(amplitude).enableForegroundTracking(application);
    verify(amplitude).trackSessionEvents(false);
  }

  @Test public void initializeWithDefaultArguments() {
    integration =
        new AmplitudeIntegration(mockProvider, analytics, new ValueMap().putValue("apiKey", "foo"));

    assertThat(integration.trackAllPages).isFalse();
    assertThat(integration.trackCategorizedPages).isFalse();
    assertThat(integration.trackNamedPages).isFalse();

    verify(amplitude).initialize(application, "foo");
    verify(amplitude).enableForegroundTracking(application);
    verify(amplitude).trackSessionEvents(false);
  }

  @Test public void track() {
    Properties properties = new Properties();

    integration.track(new TrackPayloadBuilder().event("foo").properties(properties).build());

    verify(amplitude).logEvent(eq("foo"), jsonEq(properties.toJsonObject()));
    verifyNoMoreInteractions(amplitude);
  }

  @Test public void trackWithRevenue() {
    Properties properties = new Properties().putRevenue(20)
            .putValue("productId", "bar")
            .putValue("quantity", 10)
            .putValue("receipt", "baz")
            .putValue("receiptSignature", "qux");
    TrackPayload trackPayload =
            new TrackPayloadBuilder().event("foo").properties(properties).build();

    integration.track(trackPayload);
    verify(amplitude).logEvent(eq("foo"), jsonEq(properties.toJsonObject()));
    verify(amplitude).logRevenue("bar", 10, 20, "baz", "qux");
  }

  @Test public void trackWithRevenueV2() {
    integration.useLogRevenueV2 = true;
    // first case missing prices field
    Properties properties = new Properties().putRevenue(20)
        .putValue("productId", "bar")
        .putValue("quantity", 10)
        .putValue("receipt", "baz")
        .putValue("receiptSignature", "qux");
    TrackPayload trackPayload =
        new TrackPayloadBuilder().event("foo").properties(properties).build();

    integration.track(trackPayload);
    verify(amplitude).logEvent(eq("foo"), jsonEq(properties.toJsonObject()));

    Revenue expectedRevenue = new Revenue().setProductId("bar")
        .setPrice(20)
        .setQuantity(1)
        .setReceipt("baz", "qux")
        .setEventProperties(properties.toJsonObject());
    verify(amplitude).logRevenueV2(revenueEq(expectedRevenue));

    // second case has price and quantity
    properties = new Properties().putRevenue(20)
            .putValue("productId", "bar")
            .putValue("quantity", 10)
            .putValue("price", 2.00)
            .putValue("receipt", "baz")
            .putValue("receiptSignature", "qux");
    trackPayload = new TrackPayloadBuilder().event("foo").properties(properties).build();

    integration.track(trackPayload);
    verify(amplitude).logEvent(eq("foo"), jsonEq(properties.toJsonObject()));

    expectedRevenue = new Revenue().setProductId("bar")
            .setPrice(2)
            .setQuantity(10)
            .setReceipt("baz", "qux")
            .setEventProperties(properties.toJsonObject());
    verify(amplitude).logRevenueV2(revenueEq(expectedRevenue));

    // third case has price but no revenue
    properties = new Properties().putValue("productId", "bar")
            .putValue("quantity", 10)
            .putValue("price", 2.00)
            .putValue("receipt", "baz")
            .putValue("receiptSignature", "qux");
    trackPayload = new TrackPayloadBuilder().event("foo").properties(properties).build();
    integration.track(trackPayload);
    verify(amplitude).logEvent(eq("foo"), jsonEq(properties.toJsonObject()));

    verifyNoMoreInteractions(amplitude);
  }

  @Test public void identify() {
    Traits traits = createTraits("foo").putAge(20).putFirstName("bar");
    IdentifyPayload payload = new IdentifyPayloadBuilder().traits(traits).build();

    integration.identify(payload);

    verify(amplitude).setUserId("foo");
    verify(amplitude).setUserProperties(jsonEq(traits.toJsonObject()));
  }

  @Test public void screen() {
    integration.trackAllPages = false;
    integration.trackCategorizedPages = false;
    integration.trackNamedPages = false;

    integration.screen(new ScreenPayloadBuilder().category("foo").build());

    verifyNoMoreInteractions(amplitude);
  }

  @Test public void screenTrackNamedPages() {
    integration.trackAllPages = false;
    integration.trackCategorizedPages = false;
    integration.trackNamedPages = true;

    integration.screen(new ScreenPayloadBuilder().name("bar").build());
    verifyAmplitudeLoggedEvent("Viewed bar Screen", new JSONObject());

    integration.screen(new ScreenPayloadBuilder().category("foo").build());
    verifyNoMoreInteractions(amplitude);
  }

  @Test public void screenTrackCategorizedPages() {
    integration.trackAllPages = false;
    integration.trackCategorizedPages = true;
    integration.trackNamedPages = false;

    integration.screen(new ScreenPayloadBuilder().category("foo").build());
    verifyAmplitudeLoggedEvent("Viewed foo Screen", new JSONObject());

    integration.screen(new ScreenPayloadBuilder().name("foo").build());
    verifyNoMoreInteractions(amplitude);
  }

  @Test public void screenTrackAllPages() {
    integration.trackAllPages = true;
    integration.trackCategorizedPages = false;
    integration.trackNamedPages = false;

    integration.screen(new ScreenPayloadBuilder().category("foo").build());
    verifyAmplitudeLoggedEvent("Viewed foo Screen", new JSONObject());

    integration.screen(new ScreenPayloadBuilder().name("bar").build());
    verifyAmplitudeLoggedEvent("Viewed bar Screen", new JSONObject());

    integration.screen(new ScreenPayloadBuilder().category("bar").name("baz").build());
    verifyAmplitudeLoggedEvent("Viewed baz Screen", new JSONObject());
  }

  @Test public void group() {
    Traits traits = createTraits("foo").putAge(20).putFirstName("bar");
    GroupPayload payload = new GroupPayloadBuilder().groupId("testGroupId").traits(traits).build();

    integration.group(payload);

    verify(amplitude).setGroup("[Segment] Group", "testGroupId");
  }

  @Test public void flush() {
    integration.flush();

    verify(amplitude).uploadEvents();
  }

  @Test public void reset() {
    integration.reset();

    verify(amplitude).regenerateDeviceId();

    // Previously we called clearUserProperties() which was incorrect.
    verify(amplitude, never()).clearUserProperties();
  }

  private void verifyAmplitudeLoggedEvent(String event, JSONObject jsonObject) {
    verify(amplitude).logEvent(eq(event), jsonEq(jsonObject));
  }

  public static JSONObject jsonEq(JSONObject expected) {
    return argThat(new JSONObjectMatcher(expected));
  }

  private static class JSONObjectMatcher extends TypeSafeMatcher<JSONObject> {
    private final JSONObject expected;

    private JSONObjectMatcher(JSONObject expected) {
      this.expected = expected;
    }

    @Override public boolean matchesSafely(JSONObject jsonObject) {
      // todo: this relies on having the same order
      return expected.toString().equals(jsonObject.toString());
    }

    @Override public void describeTo(Description description) {
      description.appendText(expected.toString());
    }
  }

  public static Revenue revenueEq(Revenue expected) {
    return argThat(new RevenueMatcher(expected));
  }

  private static class RevenueMatcher extends TypeSafeMatcher<Revenue> {
    private final Revenue expected;

    private RevenueMatcher(Revenue expected) {
      this.expected = expected;
    }

    @Override public boolean matchesSafely(Revenue revenue) {
      // Revenue class has a custom equals method
      return expected.equals(revenue);
    }

    @Override public void describeTo(Description description) {
      description.appendText(expected.toString());
    }
  }
}
