package com.trevify.music;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

@UnstableApi
public class MusicPlayerManager {
    private static MusicPlayerManager instance;
    private final Context context;
    private Player player; // Can be ExoPlayer or MediaController
    private ListenableFuture<MediaController> controllerFuture;
    private List<Song> originalList = new ArrayList<>();
    private List<Song> currentPlaylist = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    private boolean shouldPlayWhenReady = false;

    private final List<PlaybackListener> listeners = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                notifyProgress(player.getCurrentPosition(), player.getDuration());
                handler.postDelayed(this, 50); // Fast updates for smooth waveform
            }
        }
    };

    public interface PlaybackListener {
        void onSongChanged(Song song);
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgressUpdate(long currentPosition, long duration);
    }

    private MusicPlayerManager(Context context) {
        this.context = context.getApplicationContext();
        // Initialize MediaController to connect to MusicService
        SessionToken sessionToken = new SessionToken(this.context, new ComponentName(this.context, MusicService.class));
        controllerFuture = new MediaController.Builder(this.context, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
                setupPlayerListener();
                if (shouldPlayWhenReady && !currentPlaylist.isEmpty()) {
                    applyPlaylistToPlayer(0, true);
                    shouldPlayWhenReady = false;
                }
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setupPlayerListener() {
        if (player == null) return;
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                notifyPlaybackState(player.isPlaying());
                if (playbackState == Player.STATE_READY) {
                    handler.post(progressRunnable);
                } else {
                    handler.removeCallbacks(progressRunnable);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                notifyPlaybackState(isPlaying);
                if (isPlaying) {
                    handler.post(progressRunnable);
                } else {
                    handler.removeCallbacks(progressRunnable);
                }
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                // Find the song in our current playlist that matches this mediaItem
                if (mediaItem != null) {
                    for (Song s : currentPlaylist) {
                        if (s.getStableKey().equals(mediaItem.mediaId)) {
                            currentIndex = currentPlaylist.indexOf(s);
                            StatsManager.getInstance(context).logPlay(s);
                            notifySongChanged(s);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                error.printStackTrace();
                // If a stream fails (e.g. timeout or expired URL), skip to the next song automatically
                playNext();
            }
        });

        // Initial notification if something is already playing
        Song current = getCurrentSong();
        if (current != null) {
            notifySongChanged(current);
            notifyPlaybackState(player.isPlaying());
        }
    }

    public static synchronized MusicPlayerManager getInstance(Context context) {
        if (instance == null) {
            instance = new MusicPlayerManager(context);
        }
        return instance;
    }

    public void setPlaylist(List<Song> songs, int startIndex) {
        if (songs == null || songs.isEmpty()) return;
        if (startIndex < 0 || startIndex >= songs.size()) return;

        this.originalList = new ArrayList<>(songs);
        if (isShuffle) {
            this.currentPlaylist = new ArrayList<>(songs);
            Collections.shuffle(this.currentPlaylist);
            Song startSong = songs.get(startIndex);
            this.currentIndex = currentPlaylist.indexOf(startSong);
        } else {
            this.currentPlaylist = new ArrayList<>(songs);
            this.currentIndex = startIndex;
        }

        Song currentSong = getCurrentSong();
        if (currentSong != null) {
            notifySongChanged(currentSong);
        }

        if (player == null) {
            shouldPlayWhenReady = true;
            return;
        }

        applyPlaylistToPlayer(0, true);
    }

    private void applyPlaylistToPlayer(long startPositionMs, boolean play) {
        if (player == null || currentIndex < 0 || currentIndex >= currentPlaylist.size()) return;

        List<MediaItem> mediaItems = new ArrayList<>();
        for (Song song : currentPlaylist) {
            mediaItems.add(createMediaItem(song));
        }
        player.setMediaItems(mediaItems, currentIndex, startPositionMs);
        player.prepare();
        if (play) {
            player.play();
        }
    }

    private MediaItem createMediaItem(Song song) {
        Uri artworkUri;
        if (song.isOnline && song.albumArtUrl != null && !song.albumArtUrl.isEmpty()) {
            artworkUri = Uri.parse(song.albumArtUrl);
        } else {
            artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId);
        }

        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(artworkUri)
                .build();

        return new MediaItem.Builder()
                .setMediaId(song.getStableKey())
                .setUri(song.data)
                .setMediaMetadata(metadata)
                .build();
    }

    private void playCurrent() {
        if (player == null || currentIndex < 0 || currentIndex >= currentPlaylist.size()) return;
        player.seekTo(currentIndex, 0);
        player.prepare();
        player.play();
    }

    public void playNext() {
        if (player != null) {
            if (player.hasNextMediaItem()) {
                player.seekToNext();
            } else if (!currentPlaylist.isEmpty()) {
                player.seekTo(0, 0);
            }
        }
    }

    public void playPrevious() {
        if (player != null) {
            if (player.hasPreviousMediaItem()) {
                player.seekToPrevious();
            } else if (!currentPlaylist.isEmpty()) {
                player.seekTo(currentPlaylist.size() - 1, 0);
            }
        }
    }

    public void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    public void toggleShuffle() {
        isShuffle = !isShuffle;
        Song currentSong = getCurrentSong();
        if (isShuffle) {
            Collections.shuffle(currentPlaylist);
        } else {
            currentPlaylist = new ArrayList<>(originalList);
        }
        
        if (currentSong != null) {
            currentIndex = currentPlaylist.indexOf(currentSong);
        }

        if (player != null) {
            List<MediaItem> mediaItems = new ArrayList<>();
            for (Song song : currentPlaylist) {
                mediaItems.add(createMediaItem(song));
            }
            player.setMediaItems(mediaItems, currentIndex, player.getCurrentPosition());
        }
    }

    public void toggleRepeat() {
        if (player == null) return;
        isRepeat = !isRepeat;
        player.setRepeatMode(isRepeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public void seekTo(long position) {
        if (player != null) player.seekTo(position);
    }

    public Song getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < currentPlaylist.size()) {
            return currentPlaylist.get(currentIndex);
        }
        return null;
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public boolean isShuffle() {
        return isShuffle;
    }

    public boolean isRepeat() {
        return isRepeat;
    }

    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    public void addListener(PlaybackListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        // Initial notify
        Song current = getCurrentSong();
        if (current != null) {
            listener.onSongChanged(current);
            if (player != null) {
                listener.onPlaybackStateChanged(player.isPlaying());
                listener.onProgressUpdate(player.getCurrentPosition(), player.getDuration());
            }
        }
    }

    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    private void notifySongChanged(Song song) {
        List<PlaybackListener> listenersCopy = new ArrayList<>(listeners);
        for (PlaybackListener listener : listenersCopy) {
            listener.onSongChanged(song);
        }
    }

    private void notifyPlaybackState(boolean isPlaying) {
        List<PlaybackListener> listenersCopy = new ArrayList<>(listeners);
        for (PlaybackListener listener : listenersCopy) {
            listener.onPlaybackStateChanged(isPlaying);
        }
    }

    private void notifyProgress(long current, long duration) {
        List<PlaybackListener> listenersCopy = new ArrayList<>(listeners);
        for (PlaybackListener listener : listenersCopy) {
            listener.onProgressUpdate(current, duration);
        }
    }
}
