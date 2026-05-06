package com.trevify.music;

import android.os.Bundle;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.trevify.music.databinding.ActivityAboutBinding;

public class AboutActivity extends AppCompatActivity {
    private ActivityAboutBinding binding;
    private int backButtonTopMargin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        backButtonTopMargin = ((ViewGroup.MarginLayoutParams) binding.backBtn.getLayoutParams()).topMargin;

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            updateBackButtonTopInset(systemBars.top);
            return insets;
        });

        binding.backBtn.setOnClickListener(v -> finish());
    }

    private void updateBackButtonTopInset(int topInset) {
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) binding.backBtn.getLayoutParams();
        int targetTopMargin = backButtonTopMargin + topInset;
        if (params.topMargin != targetTopMargin) {
            params.topMargin = targetTopMargin;
            binding.backBtn.setLayoutParams(params);
        }
    }
}
