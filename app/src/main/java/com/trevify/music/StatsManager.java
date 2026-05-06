package com.trevify.music;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StatsManager {
    private static final String PREF_NAME = "music_stats";
    private static final String KEY_RECENT = "recent_song_ids";
    private static final String KEY_RECENT_SONGS = "recent_songs";
    private static final String PREFIX_COUNT = "count_";
    private static final int MAX_RECENT = 10;

    private final SharedPreferences prefs;
    private static StatsManager instance;

    private StatsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized StatsManager getInstance(Context context) {
        if (instance == null) {
            instance = new StatsManager(context.getApplicationContext());
        }
        return instance;
    }

    public void logPlay(long songId) {
        // 1. Log count
        String countKey = PREFIX_COUNT + songId;
        int currentCount = prefs.getInt(countKey, 0);
        prefs.edit().putInt(countKey, currentCount + 1).apply();

        // 2. Log recent
        List<Long> recent = getRecentIds();
        recent.remove(songId); // Remove if exists to move to front
        recent.add(0, songId);
        
        if (recent.size() > MAX_RECENT) {
            recent = recent.subList(0, MAX_RECENT);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recent.size(); i++) {
            sb.append(recent.get(i));
            if (i < recent.size() - 1) sb.append(",");
        }
        prefs.edit().putString(KEY_RECENT, sb.toString()).apply();
    }

    public void logPlay(Song song) {
        if (song == null) return;
        logPlay(song.id);

        try {
            JSONArray recent = getRecentSongsJson();
            JSONArray updated = new JSONArray();
            updated.put(toJson(song));

            String songKey = song.getStableKey();
            for (int i = 0; i < recent.length() && updated.length() < MAX_RECENT; i++) {
                JSONObject item = recent.optJSONObject(i);
                if (item == null) continue;
                String itemKey = item.optString("stableKey", item.optString("sourceId", item.optString("id", "")));
                if (!songKey.equals(itemKey)) {
                    updated.put(item);
                }
            }
            prefs.edit().putString(KEY_RECENT_SONGS, updated.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Long> getRecentIds() {
        String recentStr = prefs.getString(KEY_RECENT, "");
        List<Long> ids = new ArrayList<>();
        if (recentStr.isEmpty()) return ids;
        
        String[] parts = recentStr.split(",");
        for (String part : parts) {
            try {
                ids.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    public int getPlayCount(long songId) {
        return prefs.getInt(PREFIX_COUNT + songId, 0);
    }

    public List<Song> getRecentSongs() {
        List<Song> songs = new ArrayList<>();
        JSONArray recent = getRecentSongsJson();
        for (int i = 0; i < recent.length(); i++) {
            JSONObject item = recent.optJSONObject(i);
            if (item == null) continue;
            Song song = fromJson(item);
            if (song != null) {
                songs.add(song);
            }
        }
        return songs;
    }

    private JSONArray getRecentSongsJson() {
        try {
            return new JSONArray(prefs.getString(KEY_RECENT_SONGS, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private JSONObject toJson(Song song) throws Exception {
        JSONObject json = new JSONObject();
        json.put("stableKey", song.getStableKey());
        json.put("id", song.id);
        json.put("title", song.title);
        json.put("artist", song.artist);
        json.put("album", song.album);
        json.put("data", song.data);
        json.put("albumId", song.albumId);
        json.put("duration", song.duration);
        json.put("albumArtUrl", song.albumArtUrl);
        json.put("isOnline", song.isOnline);
        json.put("sourceId", song.sourceId);
        return json;
    }

    private Song fromJson(JSONObject json) {
        Song song = new Song(
                json.optLong("id"),
                json.optString("title", "Unknown Title"),
                json.optString("artist", "Unknown Artist"),
                json.optString("album", ""),
                json.optString("data", ""),
                json.optLong("albumId"),
                json.optLong("duration")
        );
        song.albumArtUrl = json.optString("albumArtUrl", "");
        song.isOnline = json.optBoolean("isOnline", false);
        song.sourceId = json.optString("sourceId", "");
        return song;
    }
}
