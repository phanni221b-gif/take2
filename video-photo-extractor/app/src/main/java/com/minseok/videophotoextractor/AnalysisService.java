package com.minseok.videophotoextractor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalysisService extends Service {
    public static final String ACTION_START = "com.minseok.videophotoextractor.START_ANALYSIS";
    public static final String ACTION_PROGRESS = "com.minseok.videophotoextractor.ANALYSIS_PROGRESS";
    public static final String ACTION_COMPLETE = "com.minseok.videophotoextractor.ANALYSIS_COMPLETE";
    public static final String ACTION_ERROR = "com.minseok.videophotoextractor.ANALYSIS_ERROR";
    public static final String EXTRA_URI = "video_uri";
    public static final String EXTRA_AUTO_CROP = "auto_crop";

    private static final String CHANNEL_PROGRESS = "analysis_progress";
    private static final String CHANNEL_DONE = "analysis_done";
    private static final int NOTIFICATION_PROGRESS = 2101;
    private static final int NOTIFICATION_DONE = 2102;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        if (running) return START_NOT_STICKY;

        String uriText = intent.getStringExtra(EXTRA_URI);
        if (uriText == null || uriText.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean autoCrop = intent.getBooleanExtra(EXTRA_AUTO_CROP, true);
        Uri uri = Uri.parse(uriText);

        running = true;
        ResultStore.clear(this);
        ResultStore.markRunning(this, "분석을 준비하는 중…");
        startForeground(NOTIFICATION_PROGRESS, progressNotification(0, "분석을 준비하는 중…"));
        acquireWakeLock();

        executor.execute(() -> runAnalysis(uri, autoCrop));
        return START_NOT_STICKY;
    }

    private void runAnalysis(Uri uri, boolean autoCrop) {
        try {
            FrameAnalyzer.AnalyzeResult result = FrameAnalyzer.analyze(
                    this, uri, autoCrop,
                    (percent, message) -> {
                        ResultStore.updateProgress(this, percent, message);
                        notificationManager.notify(
                                NOTIFICATION_PROGRESS,
                                progressNotification(percent, message));
                        broadcast(ACTION_PROGRESS, percent, message);
                    });

            ResultStore.markCompleted(this, result);
            broadcast(ACTION_COMPLETE, 100, "추출 완료");
            stopForeground(STOP_FOREGROUND_REMOVE);
            notificationManager.notify(NOTIFICATION_DONE, doneNotification(
                    "사진 " + result.items.size() + "장 추출 완료",
                    "눌러서 결과를 확인하세요."));
        } catch (Exception e) {
            String message = "추출 실패: " + safeMessage(e);
            ResultStore.markError(this, message);
            broadcast(ACTION_ERROR, 0, message);
            stopForeground(STOP_FOREGROUND_REMOVE);
            notificationManager.notify(NOTIFICATION_DONE,
                    doneNotification("사진 추출 실패", message));
        } finally {
            running = false;
            releaseWakeLock();
            stopSelf();
        }
    }

    private void broadcast(String action, int progress, String message) {
        Intent i = new Intent(action);
        i.setPackage(getPackageName());
        i.putExtra("progress", progress);
        i.putExtra("message", message);
        sendBroadcast(i);
    }

    private Notification progressNotification(int progress, String message) {
        PendingIntent pending = activityPendingIntent();
        Notification.Builder b = new Notification.Builder(this, CHANNEL_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("영상에서 사진 추출")
                .setContentText(message == null ? "분석 중…" : message)
                .setContentIntent(pending)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS);
        if (progress <= 0) b.setProgress(100, 0, true);
        else b.setProgress(100, Math.min(100, progress), false);
        return b.build();
    }

    private Notification doneNotification(String title, String text) {
        return new Notification.Builder(this, CHANNEL_DONE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(activityPendingIntent())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
    }

    private PendingIntent activityPendingIntent() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createChannels() {
        NotificationChannel progress = new NotificationChannel(
                CHANNEL_PROGRESS, "사진 추출 진행", NotificationManager.IMPORTANCE_LOW);
        progress.setDescription("영상에서 사진을 추출하는 동안 진행률을 표시합니다.");
        progress.setSound(null, null);
        notificationManager.createNotificationChannel(progress);

        NotificationChannel done = new NotificationChannel(
                CHANNEL_DONE, "사진 추출 완료", NotificationManager.IMPORTANCE_DEFAULT);
        done.setDescription("사진 추출이 끝나면 알려줍니다.");
        notificationManager.createNotificationChannel(done);
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getPackageName() + ":analysis");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(6 * 60 * 60 * 1000L);
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {}
    }

    private static String safeMessage(Throwable e) {
        String m = e.getMessage();
        return (m == null || m.trim().isEmpty()) ? e.getClass().getSimpleName() : m;
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        ResultStore.markError(this, "백그라운드 처리 제한 시간에 도달해 분석이 중단됐습니다.");
        broadcast(ACTION_ERROR, 0, "백그라운드 처리 제한 시간에 도달했습니다.");
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf(startId);
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
