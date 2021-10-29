package com.segment.analytics.android.integrations.amplitude;

import com.segment.analytics.Middleware;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.AliasPayload;
import com.segment.analytics.integrations.BasePayload;
import com.segment.analytics.integrations.GroupPayload;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;

import java.util.Timer;
import java.util.Calendar;
import java.util.TimerTask;

public class AmplitudeSessionId implements Middleware {

  private final static String KEY = "Actions Amplitude";
  private boolean active = false;

  private final static long FIRE_TIME = 300000;

  private TimerTask timer;

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
            .putValue("session_id", sessionID))
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
    stopTimer();
  }

  private void onForeground() {
    startTimer();
  }

  private void startTimer() {
    // Set the session id
    sessionID = Calendar.getInstance().getTimeInMillis();

    timer = new TimerTask() {
      @Override
      public void run() {
        stopTimer();
        startTimer();
      }
    };
    new Timer().schedule(timer, FIRE_TIME);
  }

  private void stopTimer() {
    if (timer != null) {
      timer.cancel();
    }
    sessionID = -1;
  }
}
