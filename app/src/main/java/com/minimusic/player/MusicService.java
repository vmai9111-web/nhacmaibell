package com.minimusic.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.ArrayList;

public class MusicService extends Service implements MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {

    private final IBinder binder = new LocalBinder();
    private MediaPlayer mediaPlayer;
    private MediaSession mediaSession;
    private AudioManager audioManager;

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

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Khởi tạo MediaSession nhận tín hiệu tai nghe (có dây & Bluetooth)
        setupMediaSession();
    }

    private void setupMediaSession() {
        mediaSession = new MediaSession(this, "nhacmaibell_session");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            togglePlayPause();
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            nextSong();
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            prevSong();
                            return true;
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }

            @Override
            public void onPlay() {
                if (!isPlaying()) togglePlayPause();
            }

            @Override
            public void onPause() {
                if (isPlaying()) togglePlayPause();
            }

            @Override
            public void onSkipToNext() {
                nextSong();
            }

            @Override
            public void onSkipToPrevious() {
                prevSong();
            }
        });

        mediaSession.setActive(true);
        updatePlaybackState(PlaybackState.STATE_STOPPED);
    }

    private void updatePlaybackState(int state) {
        if (mediaSession == null) return;

        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

        PlaybackState playbackState = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0, 1.0f)
                .build();

        mediaSession.setPlaybackState(playbackState);
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return false;
        int result = audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
        );
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    updatePlaybackState(PlaybackState.STATE_PAUSED);
                    startForegroundServiceNotification("Đã tạm dừng", getCurrentTitle());
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                break;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundServiceNotification("nhacmaibell", "Đang sẵn sàng");
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

        if (!requestAudioFocus()) return;

        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(songList.get(currentIndex));
            mediaPlayer.prepare();
            mediaPlayer.setLooping(isLooping);
            mediaPlayer.start();

            updatePlaybackState(PlaybackState.STATE_PLAYING);
            startForegroundServiceNotification("Đang phát", songTitles.get(currentIndex));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            updatePlaybackState(PlaybackState.STATE_PAUSED);
            startForegroundServiceNotification("Đã tạm dừng", getCurrentTitle());
        } else {
            if (!songList.isEmpty()) {
                if (requestAudioFocus()) {
                    if (mediaPlayer.getCurrentPosition() > 0) {
                        mediaPlayer.start();
                    } else {
                        playSong(currentIndex);
                        return;
                    }
                    updatePlaybackState(PlaybackState.STATE_PLAYING);
                    startForegroundServiceNotification("Đang phát", getCurrentTitle());
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
        return mediaPlayer != null && mediaPlayer.isPlaying();
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
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (audioManager != null) {
            audioManager.abandonAudioFocus(this);
        }
        super.onDestroy();
    }
}
