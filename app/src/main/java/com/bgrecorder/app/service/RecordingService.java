package com.bgrecorder.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.bgrecorder.app.MainActivity;
import com.bgrecorder.app.R;
import com.bgrecorder.app.utils.FileUtils;
import com.bgrecorder.app.widget.RecorderWidget;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class RecordingService extends Service {

    private static final String TAG = "RecordingService";
    public static final String CHANNEL_ID = "recording_channel";
    public static final String CHANNEL_ID_ALERT = "recording_alert_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "com.bgrecorder.app.ACTION_START";
    public static final String ACTION_STOP = "com.bgrecorder.app.ACTION_STOP";
    public static final String BROADCAST_STATUS = "com.bgrecorder.app.STATUS";
    public static final String EXTRA_IS_RECORDING = "is_recording";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_FILE_PATH = "file_path";

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private MediaRecorder mediaRecorder;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private boolean isRecording = false;
    private long recordingStartTime;
    private String currentFilePath;
    private Timer durationTimer;

    public static boolean isRunning = false;

    // Camera open state callback
    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startRecordingSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera error: " + error);
            camera.close();
            cameraDevice = null;
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        startBackgroundThread();
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            if (!isRecording) {
                startForegroundNotification();
                openCameraAndRecord();
            }
        } else if (ACTION_STOP.equals(action)) {
            stopRecording();
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ── Notification ──────────────────────────────────────────────

    private void createNotificationChannels() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Recording Service",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Shows while background recording is active");
        channel.setShowBadge(false);

        NotificationChannel alertChannel = new NotificationChannel(
                CHANNEL_ID_ALERT,
                "Recording Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        alertChannel.setDescription("Alerts for recording events");

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
            nm.createNotificationChannel(alertChannel);
        }
    }

    private void startForegroundNotification() {
        Notification notification = buildNotification(getString(R.string.recording_started));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(NOTIFICATION_ID, notification, type);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String text) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent mainPending = PendingIntent.getActivity(this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_record)
                .setContentIntent(mainPending)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);

        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(R.drawable.ic_stop, getString(R.string.stop_recording), stopPending);

        return builder.build();
    }

    private void updateNotification(String text) {
        Notification notification = buildNotification(text);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    // ── Camera & Recording ────────────────────────────────────────

    private void openCameraAndRecord() {
        try {
            String cameraId = getBackCameraId();
            if (cameraId == null) {
                Log.e(TAG, "No back camera found");
                stopSelf();
                return;
            }
            currentFilePath = FileUtils.generateOutputFilePath(this);
            setupMediaRecorder();
            cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler);
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "Failed to open camera", e);
            stopSelf();
        } catch (IOException e) {
            Log.e(TAG, "Failed to setup media recorder", e);
            stopSelf();
        }
    }

    private String getBackCameraId() throws CameraAccessException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useFront = prefs.getBoolean("use_front_camera", false);

        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing != null) {
                if (useFront && facing == CameraCharacteristics.LENS_FACING_FRONT) return id;
                if (!useFront && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
            }
        }
        return cameraManager.getCameraIdList().length > 0 ? cameraManager.getCameraIdList()[0] : null;
    }

    private void setupMediaRecorder() throws IOException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String quality = prefs.getString("video_quality", "720p");
        if (quality == null) quality = "720p";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mediaRecorder = new MediaRecorder(this);
        } else {
            mediaRecorder = new MediaRecorder();
        }
        
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncodingBitRate(128000);
        mediaRecorder.setAudioSamplingRate(44100);

        switch (quality) {
            case "1080p":
                mediaRecorder.setVideoSize(1920, 1080);
                mediaRecorder.setVideoEncodingBitRate(8_000_000);
                break;
            case "480p":
                mediaRecorder.setVideoSize(854, 480);
                mediaRecorder.setVideoEncodingBitRate(2_000_000);
                break;
            default: // 720p
                mediaRecorder.setVideoSize(1280, 720);
                mediaRecorder.setVideoEncodingBitRate(4_000_000);
                break;
        }

        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setOutputFile(currentFilePath);
        mediaRecorder.prepare();
    }

    private void startRecordingSession() {
        try {
            Surface recorderSurface = mediaRecorder.getSurface();
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_RECORD);
            captureBuilder.addTarget(recorderSurface);
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

            cameraDevice.createCaptureSession(
                    Collections.singletonList(recorderSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(captureBuilder.build(), null, backgroundHandler);
                                mediaRecorder.start();
                                isRecording = true;
                                isRunning = true;
                                recordingStartTime = SystemClock.elapsedRealtime();
                                startDurationTimer();
                                broadcastStatus(true, 0);
                                RecorderWidget.updateAllWidgets(RecordingService.this, true);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Capture session error", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Capture session config failed");
                            stopSelf();
                        }
                    },
                    backgroundHandler
            );
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create capture session", e);
            stopSelf();
        }
    }

    private void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        isRunning = false;
        stopDurationTimer();

        try {
            if (captureSession != null) {
                captureSession.stopRepeating();
                captureSession.close();
                captureSession = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error stopping capture session", e);
        }

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error stopping mediaRecorder", e);
            // delete corrupted file
            if (currentFilePath != null) {
                File f = new File(currentFilePath);
                if (f.exists()) {
                    if (!f.delete()) {
                        Log.w(TAG, "Failed to delete corrupted file");
                    }
                }
            }
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        broadcastStatus(false, 0);
        RecorderWidget.updateAllWidgets(this, false);

        // Notify file saved
        if (currentFilePath != null) {
            FileUtils.notifyMediaScanner(this, currentFilePath);
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ── Duration Timer ────────────────────────────────────────────

    private void startDurationTimer() {
        durationTimer = new Timer();
        durationTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (isRecording) {
                    long elapsed = (SystemClock.elapsedRealtime() - recordingStartTime) / 1000;
                    broadcastStatus(true, elapsed);
                    updateNotification(getString(R.string.recording_active) + " • " + formatDuration(elapsed));
                }
            }
        }, 1000, 1000);
    }

    private void stopDurationTimer() {
        if (durationTimer != null) {
            durationTimer.cancel();
            durationTimer = null;
        }
    }

    // ── Background Thread ─────────────────────────────────────────

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Background thread interrupted", e);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void broadcastStatus(boolean recording, long durationSeconds) {
        Intent intent = new Intent(BROADCAST_STATUS);
        intent.putExtra(EXTRA_IS_RECORDING, recording);
        intent.putExtra(EXTRA_DURATION, durationSeconds);
        if (currentFilePath != null) intent.putExtra(EXTRA_FILE_PATH, currentFilePath);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (isRecording) stopRecording();
        stopBackgroundThread();
    }
}
