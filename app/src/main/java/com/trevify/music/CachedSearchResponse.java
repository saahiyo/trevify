package com.trevify.music;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_cache")
public class CachedSearchResponse {
    @PrimaryKey
    @NonNull
    public String cacheKey;
    public String query;
    public int limit;
    public String responseJson;
    public long cachedAt;

    public CachedSearchResponse(@NonNull String cacheKey, String query, int limit, String responseJson, long cachedAt) {
        this.cacheKey = cacheKey;
        this.query = query;
        this.limit = limit;
        this.responseJson = responseJson;
        this.cachedAt = cachedAt;
    }
}
