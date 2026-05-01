package com.trevify.music;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
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

    public void refreshFavoriteStates() {
        notifyDataSetChanged();
    }

    public void setPlayingSong(Song song) {
        Song oldPlayingSong = this.playingSong;
        this.playingSong = song;

        if (oldPlayingSong != null && oldPlayingSong.isOnline) {
            int oldPos = findTrackPositionBySourceId(oldPlayingSong.sourceId);
            if (oldPos != -1) notifyItemChanged(oldPos);
        }
        if (playingSong != null && playingSong.isOnline) {
            int newPos = findTrackPositionBySourceId(playingSong.sourceId);
            if (newPos != -1) notifyItemChanged(newPos);
        }
    }

    private int findTrackPositionBySourceId(String sourceId) {
        if (sourceId == null || sourceId.isEmpty()) return -1;
        for (int idx = 0; idx < tracks.size(); idx++) {
            if (sourceId.equals(tracks.get(idx).id)) {
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
        
        boolean isCurrentTrack = playingSong != null && playingSong.isOnline && track.id.equals(playingSong.sourceId);
        if (isCurrentTrack) {
            holder.binding.onlineTitle.setTextColor(holder.binding.getRoot().getContext().getColor(R.color.blue));
        } else {
            holder.binding.onlineTitle.setTextColor(holder.binding.getRoot().getContext().getColor(R.color.text_primary));
        }
        updateEqualizer(holder, isCurrentTrack);
        
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
                s.sourceId = track.id;
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
                        s.sourceId = track.id;
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
    public void onViewRecycled(@NonNull ViewHolder holder) {
        stopEqualizer(holder);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void updateEqualizer(ViewHolder holder, boolean isPlaying) {
        if (isPlaying) {
            holder.binding.playingEqualizer.setVisibility(View.VISIBLE);
            startEqualizer(holder);
        } else {
            stopEqualizer(holder);
            holder.binding.playingEqualizer.setVisibility(View.GONE);
        }
    }

    private void startEqualizer(ViewHolder holder) {
        if (holder.binding.playingEqualizer.getTag() instanceof AnimatorSet) return;

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                createBarAnimator(holder.binding.equalizerBar1, 320, 0),
                createBarAnimator(holder.binding.equalizerBar2, 420, 120),
                createBarAnimator(holder.binding.equalizerBar3, 360, 70)
        );
        holder.binding.playingEqualizer.setTag(animatorSet);
        animatorSet.start();
    }

    private ObjectAnimator createBarAnimator(View bar, long duration, long delay) {
        bar.setPivotY(bar.getHeight());
        ObjectAnimator animator = ObjectAnimator.ofFloat(bar, View.SCALE_Y, 0.35f, 1f);
        animator.setDuration(duration);
        animator.setStartDelay(delay);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        return animator;
    }

    private void stopEqualizer(ViewHolder holder) {
        Object animator = holder.binding.playingEqualizer.getTag();
        if (animator instanceof AnimatorSet) {
            ((AnimatorSet) animator).cancel();
            holder.binding.playingEqualizer.setTag(null);
        }
        holder.binding.equalizerBar1.setScaleY(1f);
        holder.binding.equalizerBar2.setScaleY(1f);
        holder.binding.equalizerBar3.setScaleY(1f);
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
