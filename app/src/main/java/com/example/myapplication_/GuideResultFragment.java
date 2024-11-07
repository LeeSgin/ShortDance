package com.example.myapplication_;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class GuideResultFragment extends Fragment {

    private VideoView preView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_guide_result, container, false);

        preView = view.findViewById(R.id.user_preview);
        preView.setMediaController(new MediaController(getActivity()));

        // 전달된 파일 경로 가져오기
        String videoFilePath = getArguments() != null ? getArguments().getString("videoFilePath") : null;

        if (videoFilePath != null) {
            // 동영상 파일을 VideoView에 설정하여 프리뷰로 보여줌
            preView.setVideoURI(Uri.parse(videoFilePath));
            preView.setOnPreparedListener(mediaPlayer -> {
                preView.start();
            });
        } else {
            Toast.makeText(getActivity(), "Invalid video source.", Toast.LENGTH_LONG).show();
        }

        Button saveButton = view.findViewById(R.id.save_button);
        saveButton.setOnClickListener(v -> {
        if (videoFilePath != null) {
            saveVideoToGallery(videoFilePath);
        } else {
            Toast.makeText(getContext(), "Video file path is invalid.", Toast.LENGTH_SHORT).show();
        }});

        return view;
    }

    private void saveVideoToGallery(String videoFilePath) {
        File videoFile = new File(videoFilePath);
        if (!videoFile.exists()) {
            Toast.makeText(getContext(), "Video file not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri videoUri;
            OutputStream out;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 이상 - MediaStore 사용
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.getName());
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

                videoUri = getContext().getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (videoUri == null) return;

                out = getContext().getContentResolver().openOutputStream(videoUri);
            } else {
                // Android 9 이하 - 외부 저장소로 파일 이동
                File destFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), videoFile.getName());
                videoUri = Uri.fromFile(destFile);
                out = new FileOutputStream(destFile);
            }

            // 파일 복사
            FileInputStream in = new FileInputStream(videoFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            in.close();
            out.close();

            // 미디어 스캔
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                getContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, videoUri));
            }

            Toast.makeText(getContext(), "Video saved to gallery.", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to save video to gallery.", Toast.LENGTH_SHORT).show();
        }
    }
}