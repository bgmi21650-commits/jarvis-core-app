package com.jarvis.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Random;

public class JarvisOverlayService extends Service implements TextToSpeech.OnInitListener {
    private WindowManager wm;
    private FrameLayout rootContainer;
    private LinearLayout petBody;
    private LinearLayout hudPanel;
    private TextView speechBubble;
    private WindowManager.LayoutParams params;

    private boolean isViewAttached = false;
    private boolean isPetVisible = true;
    private boolean isHudOpen = false;
    private boolean isFlashlightOn = false;

    private TextToSpeech tts;
    private CameraManager cameraManager;
    private String cameraId;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private Runnable roamRunnable;

    private static final String CHANNEL_ID = "jarvis_engine_channel";
    private static final String ACTION_TOGGLE = "ACTION_TOGGLE_JARVIS";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(101, createToggleNotification(true));
        initHardware();
        initUI();
        startRoaming();
    }

    private void initHardware() {
        tts = new TextToSpeech(this, this);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null && cameraManager.getCameraIdList().length > 0) {
                cameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            tts.setLanguage(new Locale("hi", "IN"));
            tts.setPitch(1.0f);
            tts.setSpeechRate(1.0f);
        }
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_VOICE");
        }
    }

    private void initUI() {
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
        params.x = 100;
        params.y = 400;

        rootContainer = new FrameLayout(this);

        // Speech Bubble
        speechBubble = new TextView(this);
        speechBubble.setText("Bolo bhai! Kya chal raha hai?");
        speechBubble.setTextColor(Color.WHITE);
        speechBubble.setTextSize(12);
        speechBubble.setPadding(20, 12, 20, 12);
        speechBubble.setBackground(createRoundedBackground("#E60F172A", "#00E5FF", 24f, 2));
        speechBubble.setVisibility(View.GONE);

        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        bubbleParams.setMargins(0, 0, 0, 8);
        speechBubble.setLayoutParams(bubbleParams);

        // Cute Avatar Body
        petBody = new LinearLayout(this);
        petBody.setOrientation(LinearLayout.VERTICAL);
        petBody.setGravity(Gravity.CENTER);
        petBody.setPadding(22, 18, 22, 18);
        petBody.setBackground(createRoundedBackground("#00D2FF", "#FFFFFF", 60f, 3));

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

        // Futuristic HUD Panel
        buildHudPanel();

        rootContainer.addView(speechBubble);
        rootContainer.addView(hudPanel);
        rootContainer.addView(petBody);

        wm.addView(rootContainer, params);
        isViewAttached = true;
        isPetVisible = true;

        setupDragAndTap();
    }

    private void buildHudPanel() {
        hudPanel = new LinearLayout(this);
        hudPanel.setOrientation(LinearLayout.VERTICAL);
        hudPanel.setPadding(30, 24, 30, 24);
        hudPanel.setBackground(createRoundedBackground("#F00B132B", "#00E5FF", 24f, 2));
        hudPanel.setVisibility(View.GONE);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                650, FrameLayout.LayoutParams.WRAP_CONTENT
        );
        panelParams.setMargins(140, 0, 0, 0);
        hudPanel.setLayoutParams(panelParams);

        TextView title = new TextView(this);
        title.setText("⚡ J.A.R.V.I.S  T W I N");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        // AI Twin Input
        EditText chatInput = new EditText(this);
        chatInput.setHint("Aapke jaisa reply dega...");
        chatInput.setHintTextColor(Color.parseColor("#708090"));
        chatInput.setTextColor(Color.WHITE);
        chatInput.setTextSize(12);
        chatInput.setBackground(createRoundedBackground("#331E293B", "#38BDF8", 12f, 1));
        chatInput.setPadding(16, 12, 16, 12);

        LinearLayout chatBar = new LinearLayout(this);
        chatBar.setOrientation(LinearLayout.HORIZONTAL);
        chatBar.setPadding(0, 16, 0, 16);

        Button askBtn = new Button(this);
        askBtn.setText("Send");
        askBtn.setTextColor(Color.BLACK);
        askBtn.setBackground(createRoundedBackground("#00E5FF", "#FFFFFF", 12f, 0));
        askBtn.setOnClickListener(v -> {
            String q = chatInput.getText().toString().trim();
            if (!q.isEmpty()) {
                String ans = getTwinAIResponse(q);
                chatInput.setText("");
                showBubble(ans);
                speak(ans);
            }
        });

        chatBar.addView(chatInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        chatBar.addView(askBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Quick System Controls Row
        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);

        Button flashBtn = createHudButton("Flash");
        flashBtn.setOnClickListener(v -> toggleFlashlight());

        Button batteryBtn = createHudButton("Battery");
        batteryBtn.setOnClickListener(v -> checkBattery());

        Button waBtn = createHudButton("WA");
        waBtn.setOnClickListener(v -> openWhatsApp());

        controlsRow.addView(flashBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        controlsRow.addView(batteryBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        controlsRow.addView(waBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        hudPanel.addView(title);
        hudPanel.addView(chatBar);
        hudPanel.addView(controlsRow);
    }

    private Button createHudButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(Color.parseColor("#00E5FF"));
        b.setBackground(createRoundedBackground("#2000E5FF", "#00E5FF", 12f, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(6, 0, 6, 0);
        b.setLayoutParams(lp);
        return b;
    }

    // AI Twin Logic (Answers exactly in your natural Hinglish/Bihari style)
    private String getTwinAIResponse(String query) {
        String q = query.toLowerCase();
        if (q.contains("kaisa hai") || q.contains("kaise ho")) {
            return "Ekdum jhakaas bhai! Aap batao kaisa chal raha sab?";
        } else if (q.contains("kaam") || q.contains("padhai") || q.contains("gym")) {
            return "Mehnat chalu rakh bhai, workout aur consistency me koi kami nahi honi chahiye!";
        } else if (q.contains("kya kar raha")) {
            return "Screen par ghoom raha hu, bolo kya madad chahiye?";
        } else if (q.contains("namaste") || q.contains("hi") || q.contains("hello")) {
            return "Pranaam bhai! JARVIS ready hai!";
        } else {
            return "Sahi baat hai bhai! Main full active hu, bolo aage kya karein?";
        }
    }

    private void toggleFlashlight() {
        try {
            if (cameraId != null && cameraManager != null) {
                isFlashlightOn = !isFlashlightOn;
                cameraManager.setTorchMode(cameraId, isFlashlightOn);
                String msg = isFlashlightOn ? "Flashlight on kar diya!" : "Flashlight off!";
                showBubble(msg);
                speak(msg);
            }
        } catch (CameraAccessException e) {
            Toast.makeText(this, "Flashlight error", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkBattery() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        String bMsg = "Battery abhi " + level + "% par hai bhai!";
        showBubble(bMsg);
        speak(bMsg);
    }

    private void openWhatsApp() {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage("com.whatsapp");
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            showBubble("WhatsApp installed nahi hai bhai!");
        }
    }

    private void setupDragAndTap() {
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
                            toggleHud();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void toggleHud() {
        isHudOpen = !isHudOpen;
        hudPanel.setVisibility(isHudOpen ? View.VISIBLE : View.GONE);
        if (isHudOpen) {
            speak("Systems ready!");
        }
    }

    private void showBubble(String text) {
        speechBubble.setText(text);
        speechBubble.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> speechBubble.setVisibility(View.GONE), 3500);
    }

    private void startRoaming() {
        roamRunnable = new Runnable() {
            @Override
            public void run() {
                if (isViewAttached && isPetVisible && !isHudOpen && rootContainer != null) {
                    int stepX = (random.nextInt(25) - 12);
                    int stepY = (random.nextInt(19) - 9);

                    params.x = Math.max(20, Math.min(params.x + stepX, 850));
                    params.y = Math.max(100, Math.min(params.y + stepY, 1700));

                    wm.updateViewLayout(rootContainer, params);
                }
                handler.postDelayed(this, 2000);
            }
        };
        handler.postDelayed(roamRunnable, 2500);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            toggleVisibility();
        }
        return START_STICKY;
    }

    private void toggleVisibility() {
        if (isPetVisible) {
            if (isViewAttached) {
                wm.removeView(rootContainer);
                isViewAttached = false;
            }
            isPetVisible = false;
        } else {
            if (!isViewAttached) {
                wm.addView(rootContainer, params);
                isViewAttached = true;
            }
            isPetVisible = true;
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(101, createToggleNotification(isPetVisible));
        }
    }

    private Notification createToggleNotification(boolean visible) {
        Intent toggleIntent = new Intent(this, JarvisOverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE);

        PendingIntent pi = PendingIntent.getService(
                this, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        String msg = visible ? "Pet is ON screen. Tap to HIDE." : "Pet is HIDDEN. Tap to SHOW.";

        return builder.setContentTitle("JARVIS Twin Companion")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private GradientDrawable createRoundedBackground(String bgColor, String strokeColor, float radius, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgColor));
        gd.setCornerRadius(radius);
        if (strokeWidth > 0) {
            gd.setStroke(strokeWidth, Color.parseColor(strokeColor));
        }
        return gd;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    "JARVIS Core Engine",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(chan);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (roamRunnable != null) handler.removeCallbacks(roamRunnable);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (isViewAttached && rootContainer != null) {
            wm.removeView(rootContainer);
            isViewAttached = false;
        }
    }
}
