package com.trevify.music;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SearchCacheDao {
    @Query("SELECT * FROM search_cache WHERE cacheKey = :cacheKey LIMIT 1")
    CachedSearchResponse getByKey(String cacheKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedSearchResponse response);
}
