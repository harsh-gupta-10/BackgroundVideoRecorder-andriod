package com.bgrecorder.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.bgrecorder.app.R;
import com.bgrecorder.app.service.RecordingService;

public class RecorderWidget extends AppWidgetProvider {

    private static final String TAG = "RecorderWidget";
    public static final String ACTION_START_RECORDING = "com.bgrecorder.app.START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "com.bgrecorder.app.STOP_RECORDING";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called");
        boolean recording = RecordingService.isRunning;
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId, recording);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);

        if (ACTION_START_RECORDING.equals(action)) {
            Intent serviceIntent = new Intent(context, RecordingService.class);
            serviceIntent.setAction(RecordingService.ACTION_START);
            context.startForegroundService(serviceIntent);
            updateAllWidgets(context, true);

        } else if (ACTION_STOP_RECORDING.equals(action)) {
            Intent serviceIntent = new Intent(context, RecordingService.class);
            serviceIntent.setAction(RecordingService.ACTION_STOP);
            context.startService(serviceIntent);
            updateAllWidgets(context, false);
        }
    }

    public static void updateAllWidgets(Context context, boolean isRecording) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, RecorderWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) {
            updateWidget(context, manager, id, isRecording);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager,
                                     int widgetId, boolean isRecording) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_recorder);

        if (isRecording) {
            views.setImageViewResource(R.id.widget_btn_icon, R.drawable.ic_stop_circle);
            views.setTextViewText(R.id.widget_btn_label, context.getString(R.string.stop_recording));
            views.setTextViewText(R.id.widget_status, context.getString(R.string.recording_active));
            views.setImageViewResource(R.id.widget_status_dot_img, R.drawable.dot_recording);

            Intent stopIntent = new Intent(context, RecorderWidget.class);
            stopIntent.setAction(ACTION_STOP_RECORDING);
            PendingIntent stopPending = PendingIntent.getBroadcast(context, 2, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_btn_bg, stopPending);
        } else {
            views.setImageViewResource(R.id.widget_btn_icon, R.drawable.ic_record_circle);
            views.setTextViewText(R.id.widget_btn_label, context.getString(R.string.start_recording));
            views.setTextViewText(R.id.widget_status, context.getString(R.string.tap_to_record));
            views.setImageViewResource(R.id.widget_status_dot_img, R.drawable.dot_idle);

            Intent startIntent = new Intent(context, RecorderWidget.class);
            startIntent.setAction(ACTION_START_RECORDING);
            PendingIntent startPending = PendingIntent.getBroadcast(context, 1, startIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_btn_bg, startPending);
        }

        manager.updateAppWidget(widgetId, views);
    }
}
