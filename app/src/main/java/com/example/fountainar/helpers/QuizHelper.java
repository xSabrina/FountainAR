package com.example.fountainar.helpers;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.fountainar.R;
import com.example.fountainar.activities.DemographicQuestionnaire;
import com.example.fountainar.activities.TAMQuestionnaire;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;

public class QuizHelper {
    private static final String TAG = QuizHelper.class.getSimpleName();
    private final Activity activity;
    private final long startTime;
    private final ArrayList<String> questions = new ArrayList<>();
    private final ArrayList<String> answers = new ArrayList<>();
    private final ArrayList<RadioGroup> radioGroups = new ArrayList<>();
    private final ArrayList<String> timeSpent = new ArrayList<>();
    private long quizStart;

    public QuizHelper(Activity activity) {
        this.activity = activity;
        startTime = System.currentTimeMillis();
    }

    /**
     * Sets up the quiz related to provided AR content.
     */
    public void setupQuiz() {
        Button startButton = activity.findViewById(R.id.ar_button_start);
        Button task1Button = activity.findViewById(R.id.ar_button_task1);
        Button task2Button = activity.findViewById(R.id.ar_button_task2);
        Button task3Button = activity.findViewById(R.id.ar_button_task3);

        RelativeLayout instructionsLayout = activity.findViewById(R.id.ar_layout_instructions);
        RelativeLayout task1Layout = activity.findViewById(R.id.ar_layout_task1);
        RelativeLayout task2Layout = activity.findViewById(R.id.ar_layout_task2);
        RelativeLayout task3Layout = activity.findViewById(R.id.ar_layout_task3);

        activity.runOnUiThread(() -> {
            TextView title = activity.findViewById(R.id.ar_layout_instructions_text);
            title.setText(R.string.ar_quiz_instructions);
            startButton.setVisibility(View.VISIBLE);
        });

        startButton.setOnClickListener(view -> {
            instructionsLayout.setVisibility(View.GONE);
            task1Layout.setVisibility(View.VISIBLE);
            quizStart = (System.currentTimeMillis() - startTime) / 1000;
        });

        task1Button.setOnClickListener(view -> {
            RadioGroup radioGroup = activity.findViewById(R.id.ar_rg_task1);
            continueQuiz(radioGroup, task1Layout, task2Layout);
        });

        task2Button.setOnClickListener(view -> {
            RadioGroup radioGroup = activity.findViewById(R.id.ar_rg_task2);
            continueQuiz(radioGroup, task2Layout, task3Layout);
        });

        task3Button.setOnClickListener(view -> {
            RadioGroup radioGroup = activity.findViewById(R.id.ar_rg_task3);
            if (radioGroup.getCheckedRadioButtonId() == -1) {
                Toast.makeText(activity.getApplicationContext(), R.string.ar_missing_answer,
                        Toast.LENGTH_SHORT).show();
            } else {
                getQuestionsAndAnswers();
                saveAnswersToFile();
                Intent intentMain = new Intent(activity.getApplicationContext(),
                        TAMQuestionnaire.class);
                activity.startActivity(intentMain);
            }
        });
    }

    /**
     * Continues quiz, if current question is answered yet.
     *
     * @param rg            RadioGroup of multiple choice options
     * @param currentLayout Layout of the current quiz page.
     * @param nextLayout    Layout of the next quiz page.
     */
    private void continueQuiz(RadioGroup rg, RelativeLayout currentLayout,
                              RelativeLayout nextLayout) {
        if (rg.getCheckedRadioButtonId() == -1) {
            Toast.makeText(activity.getApplicationContext(), R.string.ar_missing_answer,
                    Toast.LENGTH_SHORT).show();
        } else {
            saveTime();
            currentLayout.setVisibility(View.GONE);
            nextLayout.setVisibility(View.VISIBLE);
        }
    }

    private void saveTime() {
        long time = (System.currentTimeMillis() - startTime) / 1000 - quizStart;
        String taskDone = activity.getString(R.string.time_spent_task) + " " + time;
        timeSpent.add(taskDone);
    }

    private void getQuestionsAndAnswers() {
        View view1 = activity.findViewById(R.id.ar_tv_task1);
        questions.add(((TextView) view1).getText().toString() + " ");
        View view2 = activity.findViewById(R.id.ar_tv_task2);
        questions.add(((TextView) view2).getText().toString() + " ");
        View view3 = activity.findViewById(R.id.ar_tv_task3);
        questions.add(((TextView) view3).getText().toString() + " ");

        saveTime();
        setupRadioGroups();

        for (int i = 0; i < radioGroups.size(); i++) {
            int checkedId = radioGroups.get(i).getCheckedRadioButtonId();
            RadioButton radioButton = activity.findViewById(checkedId);
            answers.add(radioButton.getText().toString());
        }
    }

    private void setupRadioGroups() {
        RadioGroup radioGroup_q3 = activity.findViewById(R.id.ar_rg_task1);
        RadioGroup radioGroup_q4 = activity.findViewById(R.id.ar_rg_task2);
        RadioGroup radioGroup_q5 = activity.findViewById(R.id.ar_rg_task3);

        radioGroups.add(radioGroup_q3);
        radioGroups.add(radioGroup_q4);
        radioGroups.add(radioGroup_q5);
    }

    private void saveAnswersToFile() {
        Date date = new Date();
        String dateString = date.toString();
        String timeOverallSpent = activity.getString(R.string.time_spent_ar) + " " +
                ((System.currentTimeMillis() - startTime) / 1000);

        File file = createdFile();

        try {
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fOut);

            try {
                osw.write(activity.getString(R.string.dq_q1) + " " + DemographicQuestionnaire.probNum +
                        "\n\n" + dateString + "\n" + timeOverallSpent + "\n\n");

                for (int i = 0; i < questions.size(); i++) {
                    osw.write(questions.get(i));
                    osw.write("\n");
                    osw.write(answers.get(i));
                    osw.write("\n");
                    osw.write(timeSpent.get(i));
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
        File rootDirectory = new File(activity.getApplicationContext().getFilesDir(),
                "/Study_Data");
        File directory = new File(rootDirectory.getPath(), "/02_AR_Quiz");

        if (!directory.exists()) {
            boolean wasSuccessful = directory.mkdirs();
            if (!wasSuccessful) {
                Log.e(TAG, "Creating directory for AR quiz was not successful");
            }
        }

        return new File(directory, "ARQUIZ_" + DemographicQuestionnaire.probNum + ".txt");
    }
}
