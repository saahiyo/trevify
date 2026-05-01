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
        Song oldPlayingSong = this.playingSong;
        this.playingSong = song;

        if (oldPlayingSong != null && oldPlayingSong.isOnline) {
            int oldPos = findTrackPositionByHashCode(oldPlayingSong.id);
            if (oldPos != -1) notifyItemChanged(oldPos);
        }
        if (playingSong != null && playingSong.isOnline) {
            int newPos = findTrackPositionByHashCode(playingSong.id);
            if (newPos != -1) notifyItemChanged(newPos);
        }
    }

    private int findTrackPositionByHashCode(long hashCode) {
        for (int idx = 0; idx < tracks.size(); idx++) {
            if (tracks.get(idx).id.hashCode() == hashCode) {
                return idx;
            }
        }
        return -1;
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

        // Show favorite state
        updateFavIcon(holder, track);

        holder.binding.favIcon.setOnClickListener(v -> {
            boolean wasFav = FavoritesManager.getInstance(v.getContext()).isFavorite(track.id);
            FavoritesManager.getInstance(v.getContext()).toggleFavorite(track.id);
            if (!wasFav) {
                Song s = new Song(track.id.hashCode(), track.name, track.artist, track.albumName, track.downloadUrl, track.albumName.hashCode(), track.durationMs);
                s.albumArtUrl = track.albumArtUrl;
                s.isOnline = true;
                FavoritesManager.getInstance(v.getContext()).saveOnlineFavorite(track.id, s);
            } else {
                FavoritesManager.getInstance(v.getContext()).removeOnlineFavorite(track.id);
            }
            updateFavIcon(holder, track);
            boolean nowFav = FavoritesManager.getInstance(v.getContext()).isFavorite(track.id);
            android.widget.Toast.makeText(v.getContext(), nowFav ? "Added to favorites" : "Removed from favorites", android.widget.Toast.LENGTH_SHORT).show();
        });

        holder.binding.moreBtn.setOnClickListener(v -> {
            android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(v.getContext(), v);
            popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());

            boolean isFav = FavoritesManager.getInstance(v.getContext()).isFavorite(track.id);
            popupMenu.getMenu().findItem(R.id.menu_favorite).setTitle(isFav ? "Remove from Favorites" : "Add to Favorites");

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_play) {
                    if (listener != null) listener.onPlayClick(track, position);
                } else if (id == R.id.menu_favorite) {
                    boolean wasFav = FavoritesManager.getInstance(v.getContext()).isFavorite(track.id);
                    FavoritesManager.getInstance(v.getContext()).toggleFavorite(track.id);
                    if (!wasFav) {
                        Song s = new Song(track.id.hashCode(), track.name, track.artist, track.albumName, track.downloadUrl, track.albumName.hashCode(), track.durationMs);
                        s.albumArtUrl = track.albumArtUrl;
                        s.isOnline = true;
                        FavoritesManager.getInstance(v.getContext()).saveOnlineFavorite(track.id, s);
                    } else {
                        FavoritesManager.getInstance(v.getContext()).removeOnlineFavorite(track.id);
                    }
                    updateFavIcon(holder, track);
                    boolean nowFav = FavoritesManager.getInstance(v.getContext()).isFavorite(track.id);
                    android.widget.Toast.makeText(v.getContext(), nowFav ? "Added to favorites" : "Removed from favorites", android.widget.Toast.LENGTH_SHORT).show();
                } else if (id == R.id.menu_share) {
                    android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Listen to " + track.name + " by " + track.artist + "\n" + track.downloadUrl);
                    v.getContext().startActivity(android.content.Intent.createChooser(shareIntent, "Share " + track.name));
                } else if (id == R.id.menu_download) {
                    if (track.downloadUrl != null && !track.downloadUrl.isEmpty()) {
                        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(track.downloadUrl));
                        request.setTitle(track.name);
                        request.setDescription("Downloading " + track.artist);
                        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, track.name + ".mp3");
                        
                        android.app.DownloadManager manager = (android.app.DownloadManager) v.getContext().getSystemService(android.content.Context.DOWNLOAD_SERVICE);
                        if (manager != null) {
                            manager.enqueue(request);
                            android.widget.Toast.makeText(v.getContext(), "Download started", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        android.widget.Toast.makeText(v.getContext(), "Download URL not available", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            });
            popupMenu.show();
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

    private void updateFavIcon(ViewHolder holder, SaavnTrack track) {
        boolean isFav = FavoritesManager.getInstance(holder.binding.getRoot().getContext()).isFavorite(track.id);
        if (isFav) {
            holder.binding.favIcon.setImageResource(R.drawable.ic_favorite_24);
            holder.binding.favIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    holder.binding.getRoot().getContext().getColor(R.color.accent_vibrant)));
        } else {
            holder.binding.favIcon.setImageResource(R.drawable.ic_favorite_border_24);
            holder.binding.favIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    holder.binding.getRoot().getContext().getColor(R.color.text_tertiary)));
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemOnlineTrackBinding binding;

        public ViewHolder(ItemOnlineTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
