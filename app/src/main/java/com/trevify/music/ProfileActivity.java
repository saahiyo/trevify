package com.trevify.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;

import com.trevify.music.databinding.ActivityProfileBinding;

import java.util.HashSet;
import java.util.Set;

public class ProfileActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackListener, SongAdapter.OnItemClickListerner {
    private ActivityProfileBinding binding;
    private SharedPreferences themePrefs;
    private MusicPlayerManager playerManager;
    private static final String PREF_NAME = "theme_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private int appBarTopMargin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themePrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        applyTheme(themePrefs.getBoolean(KEY_DARK_MODE, false));
        EdgeToEdge.enable(this);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playerManager = MusicPlayerManager.getInstance(this);
        appBarTopMargin = ((ViewGroup.MarginLayoutParams) binding.backBtn.getLayoutParams()).topMargin;

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            updateAppBarTopInset(systemBars.top);
            return insets;
        });

        binding.backBtn.setOnClickListener(v -> finish());

        setupStats();
        setupThemeSwitch();
    }

    @Override
    protected void onStart() {
        super.onStart();
        playerManager.addListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        playerManager.removeListener(this);
    }

    @Override
    public void onSongChanged(Song song) {
        android.net.Uri albumArtUri = android.content.ContentUris.withAppendedId(android.net.Uri.parse("content://media/external/audio/albumart"), song.albumId);
        com.bumptech.glide.Glide.with(this)
                .asBitmap()
                .load(albumArtUri)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@androidx.annotation.NonNull android.graphics.Bitmap resource, @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        androidx.palette.graphics.Palette.from(resource).generate(palette -> {
                            if (palette != null) {
                                int vibrant = palette.getVibrantColor(getColor(R.color.blue));
                                applyDynamicColors(vibrant);
                            }
                        });
                    }
                    @Override public void onLoadCleared(@androidx.annotation.Nullable android.graphics.drawable.Drawable placeholder) {}
                });
    }

    private void applyDynamicColors(int color) {
        binding.totalSongsCount.setTextColor(color);
        binding.themeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        binding.aboutIcon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        // Also update the switch track color if possible, or just keep it subtle
    }

    @Override public void onPlaybackStateChanged(boolean isPlaying) {}
    @Override public void onProgressUpdate(long currentPosition, long duration) {}

    @Override
    public void onItemClick(int position, android.view.View view) {
        // Find the songs from the adapter
        if (binding.recyclerViewRecent.getAdapter() instanceof SongAdapter) {
            java.util.List<Song> songs = ((SongAdapter) binding.recyclerViewRecent.getAdapter()).getSongs();
            android.content.Intent intent = new android.content.Intent(this, playerActivity.class);
            intent.putParcelableArrayListExtra("songList", new java.util.ArrayList<>(songs));
            intent.putExtra("position", position);
            startActivity(intent);
        }
    }

    private void setupStats() {
        // Get Total Songs
        int totalSongs = 0;
        android.net.Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        try (Cursor cursor = getContentResolver().query(uri, null, selection, null, null)) {
            if (cursor != null) {
                totalSongs = cursor.getCount();
            }
        }
        binding.totalSongsCount.setText(String.valueOf(totalSongs));

        // Get Favorites Count
        SharedPreferences favPrefs = getSharedPreferences("music_favorites", MODE_PRIVATE);
        Set<String> favorites = favPrefs.getStringSet("favorite_ids", new HashSet<>());
        binding.favSongsCount.setText(String.valueOf(favorites.size()));

        // Setup Recent History
        setupRecentHistory();
    }

    private void setupRecentHistory() {
        StatsManager statsManager = StatsManager.getInstance(this);
        java.util.List<Long> recentIds = statsManager.getRecentIds();
        if (recentIds.isEmpty()) {
            binding.historyTitle.setVisibility(android.view.View.GONE);
            binding.recyclerViewRecent.setVisibility(android.view.View.GONE);
            return;
        }

        java.util.List<Song> allSongs = getAllSongs(); // I'll add this helper or reuse from elsewhere
        java.util.List<Song> recentSongs = new java.util.ArrayList<>();
        
        // Match IDs to full Song objects
        for (Long id : recentIds) {
            for (Song s : allSongs) {
                if (s.id == id) {
                    recentSongs.add(s);
                    break;
                }
            }
        }

        binding.recyclerViewRecent.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        SongAdapter adapter = new SongAdapter(recentSongs, this);
        binding.recyclerViewRecent.setAdapter(adapter);
    }

    private java.util.List<Song> getAllSongs() {
        java.util.List<Song> songs = new java.util.ArrayList<>();
        android.net.Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
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

    private void setupThemeSwitch() {
        boolean isDarkMode = themePrefs.getBoolean(KEY_DARK_MODE, false);
        binding.themeSwitch.setChecked(isDarkMode);

        binding.themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            themePrefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            applyTheme(isChecked);
        });
    }

    private void applyTheme(boolean isDark) {
        WindowInsetsControllerCompat controller =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(!isDark);
            controller.setAppearanceLightNavigationBars(!isDark);
        }

        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void updateAppBarTopInset(int topInset) {
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) binding.backBtn.getLayoutParams();
        int targetTopMargin = appBarTopMargin + topInset;
        if (params.topMargin != targetTopMargin) {
            params.topMargin = targetTopMargin;
            binding.backBtn.setLayoutParams(params);
        }
    }
}
