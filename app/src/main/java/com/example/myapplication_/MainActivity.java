package com.example.myapplication_;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // NavHostFragment를 통해 NavController 가져오기
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();

        // BottomNavigationView와 NavController 연결
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigationview);

        // AppBarConfiguration 설정 (탑 레벨 프래그먼트)
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.homeFragment, R.id.search, R.id.bookmark, R.id.setting)
                .build();

        // BottomNavigationView와 NavController를 연동
        NavigationUI.setupWithNavController(bottomNavigationView, navController);

        // BottomNavigationView의 선택 리스너 설정
        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                NavController navController = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment);

                // 아이템 ID에 따라 이동
                if (item.getItemId() == R.id.homeFragment) {
                    navController.navigate(R.id.homeFragment);
                    return true;
                } else if (item.getItemId() == R.id.search) {
                    navController.navigate(R.id.search);
                    return true;
                } else if (item.getItemId() == R.id.bookmark) {
                    navController.navigate(R.id.bookmark);
                    return true;
                } else if (item.getItemId() == R.id.setting) {
                    navController.navigate(R.id.setting);
                    return true;
                }
                else if(item.getItemId() == R.id.guideFragment){
                    navController.navigate(R.id.guideFragment);
                    return true;
                }

                return false;
            }
        });
    }
}