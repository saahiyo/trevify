package com.trevify.music;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SaavnApi {
    private static final String BASE_URL = "https://jiosaavn-api.shakir-ansarii075.workers.dev/api";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SearchCallback {
        void onSuccess(List<SaavnTrack> tracks);
        void onError(String error);
    }

    public static void searchSongs(String query, int limit, SearchCallback callback) {
        String url = BASE_URL + "/search/songs?query=" + java.net.URLEncoder.encode(query) + "&limit=" + limit;

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

                    JSONObject json = new JSONObject(body);
                    if (!json.optBoolean("success", false)) {
                        mainHandler.post(() -> callback.onError(json.optString("message", "API Error")));
                        return;
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

                    mainHandler.post(() -> callback.onSuccess(tracks));
                } catch (Exception e) {
                    String finalBody = body;
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage() + "\nRaw: " + (finalBody.length() > 100 ? finalBody.substring(0, 100) : finalBody)));
                }
            }
        });
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
}
