package com.example.fountainar.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fountainar.R;

public class EndActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_end);

        setupExitButtion();
    }

    private void setupExitButtion() {
        ImageButton btn1 = findViewById(R.id.exit_button);

        btn1.setOnClickListener(v -> {
            finish();
            System.exit(0);
        });
    }

}
