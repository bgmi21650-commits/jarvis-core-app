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
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
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
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JarvisOverlayService extends Service implements TextToSpeech.OnInitListener {
    private WindowManager wm;
    private FrameLayout rootContainer;
    private LinearLayout avatarContainer, hudPanel;
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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final ExecutorService networkPool = Executors.newSingleThreadExecutor();
    private Runnable roamRunnable;

    private static final String CHANNEL_ID = "jarvis_hunter_channel";
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
        startRoaming();
    }

    private void initHardware() {
        backupTts = new TextToSpeech(this, this);
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
                    @Override public void onReadyForSpeech(Bundle params) { showBubble("Sun rahi hoon... 🎙️"); }
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() { showBubble("Soch rahi hoon... ⏳"); }
                    @Override public void onError(int error) { showBubble("Aawaz nahi aayi!"); }
                    @Override public void onResults(Bundle results) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String spoken = matches.get(0);
                            showBubble("You: " + spoken);
                            fetchDeepAIResponse(spoken);
                        }
                    }
                    @Override public void onPartialResults(Bundle partialResults) {}
                    @Override public void onEvent(int eventType, Bundle params) {}
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
            }
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && backupTts != null) {
            backupTts.setLanguage(new Locale("hi", "IN"));
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
                        if (backupTts != null) backupTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FB");
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (backupTts != null) backupTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FB");
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
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 400;

        rootContainer = new FrameLayout(this);

        speechBubble = new TextView(this);
        speechBubble.setText("Hunter Cha online! ✨");
        speechBubble.setTextColor(Color.WHITE);
        speechBubble.setTextSize(12);
        speechBubble.setPadding(20, 10, 20, 10);
        speechBubble.setBackground(createBg("#EE0B0F19", "#FFB703", 20f, 2));
        speechBubble.setVisibility(View.GONE);

        avatarContainer = new LinearLayout(this);
        avatarContainer.setOrientation(LinearLayout.VERTICAL);
        avatarContainer.setGravity(Gravity.CENTER);
        avatarContainer.setPadding(18, 12, 18, 12);
        avatarContainer.setBackground(createBg("#FFB703", "#FFFFFF", 50f, 2));

        TextView face = new TextView(this);
        face.setText("🌸 (•̀ᴗ•́)و 🌸");
        face.setTextColor(Color.parseColor("#0B0F19"));
        face.setTextSize(15);
        face.setTypeface(null, android.graphics.Typeface.BOLD);
        avatarContainer.addView(face);

        FrameLayout.LayoutParams aParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        aParams.setMargins(0, 45, 0, 0);
        avatarContainer.setLayoutParams(aParams);

        buildHud();

        rootContainer.addView(speechBubble);
        rootContainer.addView(hudPanel);
        rootContainer.addView(avatarContainer);

        wm.addView(rootContainer, params);
        isViewAttached = true;
        setupDragAndTap();
    }

    private void buildHud() {
        hudPanel = new LinearLayout(this);
        hudPanel.setOrientation(LinearLayout.VERTICAL);
        hudPanel.setPadding(24, 20, 24, 20);
        hudPanel.setBackground(createBg("#F20F172A", "#FFB703", 24f, 2));
        hudPanel.setVisibility(View.GONE);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(680, FrameLayout.LayoutParams.WRAP_CONTENT);
        panelParams.setMargins(145, 0, 0, 0);
        hudPanel.setLayoutParams(panelParams);

        TextView title = new TextView(this);
        title.setText("HUNTER CHA • AI CORE");
        title.setTextColor(Color.parseColor("#FFB703"));

        EditText chatInput = new EditText(this);
        chatInput.setHint("Boliye ya type karein...");
        chatInput.setHintTextColor(Color.parseColor("#64748B"));
        chatInput.setTextColor(Color.WHITE);
        chatInput.setTextSize(12);

        LinearLayout chatBar = new LinearLayout(this);
        chatBar.setOrientation(LinearLayout.HORIZONTAL);

        Button micBtn = new Button(this);
        micBtn.setText("🎙️");
        micBtn.setBackground(createBg("#FFB703", "#FFFFFF", 12f, 0));
        micBtn.setOnClickListener(v -> startListening());

        Button sendBtn = new Button(this);
        sendBtn.setText("Send");
        sendBtn.setBackground(createBg("#FFB703", "#FFFFFF", 12f, 0));
        sendBtn.setOnClickListener(v -> {
            String q = chatInput.getText().toString().trim();
            if (!q.isEmpty()) {
                chatInput.setText("");
                showBubble("Soch rahi hoon...");
                fetchDeepAIResponse(q);
            }
        });

        chatBar.addView(chatInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        chatBar.addView(micBtn, new LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT));
        chatBar.addView(sendBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        hudPanel.addView(title);
        hudPanel.addView(chatBar);
    }

    private void fetchDeepAIResponse(String prompt) {
        networkPool.execute(() -> {
            String responseText;
            try {
                URL url = new URL("https://text.pollinations.ai/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(9000);
                conn.setReadTimeout(9000);
                conn.setDoOutput(true);

                JSONObject jsonBody = new JSONObject();
                JSONArray messages = new JSONArray();
                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", "You are Hunter Cha AI twin companion. Reply sharp and funny in natural Hinglish under 25 words.");
                messages.put(sys);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.put(userMsg);

                jsonBody.put("messages", messages);
                jsonBody.put("model", "openai");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    responseText = sb.toString().trim();
                } else {
                    responseText = "Bilkul bhai! Sab ready hai, bolo aage kya karein?";
                }
            } catch (Exception e) {
                responseText = "Network slow hai bhai, baaki systems full ready hain!";
            }

            final String finalAns = responseText;
            handler.post(() -> {
                showBubble(finalAns);
                speakNaturally(finalAns);
            });
        });
    }

    private void setupDragAndTap() {
        avatarContainer.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long startTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startTime = System.currentTimeMillis();
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        if (isViewAttached) wm.updateViewLayout(rootContainer, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - startTime;
                        if (duration < 250 && Math.abs(event.getRawX() - initialTouchX) < 15) {
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
        if (isHudOpen) speakNaturally("Hunter Cha ready!");
    }

    private void showBubble(String text) {
        speechBubble.setText(text);
        speechBubble.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> speechBubble.setVisibility(View.GONE), 4000);
    }

    private void startRoaming() {
        roamRunnable = new Runnable() {
            @Override
            public void run() {
                if (isViewAttached && isPetVisible && !isHudOpen && rootContainer != null) {
                    params.x = Math.max(20, Math.min(params.x + (random.nextInt(25) - 12), 850));
                    params.y = Math.max(100, Math.min(params.y + (random.nextInt(19) - 9), 1700));
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
            if (isViewAttached) { wm.removeView(rootContainer); isViewAttached = false; }
            isPetVisible = false;
        } else {
            if (!isViewAttached) { wm.addView(rootContainer, params); isViewAttached = true; }
            isPetVisible = true;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(101, createToggleNotification(isPetVisible));
    }

    private Notification createToggleNotification(boolean visible) {
        Intent toggleIntent = new Intent(this, JarvisOverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent pi = PendingIntent.getService(this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setContentTitle("Hunter Cha Companion")
                .setContentText(visible ? "Tap to HIDE" : "Tap to SHOW")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi).setOngoing(true).build();
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
            NotificationChannel chan = new NotificationChannel(CHANNEL_ID, "Hunter Cha", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(chan);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (roamRunnable != null) handler.removeCallbacks(roamRunnable);
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (mediaPlayer != null) mediaPlayer.release();
        if (backupTts != null) backupTts.shutdown();
        if (isViewAttached && rootContainer != null) wm.removeView(rootContainer);
    }
}
