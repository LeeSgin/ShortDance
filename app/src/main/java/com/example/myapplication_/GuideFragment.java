package com.example.myapplication_;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link GuideFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class GuideFragment extends Fragment {

    private EditText inputField;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_guide, container, false);

        // EditText 및 버튼 초기화
        inputField = view.findViewById(R.id.input_field);
        ImageButton backButton = view.findViewById(R.id.back_button);
        Button nextButton = view.findViewById(R.id.next_button);
        Button pasteButton = view.findViewById(R.id.paste_button);

        // 뒤로가기 버튼 클릭 리스너 설정
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
                navController.popBackStack();  // 이전 화면으로 돌아가기
            }
        });

//        nextButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                // EditText에서 링크 가져오기
//                String videoLink = inputField.getText().toString();
//
//                // NavController를 사용하여 프래그먼트 간 이동
//                Bundle bundle = new Bundle();
//                bundle.putString("videoUri", videoLink);  // 입력한 링크를 Bundle에 추가
//
//                NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
//                navController.navigate(R.id.action_guideFragment_to_gudieRecordingFragment, bundle);  // 전달한 데이터와 함께 이동
//            }
//        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 기본 비디오 URI 설정 (로컬 리소스 또는 HTTP)
                String defaultVideoUri = "android.resource://" + getActivity().getPackageName() + "/" + R.raw.ex;  // 로컬 리소스
                // 또는 기본 HTTP 영상 URL 사용
                // String defaultVideoUri = "http://example.com/sample.mp4";

                Bundle bundle = new Bundle();
                bundle.putString("videoUri", defaultVideoUri);
                bundle.putBoolean("startFromTenSeconds", false);

                NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
                navController.navigate(R.id.action_guideFragment_to_gudieRecordingFragment, bundle);
            }
        });

        pasteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String defaultVideoUri = "android.resource://" + getActivity().getPackageName() + "/" + R.raw.ex;  // 로컬 리소스
                // 또는 기본 HTTP 영상 URL 사용
                // String defaultVideoUri = "http://example.com/sample.mp4";

                Bundle bundle = new Bundle();
                bundle.putString("videoUri", defaultVideoUri);
                bundle.putBoolean("startFromTenSeconds", false);

                NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
                navController.navigate(R.id.action_guideFragment_to_cameraFragment, bundle);
            }
        });

        return view;
    }
}
