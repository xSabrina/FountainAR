package com.example.fountainar.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
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

public class DemographicQuestionnaire extends AppCompatActivity {

    private static final String TAG = DemographicQuestionnaire.class.getSimpleName();

    public static int probNum;

    private final ArrayList<String> questions = new ArrayList<>();
    private final ArrayList<String> answers = new ArrayList<>();
    private final ArrayList<RadioGroup> radioGroups = new ArrayList<>();
    private final ArrayList<RadioButton> radioButtons = new ArrayList<>();
    private final ArrayList<EditText> editTexts = new ArrayList<>();
    private final ArrayList<EditText> missingInputFields = new ArrayList<>();
    private final ArrayList<RadioGroup> missingRadioGroups = new ArrayList<>();

    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_demographic_questionnaire);
        startTime = System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                setupRadioGroups();
                setupInputListeners();
                setupFinishButton();
            } else {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        }

    }

    private void setupRadioGroups() {
        RadioGroup radioGroup_q3 = findViewById(R.id.dq_q3_rg);
        RadioGroup radioGroup_q4 = findViewById(R.id.dq_q4_rg);
        RadioGroup radioGroup_q5 = findViewById(R.id.dq_q5_rg);
        RadioGroup radioGroup_q6 = findViewById(R.id.dq_q6_rg);
        RadioGroup radioGroup_q7 = findViewById(R.id.dq_q7_rg);
        RadioGroup radioGroup_q8 = findViewById(R.id.dq_q8_rg);
        RadioGroup radioGroup_q9 = findViewById(R.id.dq_q9_rg);

        radioGroups.add(radioGroup_q3);
        radioGroups.add(radioGroup_q4);
        radioGroups.add(radioGroup_q5);
        radioGroups.add(radioGroup_q6);
        radioGroups.add(radioGroup_q7);
        radioGroups.add(radioGroup_q8);
        radioGroups.add(radioGroup_q9);
    }

    private void setupInputListeners() {
        EditText subjectNr = findViewById(R.id.dq_q1_input);
        editTexts.add(subjectNr);
        EditText age = findViewById(R.id.dq_q2_input);
        editTexts.add(age);

        RadioButton q4 = findViewById(R.id.dq_q4_alternative_answer);
        radioButtons.add(q4);
        EditText q4_input = findViewById(R.id.dq_q4_alternative_answer_input);
        editTexts.add(q4_input);

        RadioButton q5_student = findViewById(R.id.dq_q5_student);
        radioButtons.add(q5_student);
        EditText q5_stud_input = findViewById(R.id.dq_q5_student_subject);
        editTexts.add(q5_stud_input);

        RadioButton q5_alt_a = findViewById(R.id.dq_q5_alternative_answer);
        radioButtons.add(q5_alt_a);
        EditText q5_input = findViewById(R.id.dq_q5_alternative_answer_input);
        editTexts.add(q5_input);

        RadioButton q6 = findViewById(R.id.dq_q6_alternative_answer);
        radioButtons.add(q6);
        EditText q6_input = findViewById(R.id.dq_q6_alternative_answer_input);
        editTexts.add(q6_input);

        RadioButton q8 = findViewById(R.id.dq_q8_alternative_answer);
        radioButtons.add(q8);
        EditText q8_input = findViewById(R.id.dq_q8_alternative_answer_input);
        editTexts.add(q8_input);

        RadioButton q9 = findViewById(R.id.dq_q9_alternative_answer);
        radioButtons.add(q9);
        EditText q9_input = findViewById(R.id.dq_q9_alternative_answer_input);
        editTexts.add(q9_input);

        for (int i = 0; i < radioButtons.size(); i++) {
            int finalI = i;
            radioButtons.get(finalI).setOnCheckedChangeListener((compoundButton, b) ->
                    editTexts.get(finalI + 2).setEnabled(compoundButton.isChecked()));
        }

    }

    private void setupFinishButton() {
        Button finishButton = findViewById(R.id.dq_finished_button);

        finishButton.setOnClickListener(view -> {
            if (everyRadioGroupFinished()) {
                getQuestionsAndAnswers();
                saveDataToFile();
                Intent intentMain = new Intent(getApplicationContext(), ArView.class);
                startActivity(intentMain);
            } else {
                highlightMissingFields();
                Toast.makeText(getApplicationContext(), R.string.fill_out_first,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean everyRadioGroupFinished() {
        missingRadioGroups.clear();
        missingInputFields.clear();
        providedInputsCompleted();

        for (int i = 0; i < radioGroups.size(); i++) {
            int selectedId = radioGroups.get(i).getCheckedRadioButtonId();
            if (selectedId == -1) {
                missingRadioGroups.add(radioGroups.get(i));
            }
        }

        return missingRadioGroups.size() == 0 && missingInputFields.size() == 0;
    }

    private void providedInputsCompleted() {
        for (int i = 0; i < editTexts.size(); i++) {
            if (i <= 1) {
                if (editTexts.get(i).getText().length() == 0) {
                    missingInputFields.add(editTexts.get(i));
                }

            } else {
                if (radioButtons.get(i - 2).isChecked()) {
                    if (editTexts.get(i).getText().length() == 0) {
                        missingInputFields.add(editTexts.get(i));
                        missingRadioGroups.add((RadioGroup) radioButtons.get(i - 2).getParent());
                    }
                }
            }
        }
    }

    private void getQuestionsAndAnswers() {
        LinearLayout linearLayout = findViewById(R.id.dq_layout);

        for (int i = 0; i < linearLayout.getChildCount(); i++) {
            View view = linearLayout.getChildAt(i + 1);

            if (view instanceof TextView && !(view instanceof EditText) &&
                    !(view instanceof Button)) {
                questions.add(((TextView) view).getText().toString() + " ");
            }

            if (view instanceof EditText) {
                if (((EditText) view).getText() != null) {
                    answers.add(((EditText) view).getText().toString());
                }
            }

            if (view instanceof RadioGroup) {
                int checkedId = ((RadioGroup) view).getCheckedRadioButtonId();
                RadioButton radioButton = findViewById(checkedId);

                if (radioButton.getText().equals(getString(R.string.dq_alternative_answer))) {
                    View editView = ((RadioGroup) view).getChildAt(((RadioGroup) view)
                            .getChildCount() - 1);
                    answers.add(radioButton.getText().toString() + ((EditText) editView)
                            .getText().toString());
                } else if (radioButton.getText().equals(getString(R.string.dq_q5_student))) {
                    View editView = ((RadioGroup) view).getChildAt(1);
                    answers.add(radioButton.getText().toString() + ((EditText) editView)
                            .getText().toString());
                } else {
                    answers.add(radioButton.getText().toString());
                }

            }
        }
    }

    private void saveDataToFile() {
        probNum = Integer.parseInt(((EditText) findViewById(R.id.dq_q1_input)).
                getText().toString());

        Date date = new Date();
        String dateString = date.toString();
        String timeSpent = String.valueOf(R.string.time_spent_questionnaire +
                ((System.currentTimeMillis() - startTime) / 1000));

        File file = createdFile();

        try {
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fOut);

            try {

                osw.write(probNum + "\n\n" + dateString + "\n" + timeSpent + "\n\n");

                for (int i = 0; i < questions.size(); i++) {
                    osw.write(questions.get(i) + "\n");
                    osw.write(answers.get(i) + "\n\n");
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
        if (!rootDirectory.exists()) {
            boolean wasRootSuccessful = rootDirectory.mkdirs();
            if (!wasRootSuccessful) {
                Log.e(TAG, "Creating directory for study data was not successful");
            }
        }

        File directory = new File(rootDirectory.getPath(), "/Demographic_Questionnaires");
        if (!directory.exists()) {
            boolean wasSuccessful = directory.mkdirs();
            if (!wasSuccessful) {
                Log.e(TAG, "Creating directory for demographic questionnaires " +
                        "was not successful");
            }
        }

        return new File(directory, "DQ_" + probNum + ".txt");
    }

    private void highlightMissingFields() {
        resetBackgrounds();

        for (int i = 0; i < missingRadioGroups.size(); i++) {
            if (missingRadioGroups.get(i).getCheckedRadioButtonId() == -1) {
                missingRadioGroups.get(i).setBackgroundColor(getColor(R.color.red_light));
                for (int j = 0; j < missingRadioGroups.get(i).getChildCount(); j++) {
                    missingRadioGroups.get(i).getChildAt(j)
                            .setBackgroundColor(getColor(R.color.red_light));
                }
            }
        }

        if (missingInputFields.size() != 0) {
            for (int i = 0; i < missingInputFields.size(); i++) {
                missingInputFields.get(i).setBackgroundColor(getColor(R.color.red_light));
            }
        }

        scrollToFirstMissingField();
    }

    private void scrollToFirstMissingField() {
        int missingFieldY;

        if (editTexts.get(0).getText().length() == 0) {
            missingFieldY = editTexts.get(0).getTop();
        } else if (editTexts.get(1).getText().length() == 0) {
            missingFieldY = editTexts.get(1).getTop();
        } else {
            missingFieldY = missingRadioGroups.get(0).getTop();
        }

        ScrollView scrollView = findViewById(R.id.dq_scroll_view);
        scrollView.scrollTo(0, missingFieldY - scrollView.getHeight() / 6);
    }

    private void resetBackgrounds() {
        for (int i = 0; i < editTexts.size(); i++) {
            editTexts.get(i).setBackgroundColor(getColor(R.color.white));
        }

        for (int i = 0; i < radioGroups.size(); i++) {
            radioGroups.get(i).setBackgroundColor(getColor(R.color.white));
            for (int j = 0; j < radioGroups.get(i).getChildCount(); j++) {
                radioGroups.get(i).getChildAt(j)
                        .setBackgroundColor(getColor(R.color.white));
            }
        }
    }

}