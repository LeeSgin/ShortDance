package com.example.myapplication_

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation.findNavController
import com.example.myapplication_.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "Pose Landmarker"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var recorder: Recorder = Recorder.Builder()
        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
        .build()
    private var videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(
        recorder!!
    )
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    private var videoView: VideoView? = null
    private var outputDirectory: File? = null
    private var countdownText: TextView? = null
    private var isRecording = false // 녹화 상태 변수 추가
    private lateinit var captureButton: ImageButton
    private var isCountdown = true

    private var currentRecording: Recording? = null // 현재 녹화 상태를 저장할 변수

    private val handler = Handler(Looper.getMainLooper()) // 메인 스레드의 핸들러

    private var baseVideoUri: Uri? = null
    private var videoUri: Uri? = null // 최초 영상 URI 저장

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
/*        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }*/

        // Start the PoseLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if(this::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            // Close the PoseLandmarkerHelper and release resources
            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        videoView = view.findViewById(R.id.video_view1)
        outputDirectory = getOutputDirectory()
        countdownText = view.findViewById(R.id.countdown_text) // 카운트다운 텍스트 뷰 초기화

/*        val previewView = view.findViewById<PreviewView>(R.id.preview_view)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE*/

        val videoUriString = if (arguments != null) requireArguments().getString("videoUri") else null
        //videoView?.setMediaController(MediaController(activity)) /* 비디오 컨트롤러 설정 */

        requestPermissionsIfNeeded()

        if (allPermissionsGranted()) {
            // Wait for the views to be properly laid out
            fragmentCameraBinding.viewFinder.post {
                // Set up the camera and its use cases
                setUpCamera()
            }

            // Create the PoseLandmarkerHelper that will handle the inference
            backgroundExecutor.execute {
                poseLandmarkerHelper = PoseLandmarkerHelper(
                    context = requireContext(),
                    runningMode = RunningMode.LIVE_STREAM,
                    minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                    minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                    minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                    currentDelegate = viewModel.currentDelegate,
                    poseLandmarkerHelperListener = this
                )
            }


            // ---------------------------미리 만들어 둔 비디오 재생하는 부분-------------------------------------
            val startFromTenSeconds =
                arguments != null && requireArguments().getBoolean("startFromTenSeconds", false)

            if (videoUriString != null) {
                baseVideoUri = Uri.parse(videoUriString)
                videoView?.setVideoURI(baseVideoUri)
                videoView?.setOnPreparedListener(MediaPlayer.OnPreparedListener { mediaPlayer: MediaPlayer? ->
                    if (startFromTenSeconds) {
                        videoView?.seekTo(14000) // 10초부터 재생
                    } else {
                        videoView?.seekTo(0) // 0초부터 재생
                    }
                    Log.d(
                        "VideoView",
                        "Current playback position after seek: " + videoView?.getCurrentPosition() + "ms"
                    )
                })
            } else {
                Toast.makeText(activity, "Invalid video source.", Toast.LENGTH_LONG).show()
            }


            captureButton = requireView().findViewById(R.id.capture_button)
            captureButton.setOnClickListener {
                Log.d("Recording", "Capture button clicked.") // 로그 추가
                //Toast.makeText(requireContext(), "Capture button clicked", Toast.LENGTH_SHORT).show()
                if (isRecording) {
                    stopRecording() // 이미 녹화 중이면 중지
                } else {
                    Log.d("Recording", "Starting recording.") // 로그 추가
                    startCountdownAndRecord()
                    //startRecording()
                }
            }
        } else {
            Toast.makeText(requireContext(), "Permissions not granted.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsIfNeeded() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setUpCamera()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                requireActivity().finish()
            }
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectPose(image)
                    }
                }

        recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer, videoCapture
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectPose(imageProxy: ImageProxy) {
        if(this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after pose have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onResults(
        resultBundle: PoseLandmarkerHelper.ResultBundle
    ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
/*                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)*/

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    private fun startRecording() {
        Log.d("Recording", "Inside startRecording()") // 디버그 메시지

        // 동영상 파일 경로 설정
        val videoFile = File(outputDirectory, System.currentTimeMillis().toString() + ".mp4")

        try {
            // 준비된 동영상 녹화 시작
            currentRecording = videoCapture.output.prepareRecording(
                requireContext(),
                FileOutputOptions.Builder(videoFile).build()
            )
                .withAudioEnabled()
                .start(
                    ContextCompat.getMainExecutor(requireContext())
                ) { videoRecordEvent: VideoRecordEvent? ->
                    if (videoRecordEvent is VideoRecordEvent.Start) {
                        isRecording = true // 녹화 시작 상태로 설정
                        videoView?.start() // 비디오 재생 시작
                        Log.d("Recording", "Recording started.") // 디버그 메시지
                        Toast.makeText(context, "Recording started.", Toast.LENGTH_SHORT).show()
                        videoUri = Uri.fromFile(videoFile) // 비디오 URI 저장

                    } else if (videoRecordEvent is VideoRecordEvent.Finalize) {
                        isRecording = false // 녹화 중지 상태로 설정
                        Log.d("Recording", "Recording finalized.") // 디버그 메시지

                        // 여기서 두 영상을 사용할 수 있도록 Bundle로 전달
                        val bundle = Bundle()
                        if (videoUri != null) {
                            bundle.putString("videoFilePath", videoUri.toString())
                        } else {
                            Log.e("CameraFragment", "videoUri is null. Cannot navigate.")
                            return@start // 강제 종료 방지
                        }


                        // 다음 프래그먼트로 전환
                        val navController = findNavController(requireActivity(), R.id.nav_host_fragment)
                        navController.navigate(
                            R.id.action_cameraFragment_to_guide_result_Fragment,
                            bundle
                        ) // 다음 화면으로 이동하며 Bundle 전달

                        // 타이머 취소
                        //handler.removeCallbacks(redOverlayRunnable)
                    }
                }
        } catch (e: SecurityException) {
            Log.e("Recording", "Permission denied: " + e.message)
            Toast.makeText(
                context,
                "Camera and audio permissions are required to record video.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun stopRecording() {
        // 녹화 중지
        if (currentRecording != null && isRecording) {
            currentRecording?.stop() // 녹화 중지
            isRecording = false // 녹화 상태 업데이트
            Toast.makeText(context, "Recording stopped.", Toast.LENGTH_SHORT).show()
            Log.d("Recording", "Recording stopped.") // 디버그 메시지
            currentRecording = null // 현재 녹화 상태 초기화

            // 타이머 취소
            //handler.removeCallbacks(redOverlayRunnable)
        }
    }

    //재생 전 카운트다운
    private fun startCountdownAndRecord() {
        countdownText?.visibility = View.VISIBLE // 카운트다운 텍스트 뷰 보이기
        countdownText?.text = "3"

        if (!isCountdown) { startRecording() }

        handler.postDelayed(object : Runnable {
            var countdown: Int = 2 // 다음 카운트다운 숫자

            override fun run() {
                countdownText?.text = countdown.toString() // 카운트다운 텍스트 업데이트
                countdown--

                if (countdown >= 0) {
                    handler.postDelayed(this, 1000) // 1초 후에 다시 실행
                } else {
                    countdownText?.visibility = View.GONE // 카운트다운 끝나면 숨김
                    Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                    isCountdown = false
                    startRecording() // 카운트다운 후 녹화 시작
                    //videoView?.start() // 비디오 재생 시작
                    Log.d("VideoView", "Video playback started.")
                }
            }
        }, 1000) // 첫 번째 카운트다운 후 1초 후 실행
    }

    private fun getOutputDirectory(): File? {
        var mediaDir = requireContext().getExternalFilesDir(null) // 앱의 전용 저장소 사용
        if (mediaDir != null && !mediaDir.mkdirs()) {
            // 폴더가 이미 존재하는 경우 또는 생성 실패 시
            mediaDir = File(mediaDir.path)
        }
        return mediaDir
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
/*            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU, false
                )
            }*/
        }
    }
}