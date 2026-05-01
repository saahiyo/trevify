package com.trevify.music;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class Song implements Parcelable {
    public long id;
    public String title;
    public String artist;
    public String album;
    public String data;
    public long albumId;
    public long duration;
    public String albumArtUrl;
    public boolean isOnline;

    public Song(long id, String title, String artist, String album, String data, long albumId, long duration) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.data = data;
        this.albumId = albumId;
        this.duration = duration;
        this.albumArtUrl = "";
        this.isOnline = false;
    }

    protected Song(Parcel in) {
        id = in.readLong();
        title = in.readString();
        artist = in.readString();
        album = in.readString();
        data = in.readString();
        albumId = in.readLong();
        duration = in.readLong();
        albumArtUrl = in.readString();
        isOnline = in.readByte() != 0;
    }

    public static final Creator<Song> CREATOR = new Creator<Song>() {
        @Override
        public Song createFromParcel(Parcel in) {
            return new Song(in);
        }

        @Override
        public Song[] newArray(int size) {
            return new Song[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeString(data);
        dest.writeLong(albumId);
        dest.writeLong(duration);
        dest.writeString(albumArtUrl);
        dest.writeByte((byte) (isOnline ? 1 : 0));
    }
}

