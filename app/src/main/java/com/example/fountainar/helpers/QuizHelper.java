package com.example.fountainar.helpers;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.fountainar.R;
import com.example.fountainar.activities.DemographicQuestionnaire;
import com.example.fountainar.activities.TAMQuestionnaire;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;

/**
 * Helper for managing the quiz functionality in the AR activity.
 */
public class QuizHelper {
    private static final String TAG = QuizHelper.class.getSimpleName();
    private final Activity ACTIVITY;
    private final ArrayList<String> QUESTIONS = new ArrayList<>();
    private final ArrayList<String> ANSWERS = new ArrayList<>();
    private final ArrayList<String> TIME_SPENT = new ArrayList<>();
    private final long AR_START_TIME;

    private long quizStart;
    private long questionStart;
    private boolean questionStarted;
    private TextView task;
    private Button button;
    private RadioGroup radioGroup;

    public QuizHelper(Activity activity) {
        this.ACTIVITY = activity;
        AR_START_TIME = System.currentTimeMillis();
    }

    /**
     * Sets up the quiz by initializing UI elements and event listeners.
     */
    @SuppressLint("CutPasteId")
    public void setupQuiz() {
        button = ACTIVITY.findViewById(R.id.ar_button);
        TextView instructions = ACTIVITY.findViewById(R.id.ar_layout_instructions_text);
        task = ACTIVITY.findViewById(R.id.ar_tv_task);
        radioGroup = ACTIVITY.findViewById(R.id.ar_rg_task);

        ACTIVITY.runOnUiThread(() -> {
            instructions.setText(R.string.ar_quiz_instructions);
            TextView title = ACTIVITY.findViewById(R.id.ar_quiz_title);
            title.setText(R.string.faculty_fountain);
            button.setVisibility(View.VISIBLE);
        });

        button.setOnClickListener(view -> {
            if (button.getText().equals(ACTIVITY.getString(R.string.start))) {
                instructions.setVisibility(View.GONE);
                task.setVisibility(View.VISIBLE);
                radioGroup.setVisibility(View.VISIBLE);
                button.setText(R.string.further);
                quizStart = System.currentTimeMillis();
            } else {
                updateTaskLayout();

                if (questionStarted) {
                    questionStart = System.currentTimeMillis() - quizStart;
                }
            }
        });
    }

    private void updateTaskLayout() {
        ACTIVITY.runOnUiThread(() -> {
            if (task.getText().equals(ACTIVITY.getString(R.string.ar_question_task1))) {
                checkRadioGroup(ACTIVITY.getString(R.string.ar_question_task2), 1);
            } else if (task.getText().equals(ACTIVITY.getString(R.string.ar_question_task2))) {
                checkRadioGroup(ACTIVITY.getString(R.string.ar_question_task3), 2);
            } else if (task.getText().equals(ACTIVITY.getString(R.string.ar_question_task3))) {
                checkRadioGroup(null, 3);
            }
        });
    }

    /**
     * Checks the radio group for a selected answer and updates the UI elements, saves the answers,
     * and navigates to the next question or the next activity depending on questionNum.
     *
     * @param nextTask    The text for the next task/question.
     * @param questionNum The current question number.
     */
    @SuppressLint("DiscouragedApi")
    private void checkRadioGroup(String nextTask, int questionNum) {
        int color = radioGroup.getCheckedRadioButtonId() == -1 ?
                ContextCompat.getColor(ACTIVITY, R.color.red_light) :
                ContextCompat.getColor(ACTIVITY, R.color.white);
        questionStarted = radioGroup.getCheckedRadioButtonId() != -1;
        radioGroup.setBackgroundColor(color);

        if (radioGroup.getCheckedRadioButtonId() == -1) {
            Toast.makeText(ACTIVITY.getApplicationContext(), R.string.ar_missing_answer,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        getQuestionsAndAnswers(questionNum);

        if (questionNum < 3) {
            radioGroup.clearCheck();
            task.setText(nextTask);

            String[] answerTaskIds = {
                    "ar_answer_task" + (questionNum + 1) + "_1",
                    "ar_answer_task" + (questionNum + 1) + "_2",
                    "ar_answer_task" + (questionNum + 1) + "_3",
                    "ar_answer_task" + (questionNum + 1) + "_4"
            };

            for (int i = 0; i < radioGroup.getChildCount(); i++) {
                RadioButton radioButton = (RadioButton) radioGroup.getChildAt(i);
                String answerTaskId = answerTaskIds[i];
                int resourceId = ACTIVITY.getResources().getIdentifier(answerTaskId,
                        "string", ACTIVITY.getPackageName());
                String answerText = ACTIVITY.getString(resourceId);
                radioButton.setText(answerText);
            }
        } else {
            saveAnswersToFile();
            Intent intent = new Intent(ACTIVITY.getApplicationContext(), TAMQuestionnaire.class);
            ACTIVITY.startActivity(intent);
        }
    }

    /**
     * Retrieves the questions and answers from the quiz UI and saves them.
     */
    private void getQuestionsAndAnswers(int questionNum) {
        String question = getTextFromTextView(R.id.ar_tv_task) + " ";
        QUESTIONS.add(question);
        saveTime();

        int checkedId = radioGroup.getCheckedRadioButtonId();
        RadioButton radioButton = ACTIVITY.findViewById(checkedId);
        String answer = radioButton.getText().toString();
        String correctness = isAnswerCorrect(questionNum, answer) ? "Correct" : "False";

        String answerWithCorrectness = answer + "\n" + correctness;
        ANSWERS.add(answerWithCorrectness);
    }

    private boolean isAnswerCorrect(int questionNum, String answer) {
        switch (questionNum) {
            case 1:
                return answer.equals(ACTIVITY.getString(R.string.ar_answer_task1_4));
            case 2:
                return answer.equals(ACTIVITY.getString(R.string.ar_answer_task2_1));
            case 3:
                return answer.equals(ACTIVITY.getString(R.string.ar_answer_task3_3));
            default:
                return false;
        }
    }


    /**
     * Calculates and saves the time spent on the current task/question and stores it in the
     * 'timeSpent' ArrayList. If the calculated time is less than 1 second, a minimum time of
     * 1 second is recorded to avoid rounding down to 0 seconds.
     */
    private void saveTime() {
        long exactTime = (System.currentTimeMillis() - questionStart) - quizStart;

        if (exactTime > 0 && exactTime < 1000) {
            exactTime = 1000;
        }

        long time = (exactTime / 1000);
        String taskDone = ACTIVITY.getString(R.string.time_spent_task) + " " + time;
        TIME_SPENT.add(taskDone);
    }

    /**
     * Retrieves the text from a TextView based on its ID.
     *
     * @param textViewId The ID of the TextView.
     * @return The text content of the TextView.
     */
    private String getTextFromTextView(int textViewId) {
        TextView textView = ACTIVITY.findViewById(textViewId);
        return textView.getText().toString();
    }

    /**
     * Saves the answers and associated questions and time spent for each to a file.
     */
    private void saveAnswersToFile() {
        Date date = new Date();
        String dateString = date.toString();
        long timeOverallSpent = (System.currentTimeMillis() - AR_START_TIME) / 1000;
        File file = createdFile();

        try (FileOutputStream fOut = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fOut)) {
            osw.write(ACTIVITY.getString(R.string.dq_q1) + " "
                    + DemographicQuestionnaire.probNum + "\n\n" + dateString + "\n"
                    + ACTIVITY.getString(R.string.time_spent_ar) + " " + timeOverallSpent + "\n\n");

            for (int i = 0; i < QUESTIONS.size(); i++) {
                osw.write(QUESTIONS.get(i));
                osw.write("\n");
                osw.write(ANSWERS.get(i));
                osw.write("\n");
                osw.write(TIME_SPENT.get(i));
                osw.write("\n\n");
            }

            osw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates the file to store the quiz answers.
     *
     * @return The created File object.
     */
    private File createdFile() {
        File rootDirectory = new File(ACTIVITY.getApplicationContext().getFilesDir(),
                "/Study_Data");
        File directory = new File(rootDirectory.getPath(), "/02_AR_Quizzes");

        if (!directory.exists()) {
            boolean wasSuccessful = directory.mkdirs();
            if (!wasSuccessful) {
                Log.e(TAG, "Creating directory for AR quiz was not successful");
            }
        }

        return new File(directory, "AR_QUIZ_" + DemographicQuestionnaire.probNum + ".txt");
    }
}