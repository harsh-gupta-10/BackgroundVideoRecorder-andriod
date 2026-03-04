package com.bgrecorder.app.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileUtils {

    private static final String DIR_NAME = "BGRecorder";

    public static String generateOutputFilePath(Context context) {
        File dir = getRecordingsDir(context);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "VID_" + timeStamp + ".mp4";
        return new File(dir, fileName).getAbsolutePath();
    }

    public static File getRecordingsDir(Context context) {
        File dir;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Scoped storage — use app-specific external files dir
            dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), DIR_NAME);
        } else {
            dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES), DIR_NAME);
        }
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static List<File> getRecordings(Context context) {
        File dir = getRecordingsDir(context);
        File[] files = dir.listFiles(f -> f.getName().endsWith(".mp4"));
        if (files == null || files.length == 0) return new ArrayList<>();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        Collections.sort(list, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return list;
    }

    public static void notifyMediaScanner(Context context, String filePath) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(new File(filePath)));
        context.sendBroadcast(intent);
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static String formatDate(long millis) {
        return new SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.US).format(new Date(millis));
    }
}
