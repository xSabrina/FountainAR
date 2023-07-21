package com.example.fountainar.activities;

import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fountainar.R;
import com.example.fountainar.handlers.BackPressedHandler;

/**
 * Activity to display the end screen of the FountainAR app and handling closing the entire app
 * through an exit button.
 */

public class EndActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_end);
        setupExitButton();
        BackPressedHandler.setupBackPressedCallback(this);
    }

    /**
     * Sets up the exit button and to close the application on click.
     */
    private void setupExitButton() {
        ImageButton finishButton = findViewById(R.id.exit_button);

        finishButton.setOnClickListener(v -> finishAffinity());
    }
}
