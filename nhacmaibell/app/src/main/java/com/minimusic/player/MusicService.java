package com.minimusic.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.ArrayList;

public class MusicService extends Service implements MediaPlayer.OnCompletionListener {

    private final IBinder binder = new LocalBinder();
    private MediaPlayer mediaPlayer;
    private ArrayList<String> songList = new ArrayList<>();
    private ArrayList<String> songTitles = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isLooping = false;

    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(
            new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        );
        mediaPlayer.setOnCompletionListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundServiceNotification("Light Music Player", "Đang sẵn sàng");
        return START_STICKY;
    }

    private void startForegroundServiceNotification(String title, String text) {
        String channelId = "MUSIC_CHANNEL";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId, "Music Playback", NotificationManager.IMPORTANCE_LOW
            );
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build();

        startForeground(1, notification);
    }

    public void setPlaylist(ArrayList<String> paths, ArrayList<String> titles) {
        this.songList = paths;
        this.songTitles = titles;
    }

    public void playSong(int index) {
        if (songList.isEmpty() || index < 0 || index >= songList.size()) return;
        currentIndex = index;
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(songList.get(currentIndex));
            mediaPlayer.prepare();
            mediaPlayer.setLooping(isLooping);
            mediaPlayer.start();
            
            startForegroundServiceNotification("Đang phát", songTitles.get(currentIndex));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        } else {
            if (songList.size() > 0) {
                if (mediaPlayer.getCurrentPosition() > 0) {
                    mediaPlayer.start();
                } else {
                    playSong(currentIndex);
                }
            }
        }
    }

    public void nextSong() {
        if (songList.isEmpty()) return;
        currentIndex = (currentIndex + 1) % songList.size();
        playSong(currentIndex);
    }

    public void prevSong() {
        if (songList.isEmpty()) return;
        currentIndex = (currentIndex - 1 + songList.size()) % songList.size();
        playSong(currentIndex);
    }

    public boolean toggleLoop() {
        isLooping = !isLooping;
        mediaPlayer.setLooping(isLooping);
        return isLooping;
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    public String getCurrentTitle() {
        if (!songTitles.isEmpty() && currentIndex < songTitles.size()) {
            return songTitles.get(currentIndex);
        }
        return "";
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (!isLooping) {
            nextSong();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}