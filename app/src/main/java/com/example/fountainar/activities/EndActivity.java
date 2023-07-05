package com.example.fountainar.activities;

import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fountainar.R;
import com.example.fountainar.handlers.BackPressedHandler;

public class EndActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_end);
        setupExitButton();
        BackPressedHandler.setupBackPressedCallback(this);
    }

    /**
     * Sets up the exit button and defines its click behavior.
     * When the exit button is clicked, it closes the application.
     */
    private void setupExitButton() {
        ImageButton btn1 = findViewById(R.id.exit_button);

        btn1.setOnClickListener(v -> {
            finish();
            System.exit(0);
        });
    }
}
