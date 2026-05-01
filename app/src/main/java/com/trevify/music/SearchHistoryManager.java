package com.trevify.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SearchHistoryManager {
    private static final String PREF_NAME = "search_history_prefs";
    private static final String KEY_HISTORY = "search_history";
    private static final int MAX_HISTORY = 10;
    private final SharedPreferences prefs;
    private static SearchHistoryManager instance;

    private SearchHistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SearchHistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new SearchHistoryManager(context.getApplicationContext());
        }
        return instance;
    }

    public void addSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;
        query = query.trim();
        
        List<String> history = getHistory();
        history.remove(query); // Remove if exists to move to front
        history.add(0, query);
        
        if (history.size() > MAX_HISTORY) {
            history = history.subList(0, MAX_HISTORY);
        }
        
        saveHistory(history);
    }

    public List<String> getHistory() {
        String historyStr = prefs.getString(KEY_HISTORY, "");
        if (historyStr.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(historyStr.split("\\|\\|\\|")));
    }

    public void removeSearch(String query) {
        if (query == null) return;
        List<String> history = getHistory();
        history.remove(query.trim());
        saveHistory(history);
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }

    private void saveHistory(List<String> history) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            sb.append(history.get(i));
            if (i < history.size() - 1) sb.append("|||");
        }
        prefs.edit().putString(KEY_HISTORY, sb.toString()).apply();
    }
}
