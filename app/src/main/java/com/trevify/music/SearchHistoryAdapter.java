package com.trevify.music;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {

    public interface OnHistoryItemListener {
        void onItemClick(String query);
        void onFillClick(String query);
        void onDeleteClick(String query, int position);
    }

    private final List<String> history = new ArrayList<>();
    private final OnHistoryItemListener listener;

    public SearchHistoryAdapter(OnHistoryItemListener listener) {
        this.listener = listener;
    }

    public void setHistory(List<String> newHistory) {
        history.clear();
        history.addAll(newHistory);
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < history.size()) {
            history.remove(position);
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String query = history.get(position);
        holder.historyText.setText(query);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(query);
        });

        holder.fillBtn.setOnClickListener(v -> {
            if (listener != null) listener.onFillClick(query);
        });

        holder.deleteBtn.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onDeleteClick(query, pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return history.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView historyText;
        final ImageView fillBtn;
        final ImageView deleteBtn;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            historyText = itemView.findViewById(R.id.historyText);
            fillBtn = itemView.findViewById(R.id.fillBtn);
            deleteBtn = itemView.findViewById(R.id.deleteBtn);
        }
    }
}
