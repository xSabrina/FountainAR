package com.example.fountainar.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fountainar.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;

public class TAQuestionnaire extends AppCompatActivity {

    private static final String TAG = DemographicQuestionnaire.class.getSimpleName();

    private final ArrayList<String> questions = new ArrayList<>();
    private final ArrayList<String> tamTags = new ArrayList<>();
    private final ArrayList<String> answers = new ArrayList<>();

    private final ArrayList<RadioGroup> radioGroups = new ArrayList<>();

    private final ArrayList<RadioGroup> missingRadioGroups = new ArrayList<>();

    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tech_acc_questionnaire);
        startTime = System.currentTimeMillis();

        setupRadioGroups();
        setupFinishButton();
    }

    private void setupRadioGroups() {
        addPURadioGroups();
        addPEOURadioGroups();
        addPERadioGroups();
        addBIRadioGroups();
    }

    private void addPURadioGroups() {
        RadioGroup radioGroup_q1 = findViewById(R.id.pu1);
        RadioGroup radioGroup_q2 = findViewById(R.id.pu2);
        RadioGroup radioGroup_q3 = findViewById(R.id.pu3);
        RadioGroup radioGroup_q4 = findViewById(R.id.pu4);

        radioGroups.add(radioGroup_q1);
        radioGroups.add(radioGroup_q2);
        radioGroups.add(radioGroup_q3);
        radioGroups.add(radioGroup_q4);
    }

    private void addPEOURadioGroups() {
        RadioGroup radioGroup_q5 = findViewById(R.id.peou1);
        RadioGroup radioGroup_q6 = findViewById(R.id.peou2);
        RadioGroup radioGroup_q7 = findViewById(R.id.peou3);
        RadioGroup radioGroup_q8 = findViewById(R.id.peou4);

        radioGroups.add(radioGroup_q5);
        radioGroups.add(radioGroup_q6);
        radioGroups.add(radioGroup_q7);
        radioGroups.add(radioGroup_q8);
    }

    private void addPERadioGroups() {
        RadioGroup radioGroup_q9 = findViewById(R.id.pe1);
        RadioGroup radioGroup_q10 = findViewById(R.id.pe2);
        RadioGroup radioGroup_q11 = findViewById(R.id.pe3);
        RadioGroup radioGroup_q12 = findViewById(R.id.pe4);

        radioGroups.add(radioGroup_q9);
        radioGroups.add(radioGroup_q10);
        radioGroups.add(radioGroup_q11);
        radioGroups.add(radioGroup_q12);
    }

    private void addBIRadioGroups() {
        RadioGroup radioGroup_q13 = findViewById(R.id.bi1);
        RadioGroup radioGroup_q14 = findViewById(R.id.bi2);
        RadioGroup radioGroup_q15 = findViewById(R.id.bi3);
        RadioGroup radioGroup_q16 = findViewById(R.id.bi4);
        RadioGroup radioGroup_q17 = findViewById(R.id.bi5);
        RadioGroup radioGroup_q18 = findViewById(R.id.bi6);
        RadioGroup radioGroup_q19 = findViewById(R.id.bi7);
        RadioGroup radioGroup_q20 = findViewById(R.id.bi8);


        radioGroups.add(radioGroup_q13);
        radioGroups.add(radioGroup_q14);
        radioGroups.add(radioGroup_q15);
        radioGroups.add(radioGroup_q16);
        radioGroups.add(radioGroup_q17);
        radioGroups.add(radioGroup_q18);
        radioGroups.add(radioGroup_q19);
        radioGroups.add(radioGroup_q20);
    }

    private void setupFinishButton() {
        Button finishButton = findViewById(R.id.tam_finished_button);

        finishButton.setOnClickListener(view -> {
            if (everyRadioGroupFinished()) {
                getQuestionsAndAnswers();
                saveAnswersToFile();

                Intent intentMain = new Intent(getApplicationContext(),
                        EndActivity.class);
                startActivity(intentMain);

            } else {
                highlightMissingFields();

                Toast.makeText(getApplicationContext(), R.string.fill_out_first, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private boolean everyRadioGroupFinished() {
        missingRadioGroups.clear();

        for (int i = 0; i < radioGroups.size(); i++) {
            if (radioGroups.get(i).getCheckedRadioButtonId() == -1) {
                missingRadioGroups.add(radioGroups.get(i));
            }
        }

        return missingRadioGroups.size() == 0;
    }

    private void getQuestionsAndAnswers() {
        LinearLayout linearLayout = findViewById(R.id.tam_layout);

        for (int i = 0; i < linearLayout.getChildCount(); i++) {
            View view = linearLayout.getChildAt(i + 1);

            if (view instanceof TextView && !(view instanceof EditText) &&
                    !(view instanceof Button)) {
                questions.add(((TextView) view).getText().toString() + " ");
            }
        }

        for (int i = 0; i < radioGroups.size(); i++) {
            String tam_tag = getResources().getResourceName(radioGroups.get(i).getId());
            tamTags.add(tam_tag.substring(26));

            for (int j = 0; j < radioGroups.get(i).getChildCount(); j++) {
                RadioButton radioButton = (RadioButton) radioGroups.get(i).getChildAt(j);
                if (radioButton.isChecked()) {
                    answers.add(String.valueOf(j + 1));
                }
            }
        }
    }

    private void saveAnswersToFile() {
        Date date = new Date();
        String dateString = date.toString();
        String timeSpent = String.valueOf(R.string.time_spent_questionnaire +
                ((System.currentTimeMillis() - startTime) / 1000));

        File file = createdFile();

        try {
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fOut);

            try {
                osw.write(DemographicQuestionnaire.probNum + "\n\n" + dateString + "\n" +
                        timeSpent + "\n\n");

                for (int i = 0; i < questions.size(); i++) {
                    osw.write(questions.get(i));
                    osw.write("\n");
                    osw.write(tamTags.get(i));
                    osw.write("\n");
                    osw.write(answers.get(i));
                    osw.write("\n\n");
                }

                osw.flush();
                osw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private File createdFile() {
        File rootDirectory = new File(this.getApplicationContext().getFilesDir(),
                "/Study_Data");
        File directory = new File(rootDirectory.getPath(),
                "/TAM_Questionnaires");
        if (!directory.exists()) {
            boolean wasSuccessful = directory.mkdirs();
            if (!wasSuccessful) {
                Log.e(TAG, "Creating directory for TAM questionnaires was not successful");
            }
        }

        return new File(directory, "TAM_" + DemographicQuestionnaire.probNum + ".txt");
    }

    private void highlightMissingFields() {
        ScrollView scrollView = findViewById(R.id.tam_scroll_view);
        ViewGroup containingLayout = (ViewGroup) missingRadioGroups.get(0).getParent();

        resetBackgrounds();
        scrollView.scrollTo(0, (int) containingLayout.getY() -
                scrollView.getHeight() / 3);

        for (int i = 0; i < missingRadioGroups.size(); i++) {
            missingRadioGroups.get(i).setBackgroundColor(getColor(R.color.red_light));

            for (int j = 0; j < missingRadioGroups.get(i).getChildCount(); j++) {
                missingRadioGroups.get(i).getChildAt(j)
                        .setBackgroundColor(getColor(R.color.red_light));
            }
        }

    }

    private void resetBackgrounds() {
        for (int i = 0; i < radioGroups.size(); i++) {
            radioGroups.get(i).setBackgroundColor(getColor(R.color.white));
            for (int j = 0; j < radioGroups.get(i).getChildCount(); j++) {
                radioGroups.get(i).getChildAt(j)
                        .setBackgroundColor(getColor(R.color.white));
            }
        }
    }
}
