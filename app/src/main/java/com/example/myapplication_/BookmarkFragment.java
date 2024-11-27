package com.example.myapplication_;


import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;


import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link BookmarkFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BookmarkFragment extends Fragment {
    private static final String TAG = "BookmarkFragment";
    private PoseLandmarker poseLandmarker;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bookmark, container, false);

        // PoseLandmarker 초기화
        initializePoseLandmarker();

        // 카메라 설정
       // setupCameraX();

        return view;
    }


    private void initializePoseLandmarker() {
        try {
            // PoseLandmarkerOptions 객체를 설정하는 새로운 방법을 사용
            PoseLandmarker.PoseLandmarkerOptions  options = PoseLandmarker.PoseLandmarkerOptions .builder()
                    .setBaseOptions(BaseOptions.builder() // BaseOptions 사용
                            .setModelAssetPath("pose_landmark_model.tflite") // 모델 경로 설정
                            .build())
                    .build();

            // PoseLandmarker 초기화
            poseLandmarker = PoseLandmarker.createFromOptions(getContext(), options);

            Log.d(TAG, "PoseLandmarker 초기화 성공");

        } catch (Exception e) {
            Log.e(TAG, "PoseLandmarker 초기화 오류: ", e);
        }
    }
}