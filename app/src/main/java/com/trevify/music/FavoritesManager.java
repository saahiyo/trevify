package com.trevify.music;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class FavoritesManager {
    private static final String PREF_NAME = "music_favorites";
    private static final String KEY_FAVORITES = "favorite_ids";
    private final SharedPreferences sharedPreferences;
    private static FavoritesManager instance;

    private FavoritesManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized FavoritesManager getInstance(Context context) {
        if (instance == null) {
            instance = new FavoritesManager(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isFavorite(long songId) {
        return isFavorite(String.valueOf(songId));
    }

    public void toggleFavorite(long songId) {
        toggleFavorite(String.valueOf(songId));
    }

    public boolean isFavorite(String songId) {
        Set<String> favorites = new HashSet<>(sharedPreferences.getStringSet(KEY_FAVORITES, new HashSet<>()));
        return favorites.contains(songId);
    }

    public void toggleFavorite(String songId) {
        Set<String> favorites = new HashSet<>(sharedPreferences.getStringSet(KEY_FAVORITES, new HashSet<>()));
        if (favorites.contains(songId)) {
            favorites.remove(songId);
        } else {
            favorites.add(songId);
        }
        sharedPreferences.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }

    public void saveOnlineFavorite(String stringId, Song song) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("id", song.id);
            json.put("title", song.title);
            json.put("artist", song.artist);
            json.put("album", song.album);
            json.put("data", song.data);
            json.put("albumId", song.albumId);
            json.put("duration", song.duration);
            json.put("albumArtUrl", song.albumArtUrl);
            json.put("isOnline", song.isOnline);
            sharedPreferences.edit().putString("online_" + stringId, json.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeOnlineFavorite(String stringId) {
        sharedPreferences.edit().remove("online_" + stringId).apply();
    }

    public java.util.List<Song> getOnlineFavorites() {
        java.util.List<Song> list = new java.util.ArrayList<>();
        java.util.Map<String, ?> allEntries = sharedPreferences.getAll();
        for (java.util.Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().startsWith("online_")) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject((String) entry.getValue());
                    Song song = new Song(
                            json.optLong("id"),
                            json.optString("title"),
                            json.optString("artist"),
                            json.optString("album"),
                            json.optString("data"),
                            json.optLong("albumId"),
                            json.optLong("duration")
                    );
                    song.albumArtUrl = json.optString("albumArtUrl");
                    song.isOnline = json.optBoolean("isOnline");
                    list.add(song);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return list;
    }
}
