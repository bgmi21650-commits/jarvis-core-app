package com.jarvis.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

public class JarvisOverlayService extends Service {
    private WindowManager wm;
    private FrameLayout rootContainer;
    private LinearLayout petBody;
    private TextView speechBubble;
    private WindowManager.LayoutParams params;

    private boolean isViewAttached = false;
    private boolean isPetVisible = true;
    private static final String CHANNEL_ID = "jarvis_pet_channel";
    private static final String ACTION_TOGGLE = "ACTION_TOGGLE_PET";

    private final Handler walkHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private Runnable walkRunnable;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(101, createToggleNotification(true));
        buildCuteCharacterOverlay();
        startRoamingAnimation();
    }

    private void buildCuteCharacterOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 120;
        params.y = 400;

        rootContainer = new FrameLayout(this);

        speechBubble = new TextView(this);
        speechBubble.setText("Hi Ratan! ✨");
        speechBubble.setTextColor(Color.WHITE);
        speechBubble.setTextSize(11);
        speechBubble.setPadding(18, 10, 18, 10);

        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setColor(Color.parseColor("#CC1E1E2F"));
        bubbleBg.setCornerRadius(24f);
        bubbleBg.setStroke(2, Color.parseColor("#00E5FF"));
        speechBubble.setBackground(bubbleBg);
        speechBubble.setVisibility(View.GONE);

        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        bubbleParams.setMargins(0, 0, 0, 10);
        speechBubble.setLayoutParams(bubbleParams);

        petBody = new LinearLayout(this);
        petBody.setOrientation(LinearLayout.VERTICAL);
        petBody.setGravity(Gravity.CENTER);
        petBody.setPadding(20, 16, 20, 16);

        GradientDrawable petShape = new GradientDrawable();
        petShape.setColor(Color.parseColor("#00C0FF"));
        petShape.setCornerRadius(60f);
        petShape.setStroke(3, Color.WHITE);
        petBody.setBackground(petShape);

        TextView petFace = new TextView(this);
        petFace.setText("(◕‿◕✿)");
        petFace.setTextColor(Color.WHITE);
        petFace.setTextSize(16);
        petFace.setTypeface(null, android.graphics.Typeface.BOLD);
        petBody.addView(petFace);

        FrameLayout.LayoutParams petParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        petParams.setMargins(0, 50, 0, 0);
        petBody.setLayoutParams(petParams);

        rootContainer.addView(speechBubble);
        rootContainer.addView(petBody);

        wm.addView(rootContainer, params);
        isViewAttached = true;
        isPetVisible = true;

        setupInteraction();
    }

    private void setupInteraction() {
        petBody.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long startTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startTime = System.currentTimeMillis();
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        if (isViewAttached) {
                            wm.updateViewLayout(rootContainer, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - startTime;
                        float deltaX = Math.abs(event.getRawX() - initialTouchX);
                        float deltaY = Math.abs(event.getRawY() - initialTouchY);

                        if (duration < 250 && deltaX < 15 && deltaY < 15) {
                            showQuickDialog();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void showQuickDialog() {
        String[] quotes = {
                "Online & Ready! 🚀",
                "Working hard today? 💪",
                "JARVIS systems stable ⚡",
                "Tap notification to hide me!"
        };
        speechBubble.setText(quotes[random.nextInt(quotes.length)]);
        speechBubble.setVisibility(View.VISIBLE);

        walkHandler.postDelayed(() -> {
            if (speechBubble != null) speechBubble.setVisibility(View.GONE);
        }, 2200);
    }

    private void startRoamingAnimation() {
        walkRunnable = new Runnable() {
            @Override
            public void run() {
                if (isViewAttached && isPetVisible && rootContainer != null) {
                    int stepX = (random.nextInt(21) - 10);
                    int stepY = (random.nextInt(15) - 7);

                    params.x = Math.max(20, Math.min(params.x + stepX, 850));
                    params.y = Math.max(100, Math.min(params.y + stepY, 1700));

                    wm.updateViewLayout(rootContainer, params);
                }
                walkHandler.postDelayed(this, 1800);
            }
        };
        walkHandler.postDelayed(walkRunnable, 2000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            togglePetVisibility();
        }
        return START_STICKY;
    }

    private void togglePetVisibility() {
        if (isPetVisible) {
            if (isViewAttached) {
                wm.removeView(rootContainer);
                isViewAttached = false;
            }
            isPetVisible = false;
            Toast.makeText(this, "JARVIS Pet hidden. Tap notification to bring back.", Toast.LENGTH_SHORT).show();
        } else {
            if (!isViewAttached) {
                wm.addView(rootContainer, params);
                isViewAttached = true;
            }
            isPetVisible = true;
            Toast.makeText(this, "JARVIS Pet is back! ✨", Toast.LENGTH_SHORT).show();
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(101, createToggleNotification(isPetVisible));
        }
    }

    private Notification createToggleNotification(boolean visible) {
        Intent toggleIntent = new Intent(this, JarvisOverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE);

        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        String statusMsg = visible ? "Pet is on screen. Tap to HIDE." : "Pet is hidden. Tap to SHOW.";

        return builder.setContentTitle("JARVIS Companion")
                .setContentText(statusMsg)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    "JARVIS Pet Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(chan);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (walkRunnable != null) {
            walkHandler.removeCallbacks(walkRunnable);
        }
        if (isViewAttached && rootContainer != null) {
            wm.removeView(rootContainer);
            isViewAttached = false;
        }
    }
}    private static final String ACTION_TOGGLE = "ACTION_TOGGLE_PET";

    private final Handler walkHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private Runnable walkRunnable;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(101, createToggleNotification(true));
        buildCuteCharacterOverlay();
        startRoamingAnimation();
    }

    private void buildCuteCharacterOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 120;
        params.y = 400;

        rootContainer = new FrameLayout(this);

        // Speech dialogue bubble
        speechBubble = new TextView(this);
        speechBubble.setText("Hi Ratan! ✨");
        speechBubble.setTextColor(Color.WHITE);
        speechBubble.setTextSize(11);
        speechBubble.setPadding(18, 10, 18, 10);

        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setColor(Color.parseColor("#CC1E1E2F"));
        bubbleBg.setCornerRadius(24f);
        bubbleBg.setStroke(2, Color.parseColor("#00E5FF"));
        speechBubble.setBackground(bubbleBg);
        speechBubble.setVisibility(View.GONE);

        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        bubbleParams.setMargins(0, 0, 0, 10);
        speechBubble.setLayoutParams(bubbleParams);

        // Cute Character Container
        petBody = new LinearLayout(this);
        petBody.setOrientation(LinearLayout.VERTICAL);
        petBody.setGravity(Gravity.CENTER);
        petBody.setPadding(20, 16, 20, 16);

        GradientDrawable petShape = new GradientDrawable();
        petShape.setColor(Color.parseColor("#00C0FF"));
        petShape.setCornerRadius(60f);
        petShape.setStroke(3, Color.WHITE);
        petBody.setBackground(petShape);

        // Cute face expression
        TextView petFace = new TextView(this);
        petFace.setText("(◕‿◕✿)");
        petFace.setTextColor(Color.WHITE);
        petFace.setTextSize(16);
        petFace.setTypeface(null, android.graphics.Typeface.BOLD);
        petBody.addView(petFace);

        FrameLayout.LayoutParams petParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        petParams.setMargins(0, 50, 0, 0);
        petBody.setLayoutParams(petParams);

        rootContainer.addView(speechBubble);
        rootContainer.addView(petBody);

        wm.addView(rootContainer, params);
        isViewAttached = true;
        isPetVisible = true;

        setupInteraction();
    }

    private void setupInteraction() {
        petBody.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long startTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startTime = System.currentTimeMillis();
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        if (isViewAttached) {
                            wm.updateViewLayout(rootContainer, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - startTime;
                        float deltaX = Math.abs(event.getRawX() - initialTouchX);
                        float deltaY = Math.abs(event.getRawY() - initialTouchY);

                        if (duration < 250 && deltaX < 15 && deltaY < 15) {
                            showQuickDialog();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void showQuickDialog() {
        String[] quotes = {
                "Online & Ready! 🚀",
                "Working hard today? 💪",
                "JARVIS systems stable ⚡",
                "Tap notification to hide me!"
        };
        speechBubble.setText(quotes[random.nextInt(quotes.length)]);
        speechBubble.setVisibility(View.VISIBLE);

        walkHandler.postDelayed(() -> {
            if (speechBubble != null) speechBubble.setVisibility(View.GONE);
        }, 2200);
    }

    private void startRoamingAnimation() {
        walkRunnable = new Runnable() {
            @Override
            public void run() {
                if (isViewAttached && isPetVisible && rootContainer != null) {
                    // Chote randomized steps (Screen par roaming effect)
                    int stepX = (random.nextInt(21) - 10);
                    int stepY = (random.nextInt(15) - 7);

                    params.x = Math.max(20, Math.min(params.x + stepX, 850));
                    params.y = Math.max(100, Math.min(params.y + stepY, 1700));

                    wm.updateViewLayout(rootContainer, params);
                }
                walkHandler.postDelayed(this, 1800);
            }
        };
        walkHandler.postDelayed(walkRunnable, 2000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            togglePetVisibility();
        }
        return START_STICKY;
    }

    private void togglePetVisibility() {
        if (isPetVisible) {
            if (isViewAttached) {
                wm.removeView(rootContainer);
                isViewAttached = false;
            }
            isPetVisible = false;
            Toast.makeText(this, "JARVIS Pet hidden. Tap notification to bring back.", Toast.LENGTH_SHORT).show();
        } else {
            if (!isViewAttached) {
                wm.addView(rootContainer, params);
                isViewAttached = true;
            }
            isPetVisible = true;
            Toast.makeText(this, "JARVIS Pet is back! ✨", Toast.LENGTH_SHORT).show();
        }

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(101, createToggleNotification(isPetVisible));
        }
    }

    private Notification createToggleNotification(boolean visible) {
        Intent toggleIntent = new Intent(this, JarvisOverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE);

        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        String statusMsg = visible ? "Pet is on screen. Tap to HIDE." : "Pet is hidden. Tap to SHOW.";

        return builder.setContentTitle("JARVIS Companion")
                .setContentText(statusMsg)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    "JARVIS Pet Service",
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
        walkHandler.removeCallbacks(walkRunnable);
        if (isViewAttached && rootContainer != null) {
            wm.removeView(rootContainer);
            isViewAttached = false;
        }
    }
}    }

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
