package com.trevify.music;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SaavnApi {
    private static final String BASE_URL = "https://jiosaavn-api.shakir-ansarii075.workers.dev/api";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService cacheExecutor = Executors.newSingleThreadExecutor();

    public interface SearchCallback {
        void onSuccess(List<SaavnTrack> tracks);
        void onError(String error);
    }

    public static void searchSongs(String query, int limit, SearchCallback callback) {
        fetchSongsFromNetwork(query, limit, null, callback);
    }

    public static void searchSongs(Context context, String query, int limit, boolean forceRefresh, SearchCallback callback) {
        searchSongs(context, query, 1, limit, forceRefresh, callback);
    }

    public static void searchSongs(Context context, String query, int page, int limit, boolean forceRefresh, SearchCallback callback) {
        String normalizedQuery = normalizeQuery(query);
        String cacheKey = buildSearchCacheKey(normalizedQuery, page, limit);

        cacheExecutor.execute(() -> {
            SearchCacheDao dao = AppDatabase.getInstance(context).searchCacheDao();
            if (!forceRefresh) {
                CachedSearchResponse cached = dao.getByKey(cacheKey);
                if (cached != null && cached.responseJson != null && !cached.responseJson.isEmpty()) {
                    try {
                        List<SaavnTrack> cachedTracks = parseSearchResponse(cached.responseJson);
                        mainHandler.post(() -> callback.onSuccess(cachedTracks));
                        return;
                    } catch (Exception ignored) {
                        // Fall through to network if a stored response can no longer be parsed.
                    }
                }
            }

            fetchSongsFromNetwork(normalizedQuery, page, limit, dao, callback);
        });
    }

    public static void searchArtistSongs(Context context, String artist, int limit, boolean forceRefresh, SearchCallback callback) {
        searchArtistSongs(context, artist, 1, limit, forceRefresh, callback);
    }

    public static void searchArtistSongs(Context context, String artist, int page, int limit, boolean forceRefresh, SearchCallback callback) {
        String normalizedArtist = normalizeQuery(artist);
        String cacheKey = buildArtistCacheKey(normalizedArtist, page, limit);

        cacheExecutor.execute(() -> {
            SearchCacheDao dao = AppDatabase.getInstance(context).searchCacheDao();
            if (!forceRefresh) {
                CachedSearchResponse cached = dao.getByKey(cacheKey);
                if (cached != null && cached.responseJson != null && !cached.responseJson.isEmpty()) {
                    try {
                        List<SaavnTrack> cachedTracks = parseArtistSongsResponse(cached.responseJson);
                        mainHandler.post(() -> callback.onSuccess(cachedTracks));
                        return;
                    } catch (Exception ignored) {
                        // Fall through to network if a stored artist response is stale or malformed.
                    }
                }
            }

            fetchArtistSongsFromNetwork(normalizedArtist, page, limit, dao, callback);
        });
    }

    private static void fetchSongsFromNetwork(String query, int limit, SearchCacheDao cacheDao, SearchCallback callback) {
        fetchSongsFromNetwork(query, 1, limit, cacheDao, callback);
    }

    private static void fetchSongsFromNetwork(String query, int page, int limit, SearchCacheDao cacheDao, SearchCallback callback) {
        String url = BASE_URL + "/search/songs?query=" + java.net.URLEncoder.encode(query) + "&page=" + page + "&limit=" + limit;

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = "";
                try {
                    body = response.body().string();
                    if (!response.isSuccessful()) {
                        String finalBody = body;
                        mainHandler.post(() -> callback.onError("Server error (" + response.code() + "): " + finalBody));
                        return;
                    }

                    List<SaavnTrack> tracks = parseSearchResponse(body);
                    if (cacheDao != null) {
                        String normalizedQuery = normalizeQuery(query);
                        String responseJson = body;
                        cacheExecutor.execute(() -> cacheDao.upsert(new CachedSearchResponse(
                                buildSearchCacheKey(normalizedQuery, page, limit),
                                normalizedQuery,
                                limit,
                                responseJson,
                                System.currentTimeMillis()
                        )));
                    }
                    mainHandler.post(() -> callback.onSuccess(tracks));
                } catch (Exception e) {
                    String finalBody = body;
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage() + "\nRaw: " + (finalBody.length() > 100 ? finalBody.substring(0, 100) : finalBody)));
                }
            }
        });
    }

    private static void fetchArtistSongsFromNetwork(String artist, int limit, SearchCacheDao cacheDao, SearchCallback callback) {
        fetchArtistSongsFromNetwork(artist, 1, limit, cacheDao, callback);
    }

    private static void fetchArtistSongsFromNetwork(String artist, int page, int limit, SearchCacheDao cacheDao, SearchCallback callback) {
        String searchUrl = BASE_URL + "/search/artists?query=" + java.net.URLEncoder.encode(artist) + "&limit=1";

        Request searchRequest = new Request.Builder().url(searchUrl).build();
        client.newCall(searchRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String searchBody = "";
                try {
                    searchBody = response.body().string();
                    if (!response.isSuccessful()) {
                        String finalBody = searchBody;
                        mainHandler.post(() -> callback.onError("Artist lookup error (" + response.code() + "): " + finalBody));
                        return;
                    }

                    ArtistLookup artistLookup = parseArtistLookupResponse(searchBody);
                    if (artistLookup.id.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                        return;
                    }

                    fetchArtistSongsById(artist, artistLookup.id, page, limit, cacheDao, callback);
                } catch (Exception e) {
                    String finalBody = searchBody;
                    mainHandler.post(() -> callback.onError("Artist parse error: " + e.getMessage() + "\nRaw: " + (finalBody.length() > 100 ? finalBody.substring(0, 100) : finalBody)));
                }
            }
        });
    }

    private static void fetchArtistSongsById(String artist, String artistId, int limit, SearchCacheDao cacheDao, SearchCallback callback) {
        fetchArtistSongsById(artist, artistId, 1, limit, cacheDao, callback);
    }

    private static void fetchArtistSongsById(String artist, String artistId, int page, int limit, SearchCacheDao cacheDao, SearchCallback callback) {
        String songsUrl = BASE_URL + "/artists/" + java.net.URLEncoder.encode(artistId) + "/songs?page=" + page + "&limit=" + limit;

        Request songsRequest = new Request.Builder().url(songsUrl).build();
        client.newCall(songsRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = "";
                try {
                    body = response.body().string();
                    if (!response.isSuccessful()) {
                        String finalBody = body;
                        mainHandler.post(() -> callback.onError("Artist songs error (" + response.code() + "): " + finalBody));
                        return;
                    }

                    List<SaavnTrack> tracks = parseArtistSongsResponse(body);
                    if (cacheDao != null) {
                        String normalizedArtist = normalizeQuery(artist);
                        String responseJson = body;
                        cacheExecutor.execute(() -> cacheDao.upsert(new CachedSearchResponse(
                                buildArtistCacheKey(normalizedArtist, page, limit),
                                normalizedArtist,
                                limit,
                                responseJson,
                                System.currentTimeMillis()
                        )));
                    }
                    mainHandler.post(() -> callback.onSuccess(tracks));
                } catch (Exception e) {
                    String finalBody = body;
                    mainHandler.post(() -> callback.onError("Artist songs parse error: " + e.getMessage() + "\nRaw: " + (finalBody.length() > 100 ? finalBody.substring(0, 100) : finalBody)));
                }
            }
        });
    }

    private static List<SaavnTrack> parseSearchResponse(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        if (!json.optBoolean("success", false)) {
            throw new IllegalStateException(json.optString("message", "API Error"));
        }

        List<SaavnTrack> tracks = new ArrayList<>();
        JSONObject data = json.optJSONObject("data");
        if (data != null) {
            JSONArray results = data.optJSONArray("results");
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    tracks.add(parseTrack(item));
                }
            }
        }
        return tracks;
    }

    private static List<SaavnTrack> parseArtistSongsResponse(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        if (!json.optBoolean("success", false)) {
            throw new IllegalStateException(json.optString("message", "API Error"));
        }

        List<SaavnTrack> tracks = new ArrayList<>();
        JSONObject data = json.optJSONObject("data");
        if (data != null) {
            JSONArray songs = data.optJSONArray("songs");
            if (songs == null) {
                songs = data.optJSONArray("topSongs");
            }
            if (songs != null) {
                for (int i = 0; i < songs.length(); i++) {
                    JSONObject item = songs.getJSONObject(i);
                    if ("song".equals(item.optString("type", "song"))) {
                        tracks.add(parseTrack(item));
                    }
                }
            }
        }
        return tracks;
    }

    private static ArtistLookup parseArtistLookupResponse(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        if (!json.optBoolean("success", false)) {
            throw new IllegalStateException(json.optString("message", "API Error"));
        }

        JSONObject data = json.optJSONObject("data");
        if (data == null) return new ArtistLookup("", "");

        JSONArray results = data.optJSONArray("results");
        if (results == null || results.length() == 0) return new ArtistLookup("", "");

        JSONObject artist = results.getJSONObject(0);
        return new ArtistLookup(artist.optString("id", ""), artist.optString("name", ""));
    }

    private static String buildSearchCacheKey(String query, int limit) {
        return buildSearchCacheKey(query, 1, limit);
    }

    private static String buildSearchCacheKey(String query, int page, int limit) {
        return "songs|" + query + "|" + page + "|" + limit;
    }

    private static String buildArtistCacheKey(String artist, int limit) {
        return buildArtistCacheKey(artist, 1, limit);
    }

    private static String buildArtistCacheKey(String artist, int page, int limit) {
        return "artist-songs|" + artist + "|" + page + "|" + limit;
    }

    private static String normalizeQuery(String query) {
        if (query == null) return "";
        return query.trim().toLowerCase(Locale.US);
    }

    private static SaavnTrack parseTrack(JSONObject item) {
        String id = item.optString("id", "");
        String name = item.optString("name", "Unknown");
        // duration in JioSaavn API is usually in seconds
        long durationMs = item.optLong("duration", 0) * 1000;

        // Album name
        String albumName = "";
        JSONObject album = item.optJSONObject("album");
        if (album != null) {
            albumName = album.optString("name", "");
        }

        // Artist
        String artist = "Unknown";
        JSONObject artists = item.optJSONObject("artists");
        if (artists != null) {
            JSONArray primary = artists.optJSONArray("primary");
            if (primary != null && primary.length() > 0) {
                artist = primary.optJSONObject(0).optString("name", "Unknown");
            }
        }

        // Image (get highest quality)
        String albumArtUrl = "";
        JSONArray images = item.optJSONArray("image");
        if (images != null && images.length() > 0) {
            albumArtUrl = images.optJSONObject(images.length() - 1).optString("url", "");
        }

        // Download URL (get highest quality)
        String downloadUrl = "";
        JSONArray downloadUrls = item.optJSONArray("downloadUrl");
        if (downloadUrls != null && downloadUrls.length() > 0) {
            downloadUrl = downloadUrls.optJSONObject(downloadUrls.length() - 1).optString("url", "");
        }

        return new SaavnTrack(id, name, artist, albumName, albumArtUrl, downloadUrl, durationMs);
    }

    private static class ArtistLookup {
        final String id;
        final String name;

        ArtistLookup(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
