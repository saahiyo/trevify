package com.trevify.music;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.trevify.music.databinding.ActivityFavoritesBinding;

import java.util.ArrayList;
import java.util.List;

public class FavoritesActivity extends AppCompatActivity implements SongAdapter.OnItemClickListerner, MusicPlayerManager.PlaybackListener {
    private ActivityFavoritesBinding binding;
    private List<Song> favoriteSongs = new ArrayList<>();
    private MusicPlayerManager playerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityFavoritesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playerManager = MusicPlayerManager.getInstance(this);

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            
            // Add top margin instead of translation to keep constraints working properly
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = 
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) binding.backBtn.getLayoutParams();
            lp.topMargin = systemBars.top + (int)(16 * getResources().getDisplayMetrics().density);
            binding.backBtn.setLayoutParams(lp);

            return insets;
        });

        binding.backBtn.setOnClickListener(v -> finish());
        binding.recyclerViewFavorites.setLayoutManager(new LinearLayoutManager(this));

        loadFavorites();
        setupMiniPlayer();
    }

    private void loadFavorites() {
        List<Song> allSongs = getAllSongs();
        FavoritesManager favManager = FavoritesManager.getInstance(this);
        favoriteSongs.clear();
        for (Song song : allSongs) {
            if (favManager.isFavorite(song.id)) {
                favoriteSongs.add(song);
            }
        }

        if (favoriteSongs.isEmpty()) {
            binding.recyclerViewFavorites.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.favCount.setText("0 Favorites");
        } else {
            binding.recyclerViewFavorites.setVisibility(View.VISIBLE);
            binding.emptyState.setVisibility(View.GONE);
            SongAdapter adapter = new SongAdapter(favoriteSongs, this);
            binding.recyclerViewFavorites.setAdapter(adapter);
            binding.favCount.setText(favoriteSongs.size() + " Favorites");
        }
    }

    private List<Song> getAllSongs() {
        List<Song> songs = new ArrayList<>();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        String sortorder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = getContentResolver().query(uri, null, selection, null, sortorder)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

                while (cursor.moveToNext()) {
                    songs.add(new Song(
                            cursor.getLong(idColumn),
                            cursor.getString(titleColumn),
                            cursor.getString(artistColumn),
                            cursor.getString(albumColumn),
                            cursor.getString(dataColumn),
                            cursor.getLong(albumIdColumn),
                            cursor.getLong(durationColumn)
                    ));
                }
            }
        }
        return songs;
    }

    private void setupMiniPlayer() {
        binding.miniPlayer.setOnClickListener(v -> startActivity(new Intent(this, playerActivity.class)));
        binding.miniPlayPause.setOnClickListener(v -> playerManager.togglePlayPause());
    }

    @Override
    protected void onStart() {
        super.onStart();
        playerManager.addListener(this);
        loadFavorites(); // Refresh in case something was unfavorited in player
    }

    @Override
    protected void onStop() {
        super.onStop();
        playerManager.removeListener(this);
    }

    @Override
    public void onItemClick(int position) {
        Intent intent = new Intent(this, playerActivity.class);
        intent.putParcelableArrayListExtra("songList", new ArrayList<>(favoriteSongs));
        intent.putExtra("position", position);
        startActivity(intent);
    }

    @Override
    public void onSongChanged(Song song) {
        binding.miniPlayer.setVisibility(View.VISIBLE);
        binding.miniTitle.setText(song.title);
        binding.miniArtist.setText(song.artist);
        binding.miniPlayPause.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.blue)));

        Uri albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId);
        Glide.with(this)
                .load(albumArtUri)
                .placeholder(R.drawable.placeholder_img)
                .error(R.drawable.placeholder_img)
                .into(binding.miniAlbumArt);
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        binding.miniPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_24 : R.drawable.ic_play_arrow_24);
    }

    @Override
    public void onProgressUpdate(long currentPosition, long duration) {}
}
