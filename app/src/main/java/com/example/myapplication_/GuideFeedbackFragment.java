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
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class GuideFeedbackFragment extends Fragment {

    private VideoView preView;
    private VideoView baseVideoView;

    final int timeForDelay = 1000; // 틀린부분을 다시 연습하기위한 대기시간

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_guide_feedback, container, false);

        preView = view.findViewById(R.id.user_preview);
        preView.setMediaController(new MediaController(getActivity()));
        baseVideoView = view.findViewById(R.id.base_preview);
        baseVideoView.setMediaController(new MediaController(getActivity()));

        // 전달된 파일 경로 가져오기
        String mistakePartFilePath = getArguments() != null ? getArguments().getString("secondVideoUri") : null;
        String baseVideo = getArguments() != null ? getArguments().getString("videoUri") : null;

        if (mistakePartFilePath != null) {
            // 동영상 파일을 VideoView에 설정하여 프리뷰로 보여줌
            preView.setVideoURI(Uri.parse(mistakePartFilePath));
            preView.setOnPreparedListener(mediaPlayer -> {
                preView.start();
            });
        } else {
            Toast.makeText(getActivity(), "Invalid video source.", Toast.LENGTH_LONG).show();
        }
        if (baseVideo != null) {
            // 동영상 파일을 VideoView에 설정하여 프리뷰로 보여줌
            baseVideoView.setVideoURI(Uri.parse(baseVideo));
            baseVideoView.setOnPreparedListener(mediaPlayer -> {
                baseVideoView.seekTo(14000); // 사용자가 틀린 시점 = 10초 뒤
                baseVideoView.start();
            });
        } else {
            Toast.makeText(getActivity(), "Invalid video source.", Toast.LENGTH_LONG).show();
        }

        Button restartButton = view.findViewById(R.id.restart_button);
        restartButton.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
            navController.navigate(R.id.action_guideFeedbackFragment_to_cameraFragment);
        });
        Button rerepracticeButton = view.findViewById(R.id.repractice_button);
        rerepracticeButton.setOnClickListener(v -> {

            String defaultVideoUri = "android.resource://" + getActivity().getPackageName() + "/" + R.raw.ex; // 로컬 리소스 URI 설정
            Bundle bundle = new Bundle();
            bundle.putString("videoUri", defaultVideoUri); // 비디오 URI를 Bundle에 추가
            bundle.putBoolean("startFromTenSeconds", true); // 10초에서 시작

            NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
            navController.navigate(R.id.action_guideFeedbackFragment_to_cameraFragment, bundle); // 프래그먼트 이동
        });

        return view;
    }
}