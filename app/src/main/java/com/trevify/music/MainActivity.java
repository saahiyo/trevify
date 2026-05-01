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
    private String currentExploreQuery = "";
    private String lastLoadedExploreQuery = null;
    private int searchRequestGeneration = 0;
    private int selectedContentNavItemId = R.id.nav_home;
    
    private android.view.View exploreContainer;
    private android.view.View libraryContainer;
    private RecyclerView recyclerViewSpotify;
    private RecyclerView recyclerViewSongs;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout exploreRefresh;
    private android.widget.ProgressBar exploreLoading;
    private android.widget.TextView exploreHint;
    private android.widget.LinearLayout emptyState;
    private android.view.View historyContainer;
    private RecyclerView historyRecyclerView;
    private android.widget.TextView clearAllBtn;
    private SearchHistoryAdapter searchHistoryAdapter;

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
                    requestNotificationPermissionIfNeeded();
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

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
            v.setPadding(systemBars.left, 0, systemBars.right, 0);

            // Apply top padding to AppBarLayout so it completely pushes down all pinned/sticky elements
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0);
            binding.bottomNavigation.setPadding(0, 0, 0, systemBars.bottom);
            android.widget.FrameLayout.LayoutParams navParams =
                    (android.widget.FrameLayout.LayoutParams) binding.bottomNavigation.getLayoutParams();
            navParams.height = dp(80) + systemBars.bottom;
            binding.bottomNavigation.setLayoutParams(navParams);

            android.widget.FrameLayout.LayoutParams miniParams =
                    (android.widget.FrameLayout.LayoutParams) binding.miniPlayer.getLayoutParams();
            miniParams.bottomMargin = dp(92) + systemBars.bottom;
            binding.miniPlayer.setLayoutParams(miniParams);

            return insets;
        });

        // Handle back press to close search overlay
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.searchToolbar.getVisibility() == android.view.View.VISIBLE) {
                    binding.searchBackBtn.performClick();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        exploreContainer = getLayoutInflater().inflate(R.layout.layout_explore, null);
        libraryContainer = getLayoutInflater().inflate(R.layout.layout_library, null);
        exploreContainer.setLayoutParams(new android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        libraryContainer.setLayoutParams(new android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        
        exploreLoading = exploreContainer.findViewById(R.id.exploreLoading);
        exploreRefresh = exploreContainer.findViewById(R.id.exploreRefresh);
        exploreHint = exploreContainer.findViewById(R.id.exploreHint);
        recyclerViewSpotify = exploreContainer.findViewById(R.id.recyclerViewSpotify);
        historyContainer = binding.historyContainer;
        historyRecyclerView = binding.historyRecyclerView;
        clearAllBtn = binding.clearAllBtn;
        recyclerViewSongs = libraryContainer.findViewById(R.id.recyclerViewSongs);
        emptyState = libraryContainer.findViewById(R.id.emptyState);

        // Setup search history adapter
        searchHistoryAdapter = new SearchHistoryAdapter(new SearchHistoryAdapter.OnHistoryItemListener() {
            @Override
            public void onItemClick(String query) {
                binding.searchEditText.setText(query);
                binding.searchEditText.setSelection(query.length());
                performOnlineSearch(query);
            }

            @Override
            public void onFillClick(String query) {
                // Fill the search bar without triggering search
                binding.searchEditText.setText(query);
                binding.searchEditText.setSelection(query.length());
            }

            @Override
            public void onDeleteClick(String query, int position) {
                SearchHistoryManager.getInstance(MainActivity.this).removeSearch(query);
                searchHistoryAdapter.removeItem(position);
                // Hide history if no more items
                if (searchHistoryAdapter.getItemCount() == 0) {
                    historyContainer.setVisibility(android.view.View.GONE);
                }
            }
        });
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(searchHistoryAdapter);

        // Clear all history button
        clearAllBtn.setOnClickListener(v -> {
            SearchHistoryManager.getInstance(this).clearHistory();
            historyContainer.setVisibility(android.view.View.GONE);
        });

        // Initialize adapters
        localAdapter = new SongAdapter(new ArrayList<>(), this);
        recyclerViewSongs.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewSongs.setAdapter(localAdapter);

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
                s.sourceId = t.id;
                converted.add(s);
            }
            playerManager.setPlaylist(converted, position);
        });
        recyclerViewSpotify.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewSpotify.setAdapter(onlineAdapter);
        exploreRefresh.setOnRefreshListener(() -> {
            String query = binding.searchEditText.getText().toString();
            performOnlineSearch(query, true);
        });

        checkPermissionAndLoadSong();

        setupNavigation();
        setupMiniPlayer();
        setupSearch();

        binding.favoritesBtn.setOnClickListener(v -> startActivity(new Intent(this, FavoritesActivity.class)));
        binding.profileBtn.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void setupNavigation() {
        binding.viewPager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @androidx.annotation.NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(viewType == 0 ? exploreContainer : libraryContainer) {};
            }
            @Override
            public void onBindViewHolder(@androidx.annotation.NonNull RecyclerView.ViewHolder holder, int position) {}
            @Override
            public int getItemCount() { return 2; }
            @Override
            public int getItemViewType(int position) { return position; }
        });

        binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedContentNavItemId = R.id.nav_home;
                binding.viewPager.setCurrentItem(0, true);
                return true;
            } else if (itemId == R.id.nav_search) {
                openSearchOverlay();
                return false;
            } else if (itemId == R.id.nav_library) {
                selectedContentNavItemId = R.id.nav_library;
                binding.viewPager.setCurrentItem(1, true);
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return false;
            }
            return false;
        });

        binding.viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    selectedContentNavItemId = R.id.nav_home;
                    if (binding.bottomNavigation.getSelectedItemId() != R.id.nav_home) {
                        binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
                    }
                    binding.searchEditText.setHint("Search JioSaavn...");
                    String query = binding.searchEditText.getText().toString();
                    performOnlineSearch(query);
                } else {
                    selectedContentNavItemId = R.id.nav_library;
                    if (binding.bottomNavigation.getSelectedItemId() != R.id.nav_library) {
                        binding.bottomNavigation.setSelectedItemId(R.id.nav_library);
                    }
                    binding.searchEditText.setHint("Search your library...");
                    localAdapter.filter(binding.searchEditText.getText().toString());
                }
                updateSongCount();
            }
        });
    }

    private void setupSearch() {
        // Back button closes search overlay
        binding.searchBackBtn.setOnClickListener(v -> {
            binding.searchToolbar.setVisibility(android.view.View.GONE);
            binding.historyContainer.setVisibility(android.view.View.GONE);
            
            binding.headerSection.setVisibility(android.view.View.VISIBLE);
            binding.bottomNavigation.setVisibility(android.view.View.VISIBLE);
            binding.bottomNavigation.setSelectedItemId(selectedContentNavItemId);
            
            binding.searchEditText.clearFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(binding.searchEditText.getWindowToken(), 0);
            
        });

        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            private final Handler handler = new Handler(android.os.Looper.getMainLooper());
            private Runnable workRunnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Show/hide clear button
                binding.searchClearBtn.setVisibility(
                    s.length() > 0 ? android.view.View.VISIBLE : android.view.View.GONE);

                // Show history if empty
                if (s.length() == 0) {
                    showHistory();
                } else {
                    binding.historyContainer.setVisibility(android.view.View.GONE);
                }

                if (binding.viewPager.getCurrentItem() == 1) {
                    // Local search
                    localAdapter.filter(s.toString());
                    updateSongCount();
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

        // Clear button clears the search text
        binding.searchClearBtn.setOnClickListener(v -> {
            binding.searchEditText.setText("");
            binding.searchEditText.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        
        binding.searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                // Save history on search submit
                String query = binding.searchEditText.getText().toString().trim();
                if (!query.isEmpty() && binding.viewPager.getCurrentItem() == 0) {
                    SearchHistoryManager.getInstance(this).addSearch(query);
                }
                
                binding.searchEditText.clearFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(binding.searchEditText.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        // Initial fetch for explore with diverse high-quality queries
        String[] defaultQueries = {
            "top songs 2026",
            "bollywood hits", 
            "lofi mashup",
            "latest hindi", 
            "punjabi hits",
            "arijit singh"
        };
        currentExploreQuery = defaultQueries[new java.util.Random().nextInt(defaultQueries.length)];
        performOnlineSearch(currentExploreQuery);
    }

    private void openSearchOverlay() {
        binding.headerSection.setVisibility(android.view.View.GONE);
        binding.bottomNavigation.setVisibility(android.view.View.GONE);

        binding.searchToolbar.setVisibility(android.view.View.VISIBLE);

        binding.searchEditText.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(binding.searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);

        if (binding.searchEditText.getText().toString().isEmpty()) {
            showHistory();
        }
    }

    private void performOnlineSearch(String query) {
        performOnlineSearch(query, false);
    }

    private void performOnlineSearch(String query, boolean forceRefresh) {
        final String finalQuery;
        if (query.trim().isEmpty()) {
            if (binding.searchToolbar.getVisibility() == android.view.View.VISIBLE) {
                showHistory();
            }
            if (currentExploreQuery != null && !currentExploreQuery.isEmpty()) {
                finalQuery = currentExploreQuery;
            } else {
                exploreHint.setVisibility(android.view.View.VISIBLE);
                recyclerViewSpotify.setVisibility(android.view.View.GONE);
                return;
            }
        } else {
            finalQuery = query.trim();
            // No history saving on debounce, we do it in IME_ACTION_SEARCH
        }

        if (!forceRefresh && finalQuery.equals(lastLoadedExploreQuery)) {
            exploreLoading.setVisibility(android.view.View.GONE);
            exploreRefresh.setRefreshing(false);
            updateSongCount();
            return;
        }

        exploreHint.setVisibility(android.view.View.GONE);
        recyclerViewSpotify.setVisibility(android.view.View.GONE);
        exploreLoading.setVisibility(forceRefresh ? android.view.View.GONE : android.view.View.VISIBLE);

        final int requestGeneration = ++searchRequestGeneration;
        SaavnApi.searchSongs(this, finalQuery, 30, forceRefresh, new SaavnApi.SearchCallback() {
            @Override
            public void onSuccess(List<SaavnTrack> tracks) {
                if (requestGeneration != searchRequestGeneration) return;

                exploreLoading.setVisibility(android.view.View.GONE);
                exploreRefresh.setRefreshing(false);
                recyclerViewSpotify.setVisibility(android.view.View.VISIBLE);
                onlineAdapter.setTracks(tracks);
                lastLoadedExploreQuery = finalQuery;
                updateSongCount();
                
                if (tracks.isEmpty()) {
                    exploreHint.setText("No results found for '" + finalQuery + "'");
                    exploreHint.setVisibility(android.view.View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                if (requestGeneration != searchRequestGeneration) return;

                exploreLoading.setVisibility(android.view.View.GONE);
                exploreRefresh.setRefreshing(false);
                exploreHint.setText("Error: " + error);
                exploreHint.setVisibility(android.view.View.VISIBLE);
            }
        });
    }

    private void showHistory() {
        if (binding.viewPager.getCurrentItem() != 0) return;
        if (binding.searchToolbar.getVisibility() != android.view.View.VISIBLE) return;

        List<String> history = SearchHistoryManager.getInstance(this).getHistory();
        if (history.isEmpty()) {
            binding.historyContainer.setVisibility(android.view.View.GONE);
            return;
        }

        binding.historyContainer.setVisibility(android.view.View.VISIBLE);
        searchHistoryAdapter.setHistory(history);
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
        if (localAdapter != null) localAdapter.refreshFavoriteStates();
        if (onlineAdapter != null) onlineAdapter.refreshFavoriteStates();
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void checkPermissionAndLoadSong() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
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
            requestNotificationPermissionIfNeeded();
        } else {
            requestPermissionsLauncher.launch(permissions.toArray(new String[0]));
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
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
    private void updateSongCount() {
        if (binding == null || binding.songCount == null) return;
        if (binding.viewPager.getCurrentItem() == 0) {
            int count = onlineAdapter != null ? onlineAdapter.getItemCount() : 0;
            binding.songCount.setText(count + " Songs");
        } else {
            int count = localAdapter != null ? localAdapter.getItemCount() : 0;
            binding.songCount.setText(count + " Songs");
        }
    }

    private void loadSongs() {
        songList = getSongs();
        if (songList.isEmpty()) {
            recyclerViewSongs.setVisibility(android.view.View.GONE);
            emptyState.setVisibility(android.view.View.VISIBLE);
        } else {
            recyclerViewSongs.setVisibility(android.view.View.VISIBLE);
            emptyState.setVisibility(android.view.View.GONE);
            localAdapter.setSongs(songList);
        }
        updateSongCount();
    }
    @Override
    public void onItemClick(int position, android.view.View view) {
        List<Song> currentSongs;
        if (localAdapter != null && binding.viewPager.getCurrentItem() == 1) {
            currentSongs = localAdapter.getSongs();
        } else {
            currentSongs = songList;
        }

        Intent intent = new Intent(this, playerActivity.class);
        intent.putParcelableArrayListExtra("songList", new ArrayList<>(currentSongs));
        intent.putExtra("position", position);

        androidx.core.app.ActivityOptionsCompat options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                this, view, "album_art_transition");
        startActivity(intent, options.toBundle());
    }
}
