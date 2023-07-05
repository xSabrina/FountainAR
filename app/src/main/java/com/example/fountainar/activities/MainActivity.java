package com.example.fountainar.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fountainar.R;
import com.example.fountainar.handlers.BackPressedHandler;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupPlayButton();
        BackPressedHandler.setupBackPressedCallback(this);
    }

    /**
     * Sets up the play button and defines its click behavior.
     * When the play button is clicked, it starts the DemographicQuestionnaire activity.
     */
    private void setupPlayButton() {
        ImageButton playButton = findViewById(R.id.play_button);

        playButton.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), DemographicQuestionnaire.class);
            startActivity(intent);
        });

    }
}
