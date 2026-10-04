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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_CODE = 100;
    
    private MusicService musicService;
    private boolean isBound = false;

    private TextView tvSongTitle;
    private Button btnPlayPause, btnNext, btnPrev, btnLoop;
    private LinearLayout layoutPlaylists;
    private ListView listViewSongs;

    private static class SongItem {
        String path;
        String title;
        String folderName;

        SongItem(String path, String title, String folderName) {
            this.path = path;
            this.title = title;
            this.folderName = folderName;
        }
    }

    private final ArrayList<SongItem> allSongsList = new ArrayList<>();
    private final Map<String, ArrayList<SongItem>> folderMap = new LinkedHashMap<>();

    private final ArrayList<String> currentMp3Paths = new ArrayList<>();
    private final ArrayList<String> currentMp3Titles = new ArrayList<>();

    private final List<Button> playlistButtons = new ArrayList<>();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;
            if (!currentMp3Paths.isEmpty()) {
                musicService.setPlaylist(currentMp3Paths, currentMp3Titles);
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
        layoutPlaylists = findViewById(R.id.layoutPlaylists);
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
        allSongsList.clear();
        folderMap.clear();

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

                if (path != null && path.endsWith(".mp3") && path.toLowerCase().contains("zxcv25")) {
                    File file = new File(path);
                    String folderName = (file.getParentFile() != null) ? file.getParentFile().getName() : "zxcv25";
                    String songTitle = (title != null) ? title : "Unknown";

                    SongItem song = new SongItem(path, songTitle, folderName);
                    allSongsList.add(song);

                    if (!folderMap.containsKey(folderName)) {
                        folderMap.put(folderName, new ArrayList<>());
                    }
                    folderMap.get(folderName).add(song);
                }
            }
            cursor.close();
        }

        setupPlaylistBar();
    }

    private void setupPlaylistBar() {
        layoutPlaylists.removeAllViews();
        playlistButtons.clear();

        ArrayList<String> playlistNames = new ArrayList<>();

        int folderCount = folderMap.size();
        if (folderCount >= 2) {
            playlistNames.add("All");
        }
        playlistNames.addAll(folderMap.keySet());

        if (playlistNames.isEmpty()) {
            currentMp3Paths.clear();
            currentMp3Titles.clear();
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, currentMp3Titles);
            listViewSongs.setAdapter(adapter);
            return;
        }

        for (String pName : playlistNames) {
            Button btn = new Button(this);
            btn.setText(pName);
            btn.setAllCaps(false);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> selectPlaylist(pName));

            layoutPlaylists.addView(btn);
            playlistButtons.add(btn);
        }

        // Tự động chọn playlist đầu tiên
        selectPlaylist(playlistNames.get(0));
    }

    private void selectPlaylist(String playlistName) {
        currentMp3Paths.clear();
        currentMp3Titles.clear();

        ArrayList<SongItem> targetList;
        if ("All".equals(playlistName)) {
            targetList = allSongsList;
        } else {
            targetList = folderMap.get(playlistName);
        }

        if (targetList != null) {
            for (SongItem song : targetList) {
                currentMp3Paths.add(song.path);
                currentMp3Titles.add(song.title);
            }
        }

        // Cập nhật độ mờ nút playlist đang chọn
        for (Button btn : playlistButtons) {
            if (btn.getText().toString().equals(playlistName)) {
                btn.setAlpha(1.0f);
            } else {
                btn.setAlpha(0.4f);
            }
        }

        // Cập nhật danh sách bài hát hiển thị
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, currentMp3Titles);
        listViewSongs.setAdapter(adapter);

        // Cập nhật danh sách phát cho MusicService
        if (isBound) {
            musicService.setPlaylist(currentMp3Paths, currentMp3Titles);
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
