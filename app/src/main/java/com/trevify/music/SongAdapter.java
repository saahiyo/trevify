package com.trevify.music;

import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.PopupMenu;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.trevify.music.databinding.ItemSongBinding;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewholder> {
    private List<Song> songs;
    private List<Song> fullList;
    private OnItemClickListerner listener;
    private Song playingSong;

    public interface OnItemClickListerner {
        void onItemClick(int position);
    }

    public SongAdapter(List<Song> songs, OnItemClickListerner listener) {
        this.songs = songs;
        this.fullList = new ArrayList<>(songs);
        this.listener = listener;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
        this.fullList = new ArrayList<>(songs);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        songs = new ArrayList<>();
        if (query.isEmpty()) {
            songs.addAll(fullList);
        } else {
            String filterPattern = query.toLowerCase().trim();
            for (Song song : fullList) {
                if (song.title.toLowerCase().contains(filterPattern) ||
                    song.artist.toLowerCase().contains(filterPattern)) {
                    songs.add(song);
                }
            }
        }
        notifyDataSetChanged();
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setPlayingSong(Song song) {
        this.playingSong = song;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongAdapter.SongViewholder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongBinding binding=ItemSongBinding.inflate(
                LayoutInflater.from(parent.getContext()),parent,false);
        return new SongViewholder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull SongAdapter.SongViewholder holder, int position) {
        Song song=songs.get(position);
        holder.binding.textTitle.setText(song.title);
        holder.binding.textArtist.setText(song.artist + " • " + formatTime((int)(song.duration/1000)));

        Object imageSource;
        if (song.isOnline && song.albumArtUrl != null && !song.albumArtUrl.isEmpty()) {
            imageSource = song.albumArtUrl;
        } else {
            imageSource = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId);
        }

        Glide.with(holder.binding.getRoot().getContext())
                .load(imageSource)
                .placeholder(R.drawable.placeholder_img)
                .error(R.drawable.placeholder_img)
                .into(holder.binding.imageAlbumArt);
        // Highlight currently playing song
        if (playingSong != null && playingSong.id == song.id) {
            holder.binding.textTitle.setTextColor(holder.binding.getRoot().getContext().getColor(R.color.blue));
        } else {
            holder.binding.textTitle.setTextColor(holder.binding.getRoot().getContext().getColor(R.color.text_primary));
        }

        // Show favorite state
        updateFavIcon(holder, song);

        // Toggle favorite on heart tap
        holder.binding.favIcon.setOnClickListener(v -> {
            FavoritesManager.getInstance(v.getContext()).toggleFavorite(song.id);
            updateFavIcon(holder, song);
            boolean nowFav = FavoritesManager.getInstance(v.getContext()).isFavorite(song.id);
            Toast.makeText(v.getContext(), nowFav ? "Added to favorites" : "Removed from favorites", Toast.LENGTH_SHORT).show();
        });

        holder.binding.moreBtn.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
            popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());

            boolean isFav = FavoritesManager.getInstance(v.getContext()).isFavorite(song.id);
            popupMenu.getMenu().findItem(R.id.menu_favorite).setTitle(isFav ? "Remove from Favorites" : "Add to Favorites");

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_play) {
                    listener.onItemClick(position);
                } else if (id == R.id.menu_favorite) {
                    FavoritesManager.getInstance(v.getContext()).toggleFavorite(song.id);
                    updateFavIcon(holder, song);
                    boolean nowFav = FavoritesManager.getInstance(v.getContext()).isFavorite(song.id);
                    Toast.makeText(v.getContext(), nowFav ? "Added to favorites" : "Removed from favorites", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.menu_share) {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("audio/*");
                    Uri contentUri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    v.getContext().startActivity(Intent.createChooser(shareIntent, "Share " + song.title));
                }
                return true;
            });
            popupMenu.show();
        });
    }

    private void updateFavIcon(SongViewholder holder, Song song) {
        boolean isFav = FavoritesManager.getInstance(holder.binding.getRoot().getContext()).isFavorite(song.id);
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

    @Override
    public int getItemCount() {
        return songs.size();
    }

    private String formatTime(int seconds){
        return String.format("%02d:%02d",seconds/60,seconds%60);
    }

    public class SongViewholder extends RecyclerView.ViewHolder {
        final ItemSongBinding binding;
        final OnItemClickListerner listener;

        public SongViewholder(ItemSongBinding binding,OnItemClickListerner listerner) {
            super(binding.getRoot());
            this.binding=binding;
            this.listener=listerner;
            binding.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listerner!=null){
                     int pos=getAdapterPosition();
                     if (pos!=RecyclerView.NO_POSITION){
                         listener.onItemClick(pos);
                     }

                     }
                }
            });
        }
    }
}
