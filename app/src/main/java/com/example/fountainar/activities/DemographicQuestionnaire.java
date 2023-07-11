package com.example.fountainar.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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
import com.example.fountainar.handlers.BackPressedHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;

/**
 * Activity for users to fill out a demographic questionnaire. It manages the layout components,
 * validates input fields, saves data to a file, and provides visual feedback. Also handles
 * permission checks, extracts questions and answers, and highlights missing fields.
 */
public class DemographicQuestionnaire extends AppCompatActivity {

    private static final String TAG = DemographicQuestionnaire.class.getSimpleName();
    private final ArrayList<String> QUESTIONS = new ArrayList<>();
    private final ArrayList<String> ANSWERS = new ArrayList<>();
    private final ArrayList<RadioGroup> RADIO_GROUPS = new ArrayList<>();
    private final ArrayList<EditText> EDIT_TEXTS = new ArrayList<>();
    private final ArrayList<EditText> MISSING_INPUT_FIELDS = new ArrayList<>();
    private final ArrayList<RadioGroup> MISSING_RADIO_GROUPS = new ArrayList<>();

    public static int probNum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demographic_questionnaire);
        checkForStoragePermission();
        setupActivity();
        BackPressedHandler.setupBackPressedCallback(this);
    }

    /**
     * Checks for storage permission and handles the logic based on the Android version.
     * This method ensures that the necessary storage permissions are granted before proceeding.
     */
    private void checkForStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, R.string.stor_permission_needed, Toast.LENGTH_LONG)
                        .show();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent =
                            new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }, 2000);
            }
        }
    }

    /**
     * Initializes various components and prepares the activity for user interaction.
     */
    private void setupActivity() {
        setupRadioGroups();
        setupInputListeners();
        setupLayoutListeners();
        setupEditTextListeners();
        setupFinishButton();
    }

    /**
     * Initializes the RadioGroup objects and adds them to the radioGroups list.
     */
    private void setupRadioGroups() {
        int[] radioGroupIds = {R.id.dq_q3_rg, R.id.dq_q4_rg, R.id.dq_q5_rg, R.id.dq_q6_rg,
                R.id.dq_q7_rg, R.id.dq_q8_rg, R.id.dq_q9_rg};
        for (int id : radioGroupIds) {
            RadioGroup radioGroup = findViewById(id);
            RADIO_GROUPS.add(radioGroup);
        }
    }

    /**
     * Attaches touch listeners for the EditText fields.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupInputListeners() {
        int[] editTextIds = {R.id.dq_q1_input, R.id.dq_q2_input,
                R.id.dq_q4_alternative_answer_input, R.id.dq_q5_student_subject,
                R.id.dq_q5_alternative_answer_input, R.id.dq_q6_alternative_answer_input,
                R.id.dq_q8_alternative_answer_input, R.id.dq_q9_alternative_answer_input};

        for (int id : editTextIds) {
            EditText editText = findViewById(id);
            EDIT_TEXTS.add(editText);

            editText.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_SCROLL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Manages the focus behavior of EditText fields and removes focus from previously used fields.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupLayoutListeners() {
        LinearLayout parentLayout = findViewById(R.id.dq_layout);
        parentLayout.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                clearEditTextFocus(getCurrentFocus());
            }
            return false;
        });

        for (RadioGroup rg : RADIO_GROUPS) {
            for (int j = 0; j < rg.getChildCount(); j++) {
                View childView = rg.getChildAt(j);
                if (childView instanceof RadioButton) {
                    RadioButton rb = (RadioButton) childView;
                    rb.setOnTouchListener((view, motionEvent) -> {
                        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                            clearEditTextFocus(getCurrentFocus());
                        }
                        return false;
                    });
                }
            }
        }
    }

    /**
     * Removes focus from an EditText field and hides the keyboard from the screen.
     */
    private void clearEditTextFocus(View focusedView) {
        if (focusedView instanceof EditText) {
            focusedView.clearFocus();
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }

    /**
     * Attaches touch listeners for specific EditText fields for custom behaviors.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupEditTextListeners() {
        for (int i = 2; i < EDIT_TEXTS.size(); i++) {
            EditText eT = EDIT_TEXTS.get(i);

            eT.setOnTouchListener((v, event) -> {
                RadioGroup rg = (RadioGroup) v.getParent();
                int childNumber = 0;
                for (int j = 0; j < rg.getChildCount(); j++) {
                    if (rg.getChildAt(j).equals(eT)) {
                        childNumber = j - 1;
                    }
                }
                RadioButton rb = (RadioButton) rg.getChildAt(childNumber);
                rb.setChecked(true);
                return false;
            });
        }
    }

    /**
     * Sets up the functionality of the finish button and defines its behavior when clicked.
     */
    private void setupFinishButton() {
        Button finishButton = findViewById(R.id.dq_finished_button);
        finishButton.setOnClickListener(view -> {
            if (everyRadioGroupFinished()) {
                getQuestionsAndAnswers();
                saveDataToFile();
                Intent intentMain = new Intent(getApplicationContext(), ARActivity.class);
                startActivity(intentMain);
            } else {
                highlightMissingFields();
                Toast.makeText(getApplicationContext(), R.string.fill_out_first,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Verifies if all the radio groups have been answered and validates the input.
     */
    private boolean everyRadioGroupFinished() {
        MISSING_RADIO_GROUPS.clear();
        MISSING_INPUT_FIELDS.clear();
        checkProvidedInputs();

        for (RadioGroup radioGroup : RADIO_GROUPS) {
            int selectedId = radioGroup.getCheckedRadioButtonId();

            if (selectedId == -1) {
                MISSING_RADIO_GROUPS.add(radioGroup);
            } else {
                for (int j = 0; j < radioGroup.getChildCount(); j++) {
                    View view = radioGroup.getChildAt(j);
                    View nextView = radioGroup.getChildAt(j + 1);

                    if (selectedId == view.getId() && view instanceof RadioButton && nextView
                            instanceof EditText) {
                        EditText editText = (EditText) nextView;

                        if (editText.getText().toString().trim().isEmpty()) {
                            MISSING_INPUT_FIELDS.add(editText);
                        }
                    }
                }
            }
        }

        return MISSING_RADIO_GROUPS.isEmpty() && MISSING_INPUT_FIELDS.isEmpty();
    }


    /**
     * Verifies if all the required input fields have been filled by the user.
     */
    private void checkProvidedInputs() {
        for (int i = 0; i < 2; i++) {
            if (EDIT_TEXTS.get(i).getText().length() == 0) {
                MISSING_INPUT_FIELDS.add(EDIT_TEXTS.get(i));
            }
        }
    }

    /**
     * Extracts the questions and answers entered by the user from the layout views and stores them
     * for further processing.
     */
    private void getQuestionsAndAnswers() {
        LinearLayout linearLayout = findViewById(R.id.dq_layout);

        for (int i = 1; i < linearLayout.getChildCount(); i++) {
            View view = linearLayout.getChildAt(i);

            if (view instanceof TextView && !(view instanceof EditText) &&
                    !(view instanceof Button)) {
                QUESTIONS.add(((TextView) view).getText().toString());
            } else if (view instanceof EditText && ((EditText) view).getText() != null) {
                ANSWERS.add(((EditText) view).getText().toString());
            } else if (view instanceof RadioGroup) {
                RadioGroup radioGroup = (RadioGroup) view;
                int checkedId = radioGroup.getCheckedRadioButtonId();
                RadioButton radioButton = findViewById(checkedId);

                if (radioButton.getText().equals(getString(R.string.dq_alternative_answer))) {
                    View editView = radioGroup.getChildAt(radioGroup.getChildCount() - 1);
                    ANSWERS.add(radioButton.getText().toString() + " " +
                            ((EditText) editView).getText().toString());
                } else if (radioButton.getText().equals(getString(R.string.dq_q5_student))) {
                    View editView = radioGroup.getChildAt(1);
                    ANSWERS.add(radioButton.getText().toString() + ((EditText) editView).getText()
                            .toString());
                } else {
                    ANSWERS.add(radioButton.getText().toString());
                }
            }
        }
    }

    /**
     * Stores the collected data to a file.
     */
    private void saveDataToFile() {
        probNum = Integer.parseInt(((EditText) findViewById(R.id.dq_q1_input)).getText()
                .toString());
        Date date = new Date();
        String dateString = date.toString();
        File file = createdFile();

        try (FileOutputStream fOut = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fOut)) {

            osw.write(getString(R.string.dq_q1) + " " + probNum + "\n\n" + dateString + "\n\n");

            for (int i = 0; i < QUESTIONS.size(); i++) {
                osw.write(QUESTIONS.get(i) + "\n");
                osw.write(ANSWERS.get(i) + "\n\n");
            }

            osw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates the file for storing the data.
     */
    private File createdFile() {
        File rootDirectory = new File(this.getApplicationContext().getFilesDir(),
                "/Study_Data");
        createDirectory(rootDirectory,
                "Creating directory for study data was not successful");

        File directory = new File(rootDirectory.getPath(), "/01_Demographic_Questionnaires");
        createDirectory(directory,
                "Creating directory for demographic questionnaires was not successful");

        return new File(directory, "DQ_" + probNum + ".txt");
    }

    /**
     * Creates a directory for storing the data file.
     */
    private void createDirectory(File directory, String errorMessage) {
        if (!directory.exists()) {
            boolean wasSuccessful = directory.mkdirs();

            if (!wasSuccessful) {
                Log.e(TAG, errorMessage);
            }
        }
    }

    /**
     * Highlights the missing input fields and radio groups.
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    private void highlightMissingFields() {
        resetBackgrounds();

        for (EditText inputField : MISSING_INPUT_FIELDS) {
            inputField.setBackground(getDrawable(R.drawable.rounded_corners_highlighted));
        }

        for (RadioGroup radioGroup : MISSING_RADIO_GROUPS) {
            radioGroup.setBackgroundColor(getColor(R.color.red_light));

            for (int i = 0; i < radioGroup.getChildCount(); i++) {
                View childView = radioGroup.getChildAt(i);

                if (childView instanceof EditText) {
                    childView.setBackground(getDrawable(R.drawable.rounded_corners_highlighted));
                } else {
                    childView.setBackgroundColor(getColor(R.color.red_light));
                }
            }
        }

        scrollToFirstMissingField();
    }

    /**
     * Resets the backgrounds of the input fields and radio groups.
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    private void resetBackgrounds() {
        for (int i = 0; i < RADIO_GROUPS.size(); i++) {
            RadioGroup radioGroup = RADIO_GROUPS.get(i);
            radioGroup.setBackgroundColor(getColor(R.color.white));

            if (i < 2) {
                EDIT_TEXTS.get(i).setBackground(getDrawable(R.drawable.rounded_corners));
            }

            for (int j = 0; j < radioGroup.getChildCount(); j++) {
                View childView = radioGroup.getChildAt(j);
                childView.setBackground(childView instanceof EditText ?
                        getDrawable(R.drawable.rounded_corners) : getDrawable(R.color.white));
            }
        }
    }

    /**
     * Scrolls the view to the first missing field.
     */
    private void scrollToFirstMissingField() {
        final ScrollView scrollView = findViewById(R.id.dq_scroll_view);
        final int scrollViewHeight = scrollView.getHeight();

        View view = !MISSING_INPUT_FIELDS.isEmpty() ? (View) MISSING_INPUT_FIELDS.get(0).getParent()
                : MISSING_RADIO_GROUPS.get(0);
        assert view != null;

        int viewTop = view.getTop();
        int viewHeight = view.getHeight();
        int scrollY = Math.max(0, viewTop + (viewHeight / 2) - (scrollViewHeight / 2));
        int missingFieldY = Math.min(scrollY, scrollView.getChildAt(0).getHeight()
                - scrollViewHeight);

        scrollView.smoothScrollTo(0, missingFieldY);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}