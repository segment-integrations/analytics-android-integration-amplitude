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

  private final static String KEY = "Actions Amplitude";

  private final static long FIRE_TIME = 300 * 1000; // 300 seconds

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
        .integration(KEY, new ValueMap()
            .putValue("session_id", getSessionId()))
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
      // if sessionId is -1, then we reset to curTime (essentially creating a new session)
      // TODO ask cody if -1 has a special value
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
