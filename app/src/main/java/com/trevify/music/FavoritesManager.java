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
        Set<String> favorites = new HashSet<>(sharedPreferences.getStringSet(KEY_FAVORITES, new HashSet<>()));
        return favorites.contains(String.valueOf(songId));
    }

    public void toggleFavorite(long songId) {
        Set<String> favorites = new HashSet<>(sharedPreferences.getStringSet(KEY_FAVORITES, new HashSet<>()));
        String idStr = String.valueOf(songId);
        if (favorites.contains(idStr)) {
            favorites.remove(idStr);
        } else {
            favorites.add(idStr);
        }
        sharedPreferences.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }
}
