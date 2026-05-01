package com.trevify.music;

import static com.bumptech.glide.request.RequestOptions.bitmapTransform;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.ContentUris;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.animation.OvershootInterpolator;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.frolo.waveformseekbar.WaveformSeekBar;
import com.trevify.music.databinding.ActivityPlayerBinding;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jp.wasabeef.glide.transformations.BlurTransformation;

public class playerActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackListener {
    private ActivityPlayerBinding binding;
    private MusicPlayerManager playerManager;
    private List<Song> songList = new ArrayList<>();
    private ObjectAnimator playPauseAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportPostponeEnterTransition();
        EdgeToEdge.enable(this);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playerManager = MusicPlayerManager.getInstance(this);

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            binding.backBtn.setTranslationY(systemBars.top);
            binding.textView2.setTranslationY(systemBars.top);
            binding.favBtn.setTranslationY(systemBars.top);
            binding.moreBtnPlayer.setTranslationY(systemBars.top);
            return insets;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            songList = getIntent().getParcelableArrayListExtra("songList", Song.class);
        } else {
            songList = getIntent().getParcelableArrayListExtra("songList");
        }
        int startIndex = getIntent().getIntExtra("position", 0);

        if (songList != null && !songList.isEmpty()) {
            Song current = playerManager.getCurrentSong();
            if (current == null || !current.getStableKey().equals(songList.get(startIndex).getStableKey())) {
                playerManager.setPlaylist(songList, startIndex);
            }
        } else {
            // Restore from playerManager's existing playlist if possible (e.g. mini player entry)
            Song current = playerManager.getCurrentSong();
            if (current == null) {
                Toast.makeText(this, "No song to play", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        binding.waveformSeekBar.setWaveform(createwaveform(), true);
        setupControls();
        setupAnimations();

        binding.backBtn.setOnClickListener(v -> finish());
    }

    private void setupAnimations() {
        playPauseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                binding.cardAlbumArt,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.05f, 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.05f, 1f)
        );
        playPauseAnimator.setDuration(300);
        playPauseAnimator.setInterpolator(new OvershootInterpolator());
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

    private void setupControls() {
        binding.buttonPlayPause.setOnClickListener(v -> {
            playerManager.togglePlayPause();
            playPauseAnimator.start();
        });
        binding.buttonNext.setOnClickListener(v -> playerManager.playNext());
        binding.buttonPrev.setOnClickListener(v -> playerManager.playPrevious());
        binding.buttonShuffle.setOnClickListener(v -> {
            playerManager.toggleShuffle();
            updateShuffleRepeatIcons();
        });
        binding.buttonRepeat.setOnClickListener(v -> {
            playerManager.toggleRepeat();
            updateShuffleRepeatIcons();
        });

        binding.favBtn.setOnClickListener(v -> {
            Song currentSong = playerManager.getCurrentSong();
            if (currentSong != null) {
                boolean wasFav = FavoritesManager.getInstance(this).isFavorite(currentSong);
                FavoritesManager.getInstance(this).toggleFavorite(currentSong);
                if (currentSong.isOnline) {
                    if (!wasFav) {
                        FavoritesManager.getInstance(this).saveOnlineFavorite(currentSong.getStableKey(), currentSong);
                    } else {
                        FavoritesManager.getInstance(this).removeOnlineFavorite(currentSong.getStableKey());
                    }
                }
                updateFavoriteIcon(currentSong);
                
                // Animate heart
                binding.favBtn.animate()
                        .scaleX(1.4f).scaleY(1.4f)
                        .setDuration(150)
                        .withEndAction(() -> binding.favBtn.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                        .start();
            }
        });

        binding.moreBtnPlayer.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(this, v);
            popupMenu.getMenuInflater().inflate(R.menu.song_menu, popupMenu.getMenu());
            popupMenu.getMenu().findItem(R.id.menu_play).setVisible(false); // Already playing

            Song currentSong = playerManager.getCurrentSong();
            if (currentSong != null) {
                boolean isFav = FavoritesManager.getInstance(this).isFavorite(currentSong);
                popupMenu.getMenu().findItem(R.id.menu_favorite).setTitle(isFav ? "Remove from Favorites" : "Add to Favorites");
                
                android.view.MenuItem downloadItem = popupMenu.getMenu().findItem(R.id.menu_download);
                if (downloadItem != null) {
                    downloadItem.setVisible(currentSong.isOnline);
                }
            }

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_favorite && currentSong != null) {
                    boolean wasFav = FavoritesManager.getInstance(this).isFavorite(currentSong);
                    FavoritesManager.getInstance(this).toggleFavorite(currentSong);
                    if (currentSong.isOnline) {
                        if (!wasFav) {
                            FavoritesManager.getInstance(this).saveOnlineFavorite(currentSong.getStableKey(), currentSong);
                        } else {
                            FavoritesManager.getInstance(this).removeOnlineFavorite(currentSong.getStableKey());
                        }
                    }
                    updateFavoriteIcon(currentSong);
                } else if (id == R.id.menu_share && currentSong != null) {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    if (currentSong.isOnline) {
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, "Listen to " + currentSong.title + " by " + currentSong.artist + "\n" + currentSong.data);
                    } else {
                        shareIntent.setType("audio/*");
                        android.net.Uri contentUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currentSong.id);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share " + currentSong.title));
                } else if (id == R.id.menu_download && currentSong != null && currentSong.isOnline) {
                    if (currentSong.data != null && !currentSong.data.isEmpty()) {
                        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(currentSong.data));
                        request.setTitle(currentSong.title);
                        request.setDescription("Downloading " + currentSong.artist);
                        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, currentSong.title + ".mp3");
                        
                        android.app.DownloadManager manager = (android.app.DownloadManager) getSystemService(android.content.Context.DOWNLOAD_SERVICE);
                        if (manager != null) {
                            manager.enqueue(request);
                            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Download URL not available", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            });
            popupMenu.show();
        });

        binding.waveformSeekBar.setCallback(new WaveformSeekBar.Callback() {
            @Override
            public void onProgressChanged(WaveformSeekBar seekBar, float percent, boolean fromUser) {
                if (fromUser) {
                    long duration = playerManager.getDuration();
                    long seekPos = (long) (percent * duration);
                    playerManager.seekTo(seekPos);
                    binding.textElapsed.setText(formatTime((int) (seekPos / 1000)));
                }
            }
            @Override public void onStartTrackingTouch(WaveformSeekBar seekBar) {}
            @Override public void onStopTrackingTouch(WaveformSeekBar seekBar) {}
        });

        updateShuffleRepeatIcons();
    }

    private void updateShuffleRepeatIcons() {
        int activeColor = ContextCompat.getColor(this, R.color.blue);
        if (playerManager.isShuffle()) {
            binding.buttonShuffle.setColorFilter(activeColor);
        } else {
            binding.buttonShuffle.clearColorFilter();
        }

        if (playerManager.isRepeat()) {
            binding.buttonRepeat.setColorFilter(activeColor);
        } else {
            binding.buttonRepeat.clearColorFilter();
        }
    }

    @Override
    public void onSongChanged(Song song) {
        updateUI(song);
        updateFavoriteIcon(song);
    }

    private void updateFavoriteIcon(Song song) {
        boolean isFav = FavoritesManager.getInstance(this).isFavorite(song);
        binding.favBtn.setImageResource(isFav ? R.drawable.ic_favorite_24 : R.drawable.ic_favorite_border_24);
        if (isFav) {
            binding.favBtn.setColorFilter(getColor(R.color.accent_vibrant));
        } else {
            binding.favBtn.clearColorFilter();
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        binding.buttonPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_24 : R.drawable.ic_play_arrow_24);
        if (isPlaying) {
            animateAlbumArt(true);
        } else {
            animateAlbumArt(false);
        }
    }

    private void animateAlbumArt(boolean play) {
        float scale = play ? 1.0f : 0.9f;
        binding.cardAlbumArt.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(400)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    @Override
    public void onProgressUpdate(long currentPosition, long duration) {
        if (duration > 0) {
            float progressPercent = ((float) currentPosition / duration);
            binding.waveformSeekBar.setProgressInPercentage(progressPercent);
            binding.textElapsed.setText(formatTime((int) (currentPosition / 1000)));
            binding.textDuration.setText(formatTime((int) (duration / 1000)));
        }
    }

    private void updateUI(Song song) {
        binding.textTitle.setText(song.title != null ? song.title : "Unknown Title");
        binding.textArtist.setText(song.artist != null ? song.artist : "Unknown Artist");
        
        Object imageSource;
        if (song.isOnline && song.albumArtUrl != null && !song.albumArtUrl.isEmpty()) {
            imageSource = song.albumArtUrl;
        } else {
            imageSource = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), song.albumId);
        }
        
        loadImages(imageSource);
    }

    private void loadImages(Object source) {
        // Reset card before loading
        binding.imageAlbumArtPlayer.setPadding(0, 0, 0, 0);
        binding.imageAlbumArtPlayer.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        binding.imageAlbumArtPlayer.setImageTintList(null);

        Glide.with(this)
                .asBitmap()
                .load(source)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        binding.imageAlbumArtPlayer.setImageBitmap(resource);
                        supportStartPostponedEnterTransition();
                        Palette.from(resource).generate(palette -> {
                            if (palette != null) {
                                // Prefer Vibrant, then LightVibrant, then Muted
                                int vibrant = palette.getVibrantColor(
                                        palette.getLightVibrantColor(
                                                palette.getMutedColor(0xFF4D28FF)
                                        )
                                );
                                applyDynamicColors(vibrant);
                            }
                        });
                    }
                    @Override public void onLoadCleared(@Nullable Drawable placeholder) {}
                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        binding.imageAlbumArtPlayer.setImageResource(R.drawable.placeholder_img);
                        supportStartPostponedEnterTransition();
                        binding.imageAlbumArtPlayer.setPadding(0, 0, 0, 0);
                        binding.imageAlbumArtPlayer.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
//                        binding.bgAalbumArt.setImageDrawable(null);
//                        binding.bgAalbumArt.setBackgroundResource(R.drawable.player_bg_gradient);
                        applyDynamicColors(0xFF3617c9);
                    }
                });

        Glide.with(this)
                .asBitmap()
                .load(source)
                .apply(bitmapTransform(new BlurTransformation(50, 4)))
                .into(binding.bgAalbumArt);
    }

    private void applyDynamicColors(int color) {
        binding.buttonPlayPause.setBackgroundTintList(ColorStateList.valueOf(color));
        binding.waveformSeekBar.setWaveProgressColor(color);
        
        // Use a more substantial tint for the background overlay (around 40% opacity)
        // This ensures the dark gradient we added in XML is reinforced with the song's color
        int tintColor = (0x66000000 & 0xFF000000) | (color & 0x00FFFFFF);
        binding.backgroundOverlay.setBackgroundColor(tintColor);
        
        binding.backgroundOverlay.setAlpha(0f);
        binding.backgroundOverlay.animate().alpha(1.0f).setDuration(600).start();
        
        // Header buttons and title are now white in XML for consistency on busy backgrounds
        // We only tint the Waveform and Play/Pause button for visual pop
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private int[] createwaveform() {
        Random random = new Random();
        int[] values = new int[50];
        for (int i = 0; i < values.length; i++) {
            values[i] = 10 + random.nextInt(60);
        }
        return values;
    }
}
