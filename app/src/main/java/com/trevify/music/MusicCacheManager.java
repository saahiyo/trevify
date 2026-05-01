package com.trevify.music;

import android.content.Context;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import java.io.File;

@UnstableApi
public class MusicCacheManager {
    private static SimpleCache cache;
    private static final long CACHE_SIZE = 100 * 1024 * 1024; // 100MB

    public static synchronized SimpleCache getCache(Context context) {
        if (cache == null) {
            File cacheDir = new File(context.getCacheDir(), "music_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            cache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(CACHE_SIZE), new StandaloneDatabaseProvider(context));
        }
        return cache;
    }
}
