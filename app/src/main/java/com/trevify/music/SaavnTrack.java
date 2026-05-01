package com.trevify.music;

public class SaavnTrack {
    public String id;
    public String name;
    public String artist;
    public String albumName;
    public String albumArtUrl;
    public String downloadUrl;
    public long durationMs;

    public SaavnTrack(String id, String name, String artist, String albumName, String albumArtUrl, String downloadUrl, long durationMs) {
        this.id = id;
        this.name = name;
        this.artist = artist;
        this.albumName = albumName;
        this.albumArtUrl = albumArtUrl;
        this.downloadUrl = downloadUrl;
        this.durationMs = durationMs;
    }
}
