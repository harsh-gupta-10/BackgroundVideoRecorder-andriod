package com.bgrecorder.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bgrecorder.app.databinding.ActivityMainBinding;
import com.bgrecorder.app.service.RecordingService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private boolean isRecording = false;
    private long recordingElapsedSeconds = 0;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean recording = intent.getBooleanExtra(RecordingService.EXTRA_IS_RECORDING, false);
            long duration = intent.getLongExtra(RecordingService.EXTRA_DURATION, 0);
            updateUI(recording, duration);
        }
    };

    // Permission launcher
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    startRecording();
                } else {
                    showPermissionRationale();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        binding.btnRecord.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                checkPermissionsAndRecord();
            }
        });

        binding.btnViewRecordings.setOnClickListener(v ->
                startActivity(new Intent(this, RecordingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(RecordingService.BROADCAST_STATUS);
        // LocalBroadcastManager doesn't strictly need TIRAMISU check for internal broadcasts,
        // but for system broadcasts it would. Here it's internal.
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, filter);
        
        // Sync state if service is running
        updateUI(RecordingService.isRunning, recordingElapsedSeconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver);
    }

    // ── Permission Handling ───────────────────────────────────────

    private void checkPermissionsAndRecord() {
        List<String> needed = new ArrayList<>();
        needed.add(Manifest.permission.CAMERA);
        needed.add(Manifest.permission.RECORD_AUDIO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // WRITE_EXTERNAL_STORAGE is only needed for API 28 and below for shared storage
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        List<String> toRequest = new ArrayList<>();
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
            }
        }

        if (toRequest.isEmpty()) {
            startRecording();
        } else {
            permissionLauncher.launch(toRequest.toArray(new String[0]));
        }
    }

    private void showPermissionRationale() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permissions_required)
                .setMessage(R.string.permissions_rationale)
                .setPositiveButton(R.string.grant, (d, w) -> checkPermissionsAndRecord())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ── Recording Control ─────────────────────────────────────────

    private void startRecording() {
        Intent intent = new Intent(this, RecordingService.class);
        intent.setAction(RecordingService.ACTION_START);
        // minSdk is 26 (Oreo), so startForegroundService is always available
        startForegroundService(intent);
        RecordingService.isRunning = true;
        updateUI(true, 0);
        Toast.makeText(this, R.string.recording_started, Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        Intent intent = new Intent(this, RecordingService.class);
        intent.setAction(RecordingService.ACTION_STOP);
        startService(intent);
        RecordingService.isRunning = false;
        updateUI(false, 0);
        Toast.makeText(this, R.string.recording_saved, Toast.LENGTH_SHORT).show();
    }

    // ── UI Update ─────────────────────────────────────────────────

    private void updateUI(boolean recording, long durationSeconds) {
        isRecording = recording;
        recordingElapsedSeconds = durationSeconds;

        if (recording) {
            binding.recordingIndicator.setVisibility(View.VISIBLE);
            binding.tvStatus.setText(R.string.recording_active);
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.recording_red));
            binding.btnRecord.setText(R.string.stop_recording);
            binding.btnRecord.setBackgroundColor(ContextCompat.getColor(this, R.color.stop_btn_color));
            binding.tvTimer.setText(formatDuration(durationSeconds));
            binding.tvTimer.setVisibility(View.VISIBLE);
            binding.tvWidgetHint.setVisibility(View.GONE);
        } else {
            binding.recordingIndicator.setVisibility(View.INVISIBLE);
            binding.tvStatus.setText(R.string.ready_to_record);
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            binding.btnRecord.setText(R.string.start_recording);
            binding.btnRecord.setBackgroundColor(ContextCompat.getColor(this, R.color.start_btn_color));
            binding.tvTimer.setVisibility(View.GONE);
            binding.tvWidgetHint.setVisibility(View.VISIBLE);
        }
    }

    private String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%02d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    // ── Menu ──────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
