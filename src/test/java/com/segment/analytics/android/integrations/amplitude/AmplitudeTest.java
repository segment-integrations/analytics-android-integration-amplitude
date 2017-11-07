package com.segment.analytics.android.integrations.amplitude;

import android.app.Application;

import com.amplitude.api.AmplitudeClient;
import com.amplitude.api.Identify;
import com.amplitude.api.Revenue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.segment.analytics.Analytics;
import com.segment.analytics.Options;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static com.segment.analytics.Utils.createTraits;
import static com.segment.analytics.android.integrations.amplitude.AmplitudeIntegration.getStringSet;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 18, manifest = Config.NONE)
public class AmplitudeTest {

  @Mock Application application;
  @Mock AmplitudeClient amplitude;
  @Mock Analytics analytics;
  private AmplitudeIntegration integration;
  private AmplitudeIntegration.Provider mockProvider = new AmplitudeIntegration.Provider() {
    @Override
    public AmplitudeClient get() {
      return amplitude;
    }
  };

  @Before
  public void setUp() {
    initMocks(this);

    when(analytics.getApplication()).thenReturn(application);
    when(analytics.logger("Amplitude")).thenReturn(Logger.with(VERBOSE));

    integration =
        new AmplitudeIntegration(mockProvider, analytics, new ValueMap()
                .putValue("apiKey", "foo"));

    Mockito.reset(amplitude);
  }

  @Test
  public void factory() {
    assertThat(AmplitudeIntegration.FACTORY.key()).isEqualTo("Amplitude");
  }

  @Test
  public void initialize() {
    integration = new AmplitudeIntegration(mockProvider, analytics,
        new ValueMap().putValue("apiKey", "foo")
            .putValue("trackAllPagesV2", true)
            .putValue("trackAllPages", true)
            .putValue("trackCategorizedPages", false)
            .putValue("trackNamedPages", true)
            .putValue("enableLocationListening", false)
            .putValue("useAdvertisingIdForDeviceId", true));

    assertThat(integration.trackAllPagesV2).isTrue();
    assertThat(integration.trackAllPages).isTrue();
    assertThat(integration.trackCategorizedPages).isFalse();
    assertThat(integration.trackNamedPages).isTrue();

    verify(amplitude).initialize(application, "foo");
    verify(amplitude).enableForegroundTracking(application);
    verify(amplitude).trackSessionEvents(false);
    verify(amplitude).disableLocationListening();
    verify(amplitude).useAdvertisingIdForDeviceId();
  }

  @Test
  public void initializeWithDefaultArguments() {
    integration =
        new AmplitudeIntegration(mockProvider, analytics, new ValueMap().putValue("apiKey", "foo"));

    assertThat(integration.trackAllPages).isFalse();
    assertThat(integration.trackCategorizedPages).isFalse();
    assertThat(integration.trackNamedPages).isFalse();

    verify(amplitude).initialize(application, "foo");
    verify(amplitude).enableForegroundTracking(application);
    verify(amplitude).trackSessionEvents(false);
  }

  @Test
  public void track() {
    integration.track(new TrackPayloadBuilder()
            .event("foo")
            .properties(new Properties())
            .build());

    verify(amplitude)
        .logEvent(eq("foo"), jsonEq(new JSONObject()), isNull(JSONObject.class), eq(false));
    verifyNoMoreInteractions(amplitude);
  }

  @Test
  public void trackWithGroups() throws JSONException {
    integration.track(new TrackPayloadBuilder()
        .event("foo")
        .properties(new Properties())
        .options(new Options()
            .setIntegrationOptions("Amplitude", new ValueMap()
                .putValue("groups", new ValueMap().putValue("foo", "bar"))
            )
        )
        .build());

    JSONObject groups = new JSONObject();
    groups.put("foo", "bar");
    verify(amplitude)
        .logEvent(eq("foo"), jsonEq(new JSONObject()), jsonEq(groups), eq(false));
    verifyNoMoreInteractions(amplitude);
  }

  @Test
  public void trackWithListGroups() throws JSONException {
    integration.track(new TrackPayloadBuilder()
        .event("foo")
        .properties(new Properties())
        .options(new Options()
            .setIntegrationOptions("Amplitude", new ValueMap()
                .putValue("groups", new ValueMap()
                    .putValue("sports", ImmutableList.of("basketball", "tennis")))
            )
        )
        .build());

    JSONObject groups = new JSONObject();
    groups.put("sports", new JSONArray().put("basketball").put("tennis"));
    verify(amplitude)
        .logEvent(eq("foo"), jsonEq(new JSONObject()), jsonEq(groups), eq(false));
    verifyNoMoreInteractions(amplitude);
  }

  @Test
  public void trackOutOfSession() {
    integration.track(new TrackPayloadBuilder()
            .event("foo")
            .properties(new Properties())
            .options(new Options()
                    .setIntegrationOptions("Amplitude", new ValueMap()
                            .putValue("outOfSession", true)
                    )
            )
            .build());

    verify(amplitude)
            .logEvent(eq("foo"), jsonEq(new JSONObject()), isNull(JSONObject.class), eq(true));
  }

  @Test
  public void trackOutOfSessionOptionsNull() {
    integration.track(new TrackPayloadBuilder()
            .event("foo")
            .properties(new Properties())
            .build());

    verify(amplitude)
            .logEvent(eq("foo"), jsonEq(new JSONObject()), isNull(JSONObject.class), eq(false));
  }

  @Test
  public void trackOutOfSessionNotInstanceOfBoolean() {
    integration.track(new TrackPayloadBuilder()
            .event("foo")
            .properties(new Properties())
            .options(new Options()
                    .setIntegrationOptions("Amplitude", new ValueMap()
                            .putValue("outOfSession", "string")
                    )
            )
            .build());

    verify(amplitude)
            .logEvent(eq("foo"), jsonEq(new JSONObject()), isNull(JSONObject.class), eq(false));
  }

  @Test
  public void trackOutOfSessionKeyNotSet() {
    integration.track(new TrackPayloadBuilder()
            .event("foo")
            .properties(new Properties())
            .options(new Options()
                    .setIntegrationOptions("Amplitude", new ValueMap()
                            .putValue("randomSetting", "testing")
                    )
            )
            .build());

    verify(amplitude)
            .logEvent(eq("foo"), jsonEq(new JSONObject()), isNull(JSONObject.class), eq(false));
  }

  @SuppressWarnings("deprecation")
  @Test
  public void trackWithRevenue() {
    Properties properties = new Properties()
        .putRevenue(20)
        .putValue("productId", "bar")
        .putValue("quantity", 10)
        .putValue("receipt", "baz")
        .putValue("receiptSignature", "qux");
    TrackPayload trackPayload =
        new TrackPayloadBuilder().event("foo").properties(properties).build();

    integration.track(trackPayload);
    verify(amplitude)
        .logEvent(eq("foo"), jsonEq(properties.toJsonObject()), isNull(JSONObject.class), eq(false));
    verify(amplitude).logRevenue("bar", 10, 20, "baz", "qux");
  }

  @SuppressWarnings("deprecation")
  @Test
  public void trackWithTotal() {
    Properties properties = new Properties()
        .putTotal(15)
        .putValue("productId", "bar")
        .putValue("quantity", 10)
        .putValue("receipt", "baz")
        .putValue("receiptSignature", "qux");

    integration.track(new TrackPayloadBuilder().event("foo").properties(properties).build());

    verify(amplitude)
            .logEvent(eq("foo"), jsonEq(properties.toJsonObject()), isNull(JSONObject.class), eq(false));
    verify(amplitude).logRevenue("bar", 10, 15, "baz", "qux");
  }

  @Test
  public void trackWithRevenueV2() {
    integration.useLogRevenueV2 = true;
    Properties properties = new Properties()
        .putRevenue(20)
        .putValue("productId", "bar")
        .putValue("quantity", 10)
        .putValue("receipt", "baz")
        .putValue("receiptSignature", "qux");

    integration.track(new TrackPayloadBuilder().event("foo").properties(properties).build());

    verify(amplitude)
        .logEvent(eq("foo"), jsonEq(properties.toJsonObject()), isNull(JSONObject.class), eq(false));

    Revenue expectedRevenue = new Revenue()
        .setProductId("bar")
        .setPrice(20)
        .setQuantity(1)
        .setReceipt("baz", "qux")
        .setEventProperties(properties.toJsonObject());

    verify(amplitude).logRevenueV2(expectedRevenue);
  }

  @Test
  public void trackWithRevenueV2Price() {
    Properties properties = new Properties()
            .putValue("productId", "bar")
            .putValue("quantity", 10)
            .putValue("price", 2.00)
            .putValue("receipt", "baz")
            .putValue("receiptSignature", "qux");

    integration.track(new TrackPayloadBuilder().event("foo").properties(properties).build());

    verify(amplitude)
            .logEvent(eq("foo"), jsonEq(properties.toJsonObject()), isNull(JSONObject.class), eq(false));

    verifyNoMoreInteractions(amplitude);
  }

  @Test public void trackWithRevenueV2PriceAndQuantity() {
    integration.useLogRevenueV2 = true;

    Properties properties = new Properties()
            .putRevenue(20)
            .putValue("productId", "bar")
            .putValue("quantity", 10)
            .putValue("price", 2.00)
            .putValue("receipt", "baz")
            .putValue("receiptSignature", "qux");

    integration.track(new TrackPayloadBuilder().event("foo").properties(properties).build());
    verify(amplitude)
            .logEvent(eq("foo"), jsonEq(properties.toJsonObject()), isNull(JSONObject.class), eq(false));

    Revenue expectedRevenue = new Revenue().setProductId("bar")
            .setPrice(2)
            .setQuantity(10)
            .setReceipt("baz", "qux")
            .setEventProperties(properties.toJsonObject());

    verify(amplitude).logRevenueV2(argThat(samePropertyValuesAs(expectedRevenue)));
  }

  @Test
  public void trackWithTotalV2() {
    integration.useLogRevenueV2 = true;

    Properties properties = new Properties()
        .putTotal(20)
        .putValue("productId", "bar")
        .putValue("quantity", 10)
        .putValue("receipt", "baz")
        .putValue("receiptSignature", "qux");
    TrackPayload trackPayload =
            new TrackPayloadBuilder().event("foo").properties(properties).build();

    integration.track(trackPayload);
    verify(amplitude)
            .logEvent(eq("foo"), jsonEq(properties.toJsonObject()), isNull(JSONObject.class), eq(false));

    Revenue expectedRevenue = new Revenue()
        .setProductId("bar")
        .setPrice(20)
        .setQuantity(1)
        .setReceipt("baz", "qux")
        .setEventProperties(properties.toJsonObject());

    verify(amplitude).logRevenueV2(expectedRevenue);
  }

  @Test
  public void trackWithTotalV2Price() {
    integration.useLogRevenueV2 = true;

    Properties properties = new Properties()
            .putTotal(20)
            .putValue("productId", "bar")
            .putValue("quantity", 10)
            .putValue("price", 2.00)
            .putValue("receipt", "baz")
            .putValue("receiptSignature", "qux");

    integration.track(new TrackPayloadBuilder().event("foo").properties(properties).build());

    verify(amplitude)
            .logEvent(eq("foo"), jsonEq(properties.toJsonObject()), isNull(JSONObject.class), eq(false));

    Revenue expectedRevenue = new Revenue()
            .setProductId("bar")
            .setPrice(2)
            .setQuantity(10)
            .setReceipt("baz", "qux")
            .setEventProperties(properties.toJsonObject());

    verify(amplitude).logRevenueV2(argThat(samePropertyValuesAs(expectedRevenue)));
  }

  @Test
  public void trackWithTotalV2PriceAndQuantity() {
    integration.useLogRevenueV2 = true;

    Properties properties = new Properties()
            .putTotal(20)
            .putValue("productId", "bar")
            .putValue("quantity", 10)
            .putValue("price", 2.00)
            .putValue("receipt", "baz")
            .putValue("receiptSignature", "qux");

    integration.track(new TrackPayloadBuilder().event("foo").properties(properties).build());

    verify(amplitude)
            .logEvent(eq("foo"), jsonEq(properties.toJsonObject()), isNull(JSONObject.class), eq(false));

    Revenue expectedRevenue = new Revenue()
            .setProductId("bar")
            .setPrice(2)
            .setQuantity(10)
            .setReceipt("baz", "qux")
            .setEventProperties(properties.toJsonObject());

    verify(amplitude).logRevenueV2(argThat(samePropertyValuesAs(expectedRevenue)));
  }

  @Test
  public void identify() {
    Traits traits = createTraits("foo").putAge(20).putFirstName("bar");
    IdentifyPayload payload = new IdentifyPayloadBuilder().traits(traits).build();

    integration.identify(payload);

    verify(amplitude).setUserId("foo");
    verify(amplitude).setUserProperties(jsonEq(traits.toJsonObject()));

    verifyNoMoreInteractions(amplitude);
  }

  @Test
  public void identifyWithGroups() {
    Traits traits = createTraits("foo").putAge(20).putFirstName("bar");
    IdentifyPayload payload = new IdentifyPayloadBuilder()
        .traits(traits)
        .options(new Options()
            .setIntegrationOptions("Amplitude", new ValueMap()
                .putValue("groups", new ValueMap().putValue("foo", "bar"))
            )
        )
        .build();

    integration.identify(payload);

    verify(amplitude).setUserId("foo");
    verify(amplitude).setUserProperties(jsonEq(traits.toJsonObject()));

    verify(amplitude).setGroup("foo", "bar");
  }

  @Test
  public void identifyWithListGroups() {
    Traits traits = createTraits("foo").putAge(20).putFirstName("bar");
    IdentifyPayload payload = new IdentifyPayloadBuilder()
        .traits(traits)
        .options(new Options()
            .setIntegrationOptions("Amplitude", new ValueMap()
                .putValue("groups",
                    ImmutableMap.of("sports", ImmutableList.of("basketball", "tennis")))
            )
        )
        .build();

    integration.identify(payload);

    verify(amplitude).setUserId("foo");
    verify(amplitude).setUserProperties(jsonEq(traits.toJsonObject()));

    verify(amplitude).setGroup("sports", new JSONArray().put("basketball").put("tennis"));
  }

  @Test
  public void identifyWithIncrementedTraits() {
    ValueMap settings = new ValueMap()
        .putValue("traitsToIncrement", Arrays.asList("double", "float", "integer", "long", "string"));
    integration.traitsToIncrement = getStringSet(settings, "traitsToIncrement");

    double d = 100.0;
    float f = 100.0f;
    int i = 100;
    long l = 1000L;
    String s = "random string";

    Traits traits = createTraits("foo")
        .putValue("anonymousId", "anonId")
        .putValue("double", d)
        .putValue("float", f)
        .putValue("integer", i)
        .putValue("long", l)
        .putValue("string", s);
    IdentifyPayload payload = new IdentifyPayloadBuilder().traits(traits).build();
    integration.identify(payload);

    Identify expectedIdentify = new Identify();
    expectedIdentify.set("anonymousId", "anonId");
    expectedIdentify.set("userId", "foo");
    expectedIdentify.add("double", d);
    expectedIdentify.add("float", f);
    expectedIdentify.add("integer", i);
    expectedIdentify.add("long", l);
    expectedIdentify.add("string", s);

    verify(amplitude).identify(argThat(samePropertyValuesAs(expectedIdentify)));
  }

  @Test
  public void identifyWithSetOnce() {
    ValueMap settings = new ValueMap()
        .putValue("traitsToSetOnce", Arrays.asList("double", "float", "integer", "long", "string"));
    integration.traitsToSetOnce = getStringSet(settings, "traitsToSetOnce");

    double d = 100.0;
    float f = 100.0f;
    int i = 100;
    long l = 1000L;
    String s = "random string";

    Traits traits = createTraits("foo")
        .putValue("anonymousId", "anonId")
        .putValue("double", d)
        .putValue("float", f)
        .putValue("integer", i)
        .putValue("long", l)
        .putValue("string", s);
    IdentifyPayload payload = new IdentifyPayloadBuilder().traits(traits).build();
    integration.identify(payload);

    Identify identify = new Identify();
    identify.set("anonymousId", "anonId");
    identify.set("userId", "foo");
    identify.setOnce("double", d);
    identify.setOnce("float", f);
    identify.setOnce("integer", i);
    identify.setOnce("long", l);
    identify.setOnce("string", s);

    verify(amplitude).identify(argThat(samePropertyValuesAs(identify)));
  }

  @Test
  public void screen() {
    integration.trackAllPagesV2 = false;
    integration.trackAllPages = false;
    integration.trackCategorizedPages = false;
    integration.trackNamedPages = false;

    integration.screen(new ScreenPayloadBuilder().category("foo").build());

    verifyNoMoreInteractions(amplitude);
  }

  @Test
  public void screenTrackNamedPagesWithName() {
    integration.trackAllPagesV2 = false;
    integration.trackAllPages = false;
    integration.trackCategorizedPages = false;
    integration.trackNamedPages = true;

    integration.screen(new ScreenPayloadBuilder().name("bar").build());
    verifyAmplitudeLoggedEvent("Viewed bar Screen", new JSONObject());
  }

  @Test
  public void screenTrackNamedPagesWithCategory() {
    integration.trackAllPagesV2 = false;
    integration.trackAllPages = false;
    integration.trackCategorizedPages = false;
    integration.trackNamedPages = true;

    integration.screen(new ScreenPayloadBuilder().category("foo").build());
    verifyNoMoreInteractions(amplitude);
  }

  @Test
  public void screenTrackCategorizedPages() {
    integration.trackAllPagesV2 = false;
    integration.trackAllPages = false;
    integration.trackCategorizedPages = true;
    integration.trackNamedPages = false;

    integration.screen(new ScreenPayloadBuilder().category("foo").build());
    verifyAmplitudeLoggedEvent("Viewed foo Screen", new JSONObject());

    integration.screen(new ScreenPayloadBuilder().name("foo").build());
    verifyNoMoreInteractions(amplitude);
  }

  @Test
  public void screenTrackAllPages() {
    integration.trackAllPagesV2 = false;
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

  @Test
  public void screenTrackAllPagesV2() throws JSONException {
    Properties properties = new Properties();
    properties.putValue("bar", "baz");
    integration.screen(new ScreenPayloadBuilder().name("foo").properties(properties).build());
    verifyAmplitudeLoggedEvent("Loaded a Screen", new JSONObject()
        .put("name", "foo").put("bar", "baz"));
  }

  @Test
  public void group() {
    GroupPayload payload = new GroupPayloadBuilder()
        .groupId("testGroupId")
        .build();

    integration.group(payload);

    verify(amplitude).setGroup("[Segment] Group", "testGroupId");
  }

  @Test
  public void groupWithGroupName() {
    GroupPayload payload = new GroupPayloadBuilder()
        .groupId("testGroupId")
        .groupTraits(new Traits().putName("testName"))
        .build();

    integration.group(payload);

    verify(amplitude).setGroup("testName", "testGroupId");
  }

  @Test
  public void groupWithGroupNameSettings() {
    integration.groupTypeTrait = "company";
    integration.groupValueTrait = "companyType";

    GroupPayload payload = new GroupPayloadBuilder()
        .groupId("testGroupId")
        .groupTraits(new Traits().putValue("company", "Segment").putValue("companyType", "data"))
        .build();

    integration.group(payload);

    verify(amplitude).setGroup("Segment", "data");
  }

  @Test
  public void flush() {
    integration.flush();

    verify(amplitude).uploadEvents();
  }

  @Test
  public void reset() {
    integration.reset();

    verify(amplitude).setUserId(null);
    verify(amplitude).regenerateDeviceId();

    // Previously we called clearUserProperties() which was incorrect.
    verify(amplitude, never()).clearUserProperties();
  }

  @Test
  public void groups() throws JSONException {
    assertThat(AmplitudeIntegration.groups(new TrackPayloadBuilder()
        .event("foo")
        .build()))
        .isNull();
    assertThat(AmplitudeIntegration.groups(new TrackPayloadBuilder()
        .event("foo")
        .options(new Options())
        .build()))
        .isNull();
    assertThat(AmplitudeIntegration.groups(new TrackPayloadBuilder()
        .event("foo")
        .options(new Options()
            .setIntegrationOptions("Mixpanel", new ValueMap().putValue("groups", "foo")))
        .build()))
        .isNull();
    assertThat(AmplitudeIntegration.groups(new TrackPayloadBuilder()
        .event("foo")
        .options(new Options().setIntegration("Amplitude", true))
        .build()))
        .isNull();
    assertThat(AmplitudeIntegration.groups(new TrackPayloadBuilder()
        .event("foo")
        .options(new Options().setIntegrationOptions("Amplitude", new ValueMap()))
        .build()))
        .isNull();
    assertThat(AmplitudeIntegration.groups(new TrackPayloadBuilder()
        .event("foo")
        .options(new Options()
            .setIntegrationOptions("Amplitude", new ValueMap().putValue("foo", "bar")))
        .build()))
        .isNull();

    assertThat(AmplitudeIntegration.groups(new TrackPayloadBuilder()
        .event("foo")
        .options(new Options().setIntegrationOptions("Amplitude",
            new ValueMap().putValue("groups", new ValueMap().putValue("foo", "bar")))
        )
        .build()))
        .isEqualToComparingFieldByField(new JSONObject().put("foo", "bar"));

    assertThat(AmplitudeIntegration.groups(new TrackPayloadBuilder()
        .event("foo")
        .options(new Options().setIntegrationOptions("Amplitude",
            new ValueMap().putValue("groups",
                new ValueMap().putValue("sports", ImmutableList.of("basketball", "tennis")))
            )
        )
        .build()))
        .isEqualToComparingFieldByField(new JSONObject()
            .put("sports", new JSONArray().put("basketball").put("tennis")));
  }

  private void verifyAmplitudeLoggedEvent(String event, JSONObject jsonObject) {
    verify(amplitude).logEvent(eq(event), jsonEq(jsonObject), isNull(JSONObject.class), eq(false));
  }

  public static JSONObject jsonEq(JSONObject expected) {
    return argThat(new JSONObjectMatcher(expected));
  }

  private static class JSONObjectMatcher extends TypeSafeMatcher<JSONObject> {

    private final JSONObject expected;

    private JSONObjectMatcher(JSONObject expected) {
      this.expected = expected;
    }

    @Override
    public boolean matchesSafely(JSONObject jsonObject) {
      // todo: this relies on having the same order
      return expected.toString().equals(jsonObject.toString());
    }

    @Override
    public void describeTo(Description description) {
      description.appendText(expected.toString());
    }
  }
}
