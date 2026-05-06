package com.trevify.music;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.trevify.music.databinding.ActivitySplashBinding;

public class splashActivity extends AppCompatActivity {
    private static final long AUTO_CONTINUE_DELAY_MS = 1200L;

    private ActivitySplashBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isNavigating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySavedTheme();

        EdgeToEdge.enable(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        boolean onboarded = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("onboarded", false);

        setupSystemBars();
        playEntranceAnimation();

        if (onboarded) {
            binding.startBtn.setVisibility(View.GONE);
            binding.splashProgress.setVisibility(View.VISIBLE);
            handler.postDelayed(() -> openMain(false), AUTO_CONTINUE_DELAY_MS);
        } else {
            binding.startBtn.setVisibility(View.VISIBLE);
            binding.splashProgress.setVisibility(View.GONE);
            binding.startBtn.setOnClickListener(v -> openMain(true));
        }
    }

    private void applySavedTheme() {
        SharedPreferences themePrefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE);
        boolean isDarkMode = themePrefs.getBoolean("dark_mode", false);
        int requestedMode = isDarkMode
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;

        if (AppCompatDelegate.getDefaultNightMode() != requestedMode) {
            AppCompatDelegate.setDefaultNightMode(requestedMode);
        }
    }

    private void setupSystemBars() {
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), binding.main);
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.contentGroup.setPadding(
                    binding.contentGroup.getPaddingLeft(),
                    systemBars.top + getResources().getDimensionPixelSize(R.dimen.splash_top_padding),
                    binding.contentGroup.getPaddingRight(),
                    binding.contentGroup.getPaddingBottom()
            );
            binding.bottomActions.setPadding(
                    binding.bottomActions.getPaddingLeft(),
                    binding.bottomActions.getPaddingTop(),
                    binding.bottomActions.getPaddingRight(),
                    systemBars.bottom + getResources().getDimensionPixelSize(R.dimen.splash_bottom_padding)
            );
            return insets;
        });
    }

    private void playEntranceAnimation() {
        binding.contentGroup.setAlpha(0f);
        binding.contentGroup.setTranslationY(36f);
        binding.contentGroup.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(520L)
                .setStartDelay(120L)
                .start();

        binding.bottomActions.setAlpha(0f);
        binding.bottomActions.animate()
                .alpha(1f)
                .setDuration(420L)
                .setStartDelay(360L)
                .start();
    }

    private void openMain(boolean markOnboarded) {
        if (isNavigating) {
            return;
        }

        isNavigating = true;
        if (markOnboarded) {
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("onboarded", true)
                    .apply();
        }

        startActivity(new Intent(splashActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
