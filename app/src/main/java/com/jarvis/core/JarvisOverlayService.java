package com.jarvis.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

public class JarvisOverlayService extends Service {
    private WindowManager wm;
    private View arcReactorView;
    private WindowManager.LayoutParams params;
    private boolean isViewAttached = false;
    private static final String CHANNEL_ID = "jarvis_core_channel";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(101, createJarvisNotification());
        setupOverlay();
    }

    private void setupOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        ImageView core = new ImageView(this);
        core.setImageResource(android.R.drawable.presence_online);
        core.setBackgroundColor(Color.TRANSPARENT);
        arcReactorView = core;

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                180, 180,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;

        wm.addView(arcReactorView, params);
        isViewAttached = true;

        arcReactorView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        wm.updateViewLayout(arcReactorView, params);
                        return true;
                }
                return false;
            }
        });
    }

    public void hideOverlay() {
        if (isViewAttached && arcReactorView != null) {
            wm.removeView(arcReactorView);
            isViewAttached = false;
        }
    }

    public void showOverlay() {
        if (!isViewAttached && arcReactorView != null) {
            wm.addView(arcReactorView, params);
            isViewAttached = true;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "TOGGLE_OVERLAY".equals(intent.getAction())) {
            if (isViewAttached) {
                hideOverlay();
            } else {
                showOverlay();
            }
        }
        return START_STICKY;
    }

    private Notification createJarvisNotification() {
        Intent toggleIntent = new Intent(this, JarvisOverlayService.class);
        toggleIntent.setAction("TOGGLE_OVERLAY");
        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, toggleIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder.setContentTitle("JARVIS Protocol Active")
                .setContentText("Systems online. Tap to toggle Core UI.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    "JARVIS Background Core",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(chan);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideOverlay();
    }
}
