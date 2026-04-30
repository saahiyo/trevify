package com.trevify.music;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class StatsManager {
    private static final String PREF_NAME = "music_stats";
    private static final String KEY_RECENT = "recent_song_ids";
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
}
