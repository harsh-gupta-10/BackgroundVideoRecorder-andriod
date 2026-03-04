package com.bgrecorder.app.utils;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bgrecorder.app.R;

import java.io.File;
import java.util.List;

public class RecordingAdapter extends RecyclerView.Adapter<RecordingAdapter.VH> {

    public interface OnFileAction { void onAction(File file); }

    private final List<File> files;
    private final OnFileAction onPlay, onShare, onDelete;

    public RecordingAdapter(List<File> files,
                            OnFileAction onPlay,
                            OnFileAction onShare,
                            OnFileAction onDelete) {
        this.files = files;
        this.onPlay = onPlay;
        this.onShare = onShare;
        this.onDelete = onDelete;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recording, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        File file = files.get(position);
        holder.tvName.setText(file.getName());
        holder.tvSize.setText(FileUtils.formatFileSize(file.length()));
        holder.tvDate.setText(FileUtils.formatDate(file.lastModified()));

        holder.btnPlay.setOnClickListener(v -> onPlay.onAction(file));
        holder.btnShare.setOnClickListener(v -> onShare.onAction(file));
        holder.btnDelete.setOnClickListener(v -> onDelete.onAction(file));
    }

    @Override
    public int getItemCount() { return files.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvSize, tvDate;
        ImageButton btnPlay, btnShare, btnDelete;

        VH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_rec_name);
            tvSize = v.findViewById(R.id.tv_rec_size);
            tvDate = v.findViewById(R.id.tv_rec_date);
            btnPlay = v.findViewById(R.id.btn_play);
            btnShare = v.findViewById(R.id.btn_share);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }
}
