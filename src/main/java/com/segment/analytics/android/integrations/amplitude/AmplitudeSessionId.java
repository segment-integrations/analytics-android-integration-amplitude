package com.segment.analytics.android.integrations.amplitude;

import com.segment.analytics.Middleware;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.AliasPayload;
import com.segment.analytics.integrations.BasePayload;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;
import java.util.Calendar;

public class AmplitudeSessionId implements Middleware {

  private static final String KEY = "Actions Amplitude";

  private static final long FIRE_TIME = 300 * 1000; // 300 seconds

  private long sessionID = -1;

  @Override
  public void intercept(Chain chain) {
    BasePayload payload = chain.payload();

    switch (payload.type()) {
      case alias:
        payload = alias((AliasPayload) payload);
        break;
      case group:
        payload = group((GroupPayload) payload);
        break;
      case identify:
        payload = identify((IdentifyPayload) payload);
        break;
      case screen:
        payload = screen((ScreenPayload) payload);
        break;
      case track:
        payload = track((TrackPayload) payload);
        break;
    }

    chain.proceed(payload);
  }

  private BasePayload insertSession(BasePayload payload) {
    return payload
        .toBuilder()
        .integration(KEY, new ValueMap().putValue("session_id", getSessionId()))
        .build();
  }

  private BasePayload alias(AliasPayload payload) {
    return insertSession(payload);
  }

  private BasePayload group(GroupPayload payload) {
    return insertSession(payload);
  }

  private BasePayload identify(IdentifyPayload payload) {
    return insertSession(payload);
  }

  private BasePayload screen(ScreenPayload payload) {
    return insertSession(payload);
  }

  private BasePayload track(TrackPayload payload) {
    if (payload.event().equals("Application Backgrounded")) {
      onBackground();
    } else if (payload.event().equals("Application Opened")) {
      onForeground();
    }
    return insertSession(payload);
  }

  private void onBackground() {
    stopSession();
  }

  private void onForeground() {
    startSession();
  }

  private long getSessionId() {
    if (sessionID != -1) {
      // if sessionId is not -1, then we reset to curTime (essentially creating a new session)
      // https://help.amplitude.com/hc/en-us/articles/115002323627-Tracking-sessions-in-Amplitude#h_a832c1ce-717a-4ab3-b205-9d7ed418ef1a
      long curTime = Calendar.getInstance().getTimeInMillis();
      if (curTime - sessionID >= FIRE_TIME) { // if FIRE_TIME ms have elapsed, reset the sessionId
        sessionID = curTime; // reset sessionId
      }
    }
    return sessionID;
  }

  private void startSession() {
    // Set the session id
    sessionID = Calendar.getInstance().getTimeInMillis();
  }

  private void stopSession() {
    sessionID = -1;
  }
}
