package com.trevify.music;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.trevify.music.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnItemClickListerner, MusicPlayerManager.PlaybackListener {
    private ActivityMainBinding binding;
    private RecyclerView.Adapter adapter;
    private List<Song> songList;
    private MusicPlayerManager playerManager;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                Boolean storageGranted = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    storageGranted = results.getOrDefault(Manifest.permission.READ_MEDIA_AUDIO, false);
                } else {
                    storageGranted = results.getOrDefault(Manifest.permission.READ_EXTERNAL_STORAGE, false);
                }
                
                if (storageGranted) {
                    loadSongs();
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    private OnlineTrackAdapter onlineAdapter;
    private SongAdapter localAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playerManager = MusicPlayerManager.getInstance(this);

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = 
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) binding.textView.getLayoutParams();
            lp.topMargin = systemBars.top + (int)(48 * getResources().getDisplayMetrics().density);
            binding.textView.setLayoutParams(lp);
            return insets;
        });

        // Initialize adapters
        localAdapter = new SongAdapter(new ArrayList<>(), this);
        binding.recyclerViewSongs.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewSongs.setAdapter(localAdapter);

        onlineAdapter = new OnlineTrackAdapter((track, position) -> {
            // Convert to Song and play
            List<Song> converted = new ArrayList<>();
            for (int i = 0; i < onlineAdapter.getItemCount(); i++) {
                SaavnTrack t = onlineAdapter.getTrack(i);
                Song s = new Song(
                    t.id.hashCode(),
                    t.name,
                    t.artist,
                    t.albumName,
                    t.downloadUrl,
                    t.albumName.hashCode(),
                    t.durationMs
                );
                s.albumArtUrl = t.albumArtUrl;
                s.isOnline = true;
                converted.add(s);
            }
            playerManager.setPlaylist(converted, position);
        });
        binding.recyclerViewSpotify.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewSpotify.setAdapter(onlineAdapter);

        checkPermissionAndLoadSong();

        setupTabs();
        setupMiniPlayer();
        setupSearch();

        binding.favoritesBtn.setOnClickListener(v -> startActivity(new Intent(this, FavoritesActivity.class)));
        binding.profileBtn.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Explore"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Library"));

        // Set initial state
        binding.exploreContainer.setVisibility(android.view.View.VISIBLE);
        binding.libraryContainer.setVisibility(android.view.View.GONE);
        binding.searchEditText.setHint("Search JioSaavn...");

        binding.tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    binding.exploreContainer.setVisibility(android.view.View.VISIBLE);
                    binding.libraryContainer.setVisibility(android.view.View.GONE);
                    binding.searchEditText.setHint("Search JioSaavn...");
                    // Refresh online search if text exists
                    String query = binding.searchEditText.getText().toString();
                    if (!query.isEmpty()) performOnlineSearch(query);
                } else {
                    binding.exploreContainer.setVisibility(android.view.View.GONE);
                    binding.libraryContainer.setVisibility(android.view.View.VISIBLE);
                    binding.searchEditText.setHint("Search your library...");
                    // Filter local list
                    localAdapter.filter(binding.searchEditText.getText().toString());
                }
            }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
    }

    private void setupSearch() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            private final Handler handler = new Handler(android.os.Looper.getMainLooper());
            private Runnable workRunnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binding.tabLayout.getSelectedTabPosition() == 1) {
                    // Local search
                    localAdapter.filter(s.toString());
                } else {
                    // Online search with debounce
                    if (workRunnable != null) handler.removeCallbacks(workRunnable);
                    workRunnable = () -> performOnlineSearch(s.toString());
                    handler.postDelayed(workRunnable, 500);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Initial fetch for explore
        performOnlineSearch("trending");
    }

    private void performOnlineSearch(String query) {
        if (query.trim().isEmpty()) {
            binding.exploreHint.setVisibility(android.view.View.VISIBLE);
            binding.recyclerViewSpotify.setVisibility(android.view.View.GONE);
            return;
        }

        binding.exploreHint.setVisibility(android.view.View.GONE);
        binding.recyclerViewSpotify.setVisibility(android.view.View.GONE);
        binding.exploreLoading.setVisibility(android.view.View.VISIBLE);

        SaavnApi.searchSongs(query, 30, new SaavnApi.SearchCallback() {
            @Override
            public void onSuccess(List<SaavnTrack> tracks) {
                binding.exploreLoading.setVisibility(android.view.View.GONE);
                binding.recyclerViewSpotify.setVisibility(android.view.View.VISIBLE);
                onlineAdapter.setTracks(tracks);
                
                if (tracks.isEmpty()) {
                    binding.exploreHint.setText("No results found for '" + query + "'");
                    binding.exploreHint.setVisibility(android.view.View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                binding.exploreLoading.setVisibility(android.view.View.GONE);
                binding.exploreHint.setText("Error: " + error);
                binding.exploreHint.setVisibility(android.view.View.VISIBLE);
            }
        });
    }

    private void setupMiniPlayer() {
        binding.miniPlayer.setOnClickListener(v -> {
            Intent intent = new Intent(this, playerActivity.class);
            startActivity(intent);
        });

        binding.miniPlayPause.setOnClickListener(v -> playerManager.togglePlayPause());
        binding.miniPrev.setOnClickListener(v -> playerManager.playPrevious());
        binding.miniNext.setOnClickListener(v -> playerManager.playNext());
    }

    @Override
    protected void onStart() {
        super.onStart();
        playerManager.addListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh favorite icons when returning from another activity
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        playerManager.removeListener(this);
    }

    @Override
    public void onSongChanged(Song song) {
        if (localAdapter != null) localAdapter.setPlayingSong(song);
        if (onlineAdapter != null) onlineAdapter.setPlayingSong(song);
        
        binding.miniPlayer.setVisibility(android.view.View.VISIBLE);
        binding.miniTitle.setText(song.title);
        binding.miniArtist.setText(song.artist);
        
        // Update mini player tint to be more vibrant based on current theme
        binding.miniPlayPause.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.blue)));

        Object imageSource;
        if (song.isOnline && song.albumArtUrl != null && !song.albumArtUrl.isEmpty()) {
            imageSource = song.albumArtUrl;
        } else {
            imageSource = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId);
        }

        Glide.with(this)
                .asBitmap()
                .load(imageSource)
                .placeholder(R.drawable.placeholder_img)
                .error(R.drawable.placeholder_img)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@androidx.annotation.NonNull android.graphics.Bitmap resource, @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        binding.miniAlbumArt.setImageBitmap(resource);
                        androidx.palette.graphics.Palette.from(resource).generate(palette -> {
                            if (palette != null) {
                                int vibrant = palette.getVibrantColor(getColor(R.color.blue));
                                applyDynamicColors(vibrant);
                            }
                        });
                    }
                    @Override
                    public void onLoadStarted(@androidx.annotation.Nullable android.graphics.drawable.Drawable placeholder) {
                        binding.miniAlbumArt.setImageResource(R.drawable.placeholder_img);
                    }
                    @Override
                    public void onLoadFailed(@androidx.annotation.Nullable android.graphics.drawable.Drawable errorDrawable) {
                        binding.miniAlbumArt.setImageResource(R.drawable.placeholder_img);
                    }
                    @Override public void onLoadCleared(@androidx.annotation.Nullable android.graphics.drawable.Drawable placeholder) {}
                });

        Glide.with(this)
                .asBitmap()
                .load(imageSource)
                .apply(com.bumptech.glide.request.RequestOptions.bitmapTransform(new jp.wasabeef.glide.transformations.BlurTransformation(50, 4)))
                .into(binding.bgMainAlbumArt);
    }

    private void applyDynamicColors(int color) {
        binding.favoritesBtn.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        binding.profileBtn.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        binding.songCount.setTextColor(color);
        binding.miniPlayPause.setImageTintList(android.content.res.ColorStateList.valueOf(color));
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        binding.miniPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_24 : R.drawable.ic_play_arrow_24);
    }

    @Override
    public void onProgressUpdate(long currentPosition, long duration) {
        if (duration > 0) {
            int progress = (int) (currentPosition * 1000 / duration);
            binding.miniProgress.setProgress(progress);
        }
        String elapsed = formatTime((int) (currentPosition / 1000));
        String total = formatTime((int) (duration / 1000));
        binding.miniTime.setText(elapsed + " / " + total);
    }

    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void checkPermissionAndLoadSong() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        boolean allGranted = true;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            loadSongs();
        } else {
            requestPermissionsLauncher.launch(permissions.toArray(new String[0]));
        }
    }
    private List<Song> getSongs() {
        List<Song> songs = new ArrayList<>();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        String sortorder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = getContentResolver().query(uri,null,selection,null, sortorder)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    String album = cursor.getString(albumColumn);
                    String data = cursor.getString(dataColumn);
                    long albumId = cursor.getLong(albumIdColumn);
                    long duration = cursor.getLong(durationColumn);

                    songs.add(new Song(id, title, artist, album, data, albumId, duration));

                }
            }
        }
        return songs;
    }
    private void loadSongs() {
        songList = getSongs();
        if (songList.isEmpty()) {
            binding.recyclerViewSongs.setVisibility(android.view.View.GONE);
            binding.emptyState.setVisibility(android.view.View.VISIBLE);
            binding.songCount.setText("0 Songs");
        } else {
            binding.recyclerViewSongs.setVisibility(android.view.View.VISIBLE);
            binding.emptyState.setVisibility(android.view.View.GONE);
            localAdapter.setSongs(songList);
            binding.songCount.setText(songList.size() + " Songs");
        }
    }
    @Override
    public void onItemClick(int position) {
        List<Song> currentSongs;
        if (adapter instanceof SongAdapter) {
            currentSongs = ((SongAdapter) adapter).getSongs();
        } else {
            currentSongs = songList;
        }

        Intent intent = new Intent(this, playerActivity.class);
        intent.putParcelableArrayListExtra("songList", new ArrayList<>(currentSongs));
        intent.putExtra("position", position);
        startActivity(intent);
    }
}
