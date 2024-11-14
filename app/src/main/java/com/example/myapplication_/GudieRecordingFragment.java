package com.example.myapplication_;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.camera.core.Preview;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.camera.core.CameraSelector;
import androidx.camera.video.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;


public class GudieRecordingFragment extends Fragment {

    private static final int REQUEST_PERMISSIONS_CODE = 10;
    private VideoView videoView;
    private ExecutorService cameraExecutor;
    private File outputDirectory;

    private boolean isRecording = false; // 녹화 상태 변수 추가

    private Recording currentRecording; // 현재 녹화 상태를 저장할 변수

    private Handler handler = new Handler(Looper.getMainLooper());  // 메인 스레드의 핸들러
    private Runnable redOverlayRunnable;

    private Uri baseVideoUri;
    private Uri firstVideoUri; // 최초 영상 URI 저장
    private Uri secondVideoUri; // 새로 찍은 영상 URI 저장

    private boolean mistake = true; // 실수 했을 경우
    ImageButton captureButton;

    private TextView countdownText; // 카운트다운 텍스트 뷰

    // VideoOutput 생성
    Recorder recorder = new Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build();
    VideoCapture<Recorder> videoCapture = VideoCapture.withOutput(recorder);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gudie_recording, container, false);

        videoView = view.findViewById(R.id.video_view);
        outputDirectory = getOutputDirectory();
        cameraExecutor = Executors.newSingleThreadExecutor();
        countdownText = view.findViewById(R.id.countdown_text); // 카운트다운 텍스트 뷰 초기화

        PreviewView previewView = view.findViewById(R.id.preview_view);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

        ImageButton backButton = view.findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
            navController.popBackStack();  // 이전 화면으로 돌아가기
        });

        String videoUriString = getArguments() != null ? getArguments().getString("videoUri") : null;
        videoView.setMediaController(new MediaController(getActivity())); // 비디오 컨트롤러 설정


        View redOverlay = view.findViewById(R.id.red_overlay);  // 빨간색 효과를 위한 오버레이 뷰
        redOverlay.setVisibility(View.GONE);  // 초기에는 숨김

        Bundle arguments = getArguments();

        // ---------------------------미리 만들어 둔 비디오 재생하는 부분-------------------------------------
        boolean startFromTenSeconds = arguments != null && arguments.getBoolean("startFromTenSeconds", false);

        if (videoUriString != null) {
            baseVideoUri = Uri.parse(videoUriString);
            videoView.setVideoURI(baseVideoUri);
            videoView.setOnPreparedListener(mediaPlayer -> {
                if (startFromTenSeconds) {
                    videoView.seekTo(14000); // 10초부터 재생
                } else {
                    videoView.seekTo(0); // 0초부터 재생
                }
                Log.d("VideoView", "Current playback position after seek: " + videoView.getCurrentPosition() + "ms");
//                videoView.pause();
                //videoView.setZOrderOnTop(true);
            });
        } else {
            Toast.makeText(getActivity(), "Invalid video source.", Toast.LENGTH_LONG).show();
        }
        //------------------------------- 미리 만들어 둔 비디오 재생하는 부분-------------------------------------

        captureButton = view.findViewById(R.id.capture_button);
        captureButton.setOnClickListener(v -> {
            Log.d("Recording", "Capture button clicked."); // 로그 추가
            checkPermissionsAndStartRecording(); // 권한 확인 후 녹화 시작
        });

        startCamera();  // 카메라 시작

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }

    private File getOutputDirectory() {
        File mediaDir = getContext().getExternalFilesDir(null); // 앱의 전용 저장소 사용
        if (mediaDir != null && !mediaDir.mkdirs()) {
            // 폴더가 이미 존재하는 경우 또는 생성 실패 시
            mediaDir = new File(mediaDir.getPath());
        }
        return mediaDir;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                PreviewView previewView = getView().findViewById(R.id.preview_view);
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);

            } catch (Exception e) {
                Log.e("CameraX", "Camera initialization failed.", e);
            }
        }, ContextCompat.getMainExecutor(getContext()));
    }

    private boolean checkPermissions() {
        boolean cameraPermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean audioPermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        Log.d("Recording", "Camera permission: " + cameraPermission);
        Log.d("Recording", "Audio permission: " + audioPermission);

        return cameraPermission && audioPermission;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(getActivity(), new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        }, REQUEST_PERMISSIONS_CODE);
    }

    private void checkPermissionsAndStartRecording() {
        Log.d("Recording", "Checking permissions and starting recording."); // 로그 추가
        if (checkPermissions()) {
            if (isRecording) {
                stopRecording(); // 이미 녹화 중이면 중지
            } else {
                Log.d("Recording", "Starting recording."); // 로그 추가
//                startRecording(); // 권한이 있을 경우 바로 녹화 시작
                startCountdownAndRecord();
            }
        } else {
            requestPermissions(); // 권한이 없을 경우 요청
        }
    }

    // 권한 요청 결과 처리
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            boolean allPermissionsGranted = true;

            // 모든 권한이 승인되었는지 확인
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                startRecording();  // 모든 권한이 승인된 후 녹화 시작
                Log.d("Permissions", "All permissions granted."); // 권한 허용 로그
            } else {
                Toast.makeText(getContext(), "Please enable all permissions in app settings.", Toast.LENGTH_SHORT).show();
                Log.d("Permissions", "Not all permissions granted.");
            }
        }
    }

    private void startRecording() {
        Log.d("Recording", "Inside startRecording()"); // 디버그 메시지

        // 동영상 파일 경로 설정
        File videoFile = new File(outputDirectory, System.currentTimeMillis() + ".mp4");

        try {
            // 준비된 동영상 녹화 시작
            currentRecording = videoCapture.getOutput().prepareRecording(getContext(), new FileOutputOptions.Builder(videoFile).build())
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(getContext()), videoRecordEvent -> {
                        if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                            isRecording = true; // 녹화 시작 상태로 설정
                            videoView.start(); // 비디오 재생 시작
                            Log.d("Recording", "Recording started."); // 디버그 메시지
                            Toast.makeText(getContext(), "Recording started.", Toast.LENGTH_SHORT).show();


                            // 12초 후 빨간색 오버레이를 보여주기 위한 타이머 설정
                            if (mistake) {
                                redOverlayRunnable = () -> {
                                    View redOverlay = getView().findViewById(R.id.red_overlay);
                                    redOverlay.setVisibility(View.VISIBLE);
                                    ObjectAnimator fadeInAnimator = ObjectAnimator.ofFloat(redOverlay, "alpha", 0f, 1f);
                                    fadeInAnimator.setDuration(1000);
                                    fadeInAnimator.start();

                                    //Toast.makeText(getContext(), "12초 후 빨간색 표시", Toast.LENGTH_LONG).show();

                                    // 1초 후 오버레이 사라지기
                                    handler.postDelayed(() -> {
                                        ObjectAnimator fadeOutAnimator = ObjectAnimator.ofFloat(redOverlay, "alpha", 1f, 0f);
                                        fadeOutAnimator.setDuration(1000);
                                        fadeOutAnimator.start();
                                        stopRecording();
                                        startSecondRecording(); // 빨간색 오버레이 후 새로운 녹화 시작
                                    }, 1000);
                                };
                                handler.postDelayed(redOverlayRunnable, 12000);
                            } // 10초 후 실행
                        } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                            isRecording = false; // 녹화 중지 상태로 설정
                            Log.d("Recording", "Recording finalized."); // 디버그 메시지

                            // 타이머 취소
                            handler.removeCallbacks(redOverlayRunnable);
                        }
                    });
        } catch (SecurityException e) {
            Log.e("Recording", "Permission denied: " + e.getMessage());
            Toast.makeText(getContext(), "Camera and audio permissions are required to record video.", Toast.LENGTH_SHORT).show();
        }
    }
    //두번째 영상 기록(틀렸을 경우)
    private void startSecondRecording() {
        Log.d("Recording", "Inside startSecondRecording()"); // 디버그 메시지

        // 새로운 비디오 파일 경로 설정
        File secondVideoFile = new File(outputDirectory, System.currentTimeMillis() + "_second.mp4");

        try {
            currentRecording = videoCapture.getOutput().prepareRecording(getContext(), new FileOutputOptions.Builder(secondVideoFile).build())
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(getContext()), videoRecordEvent -> {
                        if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                            isRecording = true; // 새 녹화 시작
                            Log.d("Recording", "Second recording started."); // 디버그 메시지
                            secondVideoUri = Uri.fromFile(secondVideoFile); // 두 번째 비디오 URI 저장
                        } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                            // 최종화 단계에서 처리를 합니다.
                            isRecording = false; // 녹화 중지 상태로 설정
                            Log.d("Recording", "Second recording finalized."); // 디버그 메시지

                            // 여기서 두 영상을 사용할 수 있도록 Bundle로 전달
                            Bundle bundle = new Bundle();
                            bundle.putString("videoUri", baseVideoUri.toString());
                            bundle.putString("secondVideoUri", secondVideoUri.toString());

                            // 다음 프래그먼트로 전환
                            NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
                            navController.navigate(R.id.action_gudieRecordingFragment_to_guideFeedbackFragment, bundle);  // 다음 화면으로 이동하며 Bundle 전달
                        }
                    });
        } catch (SecurityException e) {
            Log.e("Recording", "Permission denied: " + e.getMessage());
            Toast.makeText(getContext(), "Camera and audio permissions are required to record video.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        // 녹화 중지
        if (currentRecording != null && isRecording) {
            currentRecording.stop(); // 녹화 중지
            isRecording = false; // 녹화 상태 업데이트
            Toast.makeText(getContext(), "Recording stopped.", Toast.LENGTH_SHORT).show();
            Log.d("Recording", "Recording stopped."); // 디버그 메시지
            currentRecording = null; // 현재 녹화 상태 초기화

            // 타이머 취소
            handler.removeCallbacks(redOverlayRunnable);
        }
    }

    //재생 전 카운트다운
    private void startCountdownAndRecord() {
        countdownText.setVisibility(View.VISIBLE); // 카운트다운 텍스트 뷰 보이기
        countdownText.setText("3"); // 카운트다운 시작 숫자

        handler.postDelayed(new Runnable() {
            int countdown = 2; // 다음 카운트다운 숫자

            @Override
            public void run() {
                countdownText.setText(String.valueOf(countdown)); // 카운트다운 텍스트 업데이트
                countdown--;

                if (countdown >= 0) {
                    handler.postDelayed(this, 1000); // 1초 후에 다시 실행
                } else {
                    countdownText.setVisibility(View.GONE); // 카운트다운 끝나면 숨김
                    startRecording(); // 카운트다운 후 녹화 시작
                    videoView.start(); // 비디오 재생 시작
                    Log.d("VideoView", "Video playback started.");
                }
            }
        }, 1000); // 첫 번째 카운트다운 후 1초 후 실행
    }
}
