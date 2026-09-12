package com.jarvis.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JarvisOverlayService extends Service implements TextToSpeech.OnInitListener {
    private WindowManager wm;
    private FrameLayout rootContainer;
    private LinearLayout avatarCard;
    private LinearLayout hudPanel;
    private TextView speechBubble;
    private WindowManager.LayoutParams params;

    private boolean isViewAttached = false;
    private boolean isPetVisible = true;
    private boolean isHudOpen = false;
    private boolean isFlashlightOn = false;

    private TextToSpeech backupTts;
    private MediaPlayer mediaPlayer;
    private CameraManager cameraManager;
    private String cameraId;
    private SpeechRecognizer speechRecognizer;
    private AudioManager audioManager;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkPool = Executors.newSingleThreadExecutor();

    private static final String CHANNEL_ID = "hunter_cha_channel";
    private static final String ACTION_TOGGLE = "ACTION_TOGGLE_HUNTER";

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(101, createToggleNotification(true));
        initHardware();
        initSpeechRecognizer();
        initUI();
    }

    private void initHardware() {
        backupTts = new TextToSpeech(this, this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null && cameraManager.getCameraIdList().length > 0) {
                cameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (Exception ignored) {}
    }

    private void initSpeechRecognizer() {
        handler.post(() -> {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle p) { showBubble("Sun rahi hoon, boliye... 🎙️"); }
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rms) {}
                    @Override public void onBufferReceived(byte[] b) {}
                    @Override public void onEndOfSpeech() { showBubble("Processing... ⏳"); }
                    @Override public void onError(int e) { showBubble("Aawaz saaf nahi aayi!"); }

                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String spoken = matches.get(0);
                            showBubble("You: " + spoken);
                            processCommandOrAskAI(spoken);
                        }
                    }
                    @Override public void onPartialResults(Bundle p) {}
                    @Override public void onEvent(int t, Bundle p) {}
                });
            }
        });
    }

    private void startListening() {
        handler.post(() -> {
            if (speechRecognizer != null) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
                speechRecognizer.startListening(intent);
            } else {
                Toast.makeText(this, "Voice recognition available nahi hai!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && backupTts != null) {
            backupTts.setLanguage(new Locale("hi", "IN"));
            backupTts.setPitch(1.1f);
        }
    }

    private void speakNaturally(String text) {
        networkPool.execute(() -> {
            try {
                String clean = text.replaceAll("[^a-zA-Z0-9\\s\\u0900-\\u097F,.?!]", "");
                String urlStr = "https://translate.google.com/translate_tts?ie=UTF-8&tl=hi&client=tw-ob&q=" + URLEncoder.encode(clean, "UTF-8");
                handler.post(() -> {
                    try {
                        if (mediaPlayer != null) mediaPlayer.release();
                        mediaPlayer = new MediaPlayer();
                        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).build());
                        mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(urlStr));
                        mediaPlayer.prepareAsync();
                        mediaPlayer.setOnPreparedListener(MediaPlayer::start);
                    } catch (Exception ex) {
                        if (backupTts != null) backupTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FALLBACK");
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (backupTts != null) backupTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FALLBACK");
                });
            }
        });
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
        params.x = 60;
        params.y = 350;

        rootContainer = new FrameLayout(this);

        // Speech Bubble
        speechBubble = new TextView(this);
        speechBubble.setText("Hunter Cha ready! ⚔️");
        speechBubble.setTextColor(Color.WHITE);
        speechBubble.setTextSize(12);
        speechBubble.setPadding(24, 14, 24, 14);
        speechBubble.setBackground(createBg("#F20F172A", "#38BDF8", 20f, 2));
        speechBubble.setVisibility(View.GONE);

        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        bubbleParams.setMargins(0, 0, 0, 10);
        speechBubble.setLayoutParams(bubbleParams);

        // Hunter Cha Card (Avatar)
        avatarCard = new LinearLayout(this);
        avatarCard.setOrientation(LinearLayout.HORIZONTAL);
        avatarCard.setGravity(Gravity.CENTER_VERTICAL);
        avatarCard.setPadding(20, 14, 24, 14);
        avatarCard.setBackground(createBg("#1E293B", "#38BDF8", 40f, 3));

        TextView emblem = new TextView(this);
        emblem.setText("⚔️");
        emblem.setTextSize(20);
        emblem.setPadding(0, 0, 14, 0);

        LinearLayout textGroup = new LinearLayout(this);
        textGroup.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText("HUNTER CHA");
        titleView.setTextColor(Color.parseColor("#38BDF8"));
        titleView.setTextSize(13);
        titleView.setTypeface(null, Typeface.BOLD);

        TextView statusView = new TextView(this);
        statusView.setText("S-Rank Companion • Tap");
        statusView.setTextColor(Color.parseColor("#94A3B8"));
        statusView.setTextSize(10);

        textGroup.addView(titleView);
        textGroup.addView(statusView);

        avatarCard.addView(emblem);
        avatarCard.addView(textGroup);

        FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        avatarParams.setMargins(0, 75, 0, 0);
        avatarCard.setLayoutParams(avatarParams);

        buildHudPanel();

        rootContainer.addView(speechBubble);
        rootContainer.addView(hudPanel);
        rootContainer.addView(avatarCard);

        wm.addView(rootContainer, params);
        isViewAttached = true;

        setupDragAndClick();
    }

    private void buildHudPanel() {
        hudPanel = new LinearLayout(this);
        hudPanel.setOrientation(LinearLayout.VERTICAL);
        hudPanel.setPadding(24, 20, 24, 20);
        hudPanel.setBackground(createBg("#F80F172A", "#38BDF8", 24f, 2));
        hudPanel.setVisibility(View.GONE);

        FrameLayout.LayoutParams hudParams = new FrameLayout.LayoutParams(
                680, FrameLayout.LayoutParams.WRAP_CONTENT
        );
        hudParams.setMargins(0, 160, 0, 0);
        hudPanel.setLayoutParams(hudParams);

        TextView hudTitle = new TextView(this);
        hudTitle.setText("AI SYSTEM HUB  •  LIVE");
        hudTitle.setTextColor(Color.parseColor("#38BDF8"));
        hudTitle.setTextSize(12);
        hudTitle.setTypeface(null, Typeface.BOLD);

        // Input Bar
        LinearLayout chatBar = new LinearLayout(this);
        chatBar.setOrientation(LinearLayout.HORIZONTAL);
        chatBar.setPadding(0, 12, 0, 14);

        EditText input = new EditText(this);
        input.setHint("Poocho ya mic se bolo...");
        input.setHintTextColor(Color.parseColor("#64748B"));
        input.setTextColor(Color.WHITE);
        input.setTextSize(12);
        input.setBackground(createBg("#1E293B", "#334155", 14f, 1));
        input.setPadding(16, 12, 16, 12);

        Button micBtn = new Button(this);
        micBtn.setText("🎙️");
        micBtn.setBackground(createBg("#38BDF8", "#FFFFFF", 14f, 0));
        micBtn.setOnClickListener(v -> startListening());

        Button sendBtn = new Button(this);
        sendBtn.setText("Send");
        sendBtn.setTextColor(Color.BLACK);
        sendBtn.setBackground(createBg("#38BDF8", "#FFFFFF", 14f, 0));
        sendBtn.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            if (!q.isEmpty()) {
                input.setText("");
                showBubble("Soch rahi hoon...");
                processCommandOrAskAI(q);
            }
        });

        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT);
        micLp.setMargins(6, 0, 6, 0);

        chatBar.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        chatBar.addView(micBtn, micLp);
        chatBar.addView(sendBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Quick Controls
        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);

        Button flashBtn = createButton("Flash");
        flashBtn.setOnClickListener(v -> toggleFlashlight());

        Button batteryBtn = createButton("Battery");
        batteryBtn.setOnClickListener(v -> checkBattery());

        Button waBtn = createButton("WhatsApp");
        waBtn.setOnClickListener(v -> openWhatsApp());

        Button volBtn = createButton("Vol Max");
        volBtn.setOnClickListener(v -> maxVolume());

        controlsRow.addView(flashBtn);
        controlsRow.addView(batteryBtn);
        controlsRow.addView(waBtn);
        controlsRow.addView(volBtn);

        hudPanel.addView(hudTitle);
        hudPanel.addView(chatBar);
        hudPanel.addView(controlsRow);
    }

    private Button createButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(Color.parseColor("#38BDF8"));
        b.setBackground(createBg("#1538BDF8", "#38BDF8", 12f, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(4, 0, 4, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void processCommandOrAskAI(String query) {
        String lower = query.toLowerCase();
        if (lower.contains("torch") || lower.contains("flash")) {
            toggleFlashlight();
        } else if (lower.contains("battery") || lower.contains("charge")) {
            checkBattery();
        } else if (lower.contains("whatsapp")) {
            openWhatsApp();
        } else if (lower.contains("volume") || lower.contains("aawaz")) {
            maxVolume();
        } else {
            fetchDeepAIResponse(query);
        }
    }

    private void fetchDeepAIResponse(String prompt) {
        networkPool.execute(() -> {
            String answer;
            try {
                URL url = new URL("https://text.pollinations.ai/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(9000);
                conn.setReadTimeout(9000);
                conn.setDoOutput(true);

                String sysContext = "You are Hunter Cha (Cha Hae-In) from Solo Leveling, acting as a personal Android companion twin. Reply directly in funny, smart, natural Hinglish like a real partner. Keep replies under 25 words.";

                JSONObject json = new JSONObject();
                JSONArray msgs = new JSONArray();

                JSONObject sObj = new JSONObject();
                sObj.put("role", "system");
                sObj.put("content", sysContext);
                msgs.put(sObj);

                JSONObject uObj = new JSONObject();
                uObj.put("role", "user");
                uObj.put("content", prompt);
                msgs.put(uObj);

                json.put("messages", msgs);
                json.put("model", "openai");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    answer = sb.toString().trim();
                } else {
                    answer = "Main ready hoon bhai, bolo aage kya plan hai?";
                }
            } catch (Exception e) {
                answer = "Network thoda slow hai, par systems online hain!";
            }

            final String finalAns = answer;
            handler.post(() -> {
                showBubble(finalAns);
                speakNaturally(finalAns);
            });
        });
    }

    private void toggleFlashlight() {
        try {
            if (cameraId != null && cameraManager != null) {
                isFlashlightOn = !isFlashlightOn;
                cameraManager.setTorchMode(cameraId, isFlashlightOn);
                String msg = isFlashlightOn ? "Torch On! 🔦" : "Torch Off!";
                showBubble(msg);
                speakNaturally(msg);
            }
        } catch (CameraAccessException e) {
            Toast.makeText(this, "Torch error", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkBattery() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent bStatus = registerReceiver(null, ifilter);
        int level = bStatus != null ? bStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        String msg = "Battery " + level + "% hai!";
        showBubble(msg);
        speakNaturally(msg);
    }

    private void openWhatsApp() {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage("com.whatsapp");
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            showBubble("WhatsApp install nahi hai!");
        }
    }

    private void maxVolume() {
        if (audioManager != null) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI);
            showBubble("Volume full kar diya! 🔊");
            speakNaturally("Volume full ho gaya!");
        }
    }

    private void setupDragAndClick() {
        avatarCard.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartTime = System.currentTimeMillis();
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
                        long duration = System.currentTimeMillis() - touchStartTime;
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);

                        // Clean click detect
                        if (duration < 300 && diffX < 20 && diffY < 20) {
                            toggleHud();
                        } else {
                            // Auto-dock to screen edge
                            int screenWidth = getResources().getDisplayMetrics().widthPixels;
                            params.x = (params.x < screenWidth / 2) ? 20 : (screenWidth - 260);
                            if (isViewAttached) wm.updateViewLayout(rootContainer, params);
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
            // HUD open hote hi keyboard input enable karne ke liye flag update
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            speakNaturally("Hunter Cha online!");
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        if (isViewAttached) wm.updateViewLayout(rootContainer, params);
    }

    private void showBubble(String text) {
        speechBubble.setText(text);
        speechBubble.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> speechBubble.setVisibility(View.GONE), 4000);
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
        if (manager != null) manager.notify(101, createToggleNotification(isPetVisible));
    }

    private Notification createToggleNotification(boolean visible) {
        Intent toggleIntent = new Intent(this, JarvisOverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent pi = PendingIntent.getService(this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder.setContentTitle("Hunter Cha Companion")
                .setContentText(visible ? "Tap to HIDE Hunter Cha" : "Tap to SHOW Hunter Cha")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private GradientDrawable createBg(String bg, String stroke, float rad, int width) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bg));
        gd.setCornerRadius(rad);
        if (width > 0) gd.setStroke(width, Color.parseColor(stroke));
        return gd;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(CHANNEL_ID, "Hunter Cha Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(chan);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (mediaPlayer != null) mediaPlayer.release();
        if (backupTts != null) backupTts.shutdown();
        if (isViewAttached && rootContainer != null) wm.removeView(rootContainer);
    }
}
