package com.trevify.music;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

@UnstableApi
public class MusicService extends MediaSessionService {
    private MediaSession mediaSession;
    private ExoPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        // Setup Transparent Caching
        DataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory();
        DataSource.Factory defaultDataSourceFactory = new DefaultDataSource.Factory(this, httpDataSourceFactory);
        DataSource.Factory cacheDataSourceFactory = new CacheDataSource.Factory()
                .setCache(MusicCacheManager.getCache(this))
                .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true) // Handles audio focus automatically
                .setHandleAudioBecomingNoisy(true) // Pauses on headphones unplugged
                .setWakeMode(C.WAKE_MODE_NETWORK) // CRITICAL: Keeps CPU/WiFi awake during streaming
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
                .build();

        setupCrossfade();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .build();
    }

    private void setupCrossfade() {
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable androidx.media3.common.MediaItem mediaItem, int reason) {
                // Smooth fade-in when switching songs
                fadeVolume(0.2f, 1.0f, 1200);
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    fadeVolume(0.2f, 1.0f, 1200);
                }
            }
        });
    }

    private void fadeVolume(float from, float to, long duration) {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(from, to);
        animator.setDuration(duration);
        animator.addUpdateListener(animation -> {
            if (player != null) {
                player.setVolume((float) animation.getAnimatedValue());
            }
        });
        animator.start();
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }
}
