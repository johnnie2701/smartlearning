package com.gemma3n.smartlearning;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Replace R.layout.file_list_item with your actual item layout
// Replace R.id.fileNameTextView with the ID of the TextView in your item layout

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.FileViewHolder> {

    private List<File> files = new ArrayList<>();
    private OnFileClickListener listener;

    public interface OnFileClickListener {
        void onFileClick(File file);
    }

    public FileListAdapter(OnFileClickListener listener) {
        this.listener = listener;
    }

    public void setFiles(List<File> newFiles) {
        this.files.clear();
        if (newFiles != null) {
            this.files.addAll(newFiles);
        }
        notifyDataSetChanged(); // Or use DiffUtil for better performance
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.file_list_item, parent, false); // Create R.layout.file_list_item
        return new FileViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        File currentFile = files.get(position);
        holder.fileNameTextView.setText(currentFile.getName());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFileClick(currentFile);
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileNameTextView;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.fileNameTextView); // Define in R.layout.file_list_item
        }
    }
}
