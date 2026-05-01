package com.trevify.music;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.trevify.music.databinding.ItemOnlineTrackBinding;

import java.util.ArrayList;
import java.util.List;

public class OnlineTrackAdapter extends RecyclerView.Adapter<OnlineTrackAdapter.ViewHolder> {
    private List<SaavnTrack> tracks = new ArrayList<>();
    private OnTrackClickListener listener;
    private Song playingSong;

    public interface OnTrackClickListener {
        void onPlayClick(SaavnTrack track, int position);
    }

    public OnlineTrackAdapter(OnTrackClickListener listener) {
        this.listener = listener;
    }

    public void setTracks(List<SaavnTrack> tracks) {
        this.tracks = tracks;
        notifyDataSetChanged();
    }

    public SaavnTrack getTrack(int position) {
        return tracks.get(position);
    }

    public void setPlayingSong(Song song) {
        this.playingSong = song;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOnlineTrackBinding binding = ItemOnlineTrackBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SaavnTrack track = tracks.get(position);
        
        // Highlight currently playing song
        if (playingSong != null && playingSong.isOnline && playingSong.id == track.id.hashCode()) {
            holder.binding.onlineTitle.setTextColor(holder.binding.getRoot().getContext().getColor(R.color.blue));
        } else {
            holder.binding.onlineTitle.setTextColor(holder.binding.getRoot().getContext().getColor(R.color.text_primary));
        }
        
        holder.binding.onlineTitle.setText(android.text.Html.fromHtml(track.name, android.text.Html.FROM_HTML_MODE_LEGACY).toString());
        String decodedArtist = android.text.Html.fromHtml(track.artist, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        holder.binding.onlineArtist.setText(decodedArtist + " • " + formatTime((int) (track.durationMs / 1000)));

        // Load album art from URL
        if (track.albumArtUrl != null && !track.albumArtUrl.isEmpty()) {
            Glide.with(holder.binding.getRoot().getContext())
                    .load(track.albumArtUrl)
                    .placeholder(R.drawable.placeholder_img)
                    .error(R.drawable.placeholder_img)
                    .into(holder.binding.onlineAlbumArt);
        } else {
            holder.binding.onlineAlbumArt.setImageResource(R.drawable.placeholder_img);
        }

        // Show favorite state (Coming soon for online tracks)
        holder.binding.favIcon.setOnClickListener(v -> {
            android.widget.Toast.makeText(v.getContext(), "Online favorites coming soon", android.widget.Toast.LENGTH_SHORT).show();
        });

        holder.binding.moreBtn.setOnClickListener(v -> {
            android.widget.Toast.makeText(v.getContext(), "Online track options coming soon", android.widget.Toast.LENGTH_SHORT).show();
        });

        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onPlayClick(track, position);
        });
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemOnlineTrackBinding binding;

        public ViewHolder(ItemOnlineTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
