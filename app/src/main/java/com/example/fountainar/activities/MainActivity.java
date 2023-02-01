package com.example.fountainar.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fountainar.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupPlayButton();
    }

    private void setupPlayButton() {
        ImageButton playButton = findViewById(R.id.play_button);

        playButton.setOnClickListener(view -> {
            Intent intentMain = new Intent(getApplicationContext(),
                    DemographicQuestionnaire.class);
            startActivity(intentMain);
        });
    }
}
