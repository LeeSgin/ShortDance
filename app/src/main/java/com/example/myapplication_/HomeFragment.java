package com.example.myapplication_;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class HomeFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // createGuideButton을 찾아서 NavController로 이동하는 동작을 설정
        Button createGuideButton = view.findViewById(R.id.createGuideButton);
        createGuideButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // NavController를 통해 fragment_guide.xml로 이동
                NavController navController = Navigation.findNavController(v);
                navController.navigate(R.id.action_homeFragment_to_guideFragment);
            }
        });

        return view;
    }
}
