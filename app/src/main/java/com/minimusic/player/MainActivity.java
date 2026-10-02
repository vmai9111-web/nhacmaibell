package com.minimusic.player;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_CODE = 100;
    
    private MusicService musicService;
    private boolean isBound = false;

    private TextView tvSongTitle;
    private Button btnPlayPause, btnNext, btnPrev, btnLoop;
    private ListView listViewSongs;

    private final ArrayList<String> mp3Paths = new ArrayList<>();
    private final ArrayList<String> mp3Titles = new ArrayList<>();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;
            if (!mp3Paths.isEmpty()) {
                musicService.setPlaylist(mp3Paths, mp3Titles);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSongTitle = findViewById(R.id.tvSongTitle);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        btnLoop = findViewById(R.id.btnLoop);
        listViewSongs = findViewById(R.id.listViewSongs);

        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        checkAndRequestPermissions();

        btnPlayPause.setOnClickListener(v -> {
            if (isBound) {
                musicService.togglePlayPause();
                updateUI();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (isBound) {
                musicService.nextSong();
                updateUI();
            }
        });

        btnPrev.setOnClickListener(v -> {
            if (isBound) {
                musicService.prevSong();
                updateUI();
            }
        });

        btnLoop.setOnClickListener(v -> {
            if (isBound) {
                boolean looping = musicService.toggleLoop();
                btnLoop.setText(looping ? "🔂" : "🔁");
                Toast.makeText(this, looping ? "Đã bật lặp bài này" : "Đã tắt lặp bài", Toast.LENGTH_SHORT).show();
            }
        });

        listViewSongs.setOnItemClickListener((parent, view, position, id) -> {
            if (isBound) {
                musicService.playSong(position);
                updateUI();
            }
        });
    }

    private void checkAndRequestPermissions() {
        String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQ_CODE);
        } else {
            scanAudioFiles();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanAudioFiles();
        } else {
            Toast.makeText(this, "Cần cấp quyền đọc file để phát nhạc!", Toast.LENGTH_LONG).show();
        }
    }

    private void scanAudioFiles() {
        mp3Paths.clear();
        mp3Titles.clear();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String[] projection = {
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE
        };

        Cursor cursor = getContentResolver().query(uri, projection, selection, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String path = cursor.getString(0);
                String title = cursor.getString(1);
                if (path != null && path.endsWith(".mp3")) {
                    mp3Paths.add(path);
                    mp3Titles.add(title != null ? title : "Unknown");
                }
            }
            cursor.close();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mp3Titles);
        listViewSongs.setAdapter(adapter);

        if (isBound) {
            musicService.setPlaylist(mp3Paths, mp3Titles);
        }
    }

    private void updateUI() {
        if (isBound) {
            btnPlayPause.setText(musicService.isPlaying() ? "⏸" : "▶");
            tvSongTitle.setText(musicService.getCurrentTitle());
        }
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        super.onDestroy();
    }
}