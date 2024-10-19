package com.example.cubeapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_TIME_OUT = 5000; // 3 segundos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Espera de 3 segundos antes de redirecionar para a MainActivity
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Redireciona para a MainActivity
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // Fecha a SplashActivity
            }
        }, SPLASH_TIME_OUT);
    }
}
