package com.example.fountainar.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fountainar.R;
import com.example.fountainar.helpers.StoragePermissionHelper;

import java.io.File;
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
    private final ArrayList<EditText> editTexts = new ArrayList<>();
    private final ArrayList<EditText> missingInputFields = new ArrayList<>();
    private final ArrayList<RadioGroup> missingRadioGroups = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demographic_questionnaire);
        checkForStoragePermission();
        setupActivity();
    }

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
        } else {
            checkStoragePermissions();
        }
    }

    private void setupActivity() {
        setupRadioGroups();
        setupInputListeners();
        setupLayoutListeners();
        setupEditTextListeners();
        setupFinishButton();
    }

    private void setupRadioGroups() {
        int[] radioGroupIds = {R.id.dq_q3_rg, R.id.dq_q4_rg, R.id.dq_q5_rg, R.id.dq_q6_rg,
                R.id.dq_q7_rg, R.id.dq_q8_rg, R.id.dq_q9_rg};

        for (int id : radioGroupIds) {
            RadioGroup radioGroup = findViewById(id);
            radioGroups.add(radioGroup);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupInputListeners() {
        int[] editTextIds = {R.id.dq_q1_input, R.id.dq_q2_input,
                R.id.dq_q4_alternative_answer_input, R.id.dq_q5_student_subject,
                R.id.dq_q5_alternative_answer_input, R.id.dq_q6_alternative_answer_input,
                R.id.dq_q8_alternative_answer_input, R.id.dq_q9_alternative_answer_input};

        for (int id : editTextIds) {
            EditText editText = findViewById(id);
            editTexts.add(editText);

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
     * Sets up necessary layout listeners to remove the focus from previous used EditTexts.
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

        for (RadioGroup rg : radioGroups) {
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
     * Closes the keyboard after losing the focus on EditText.
     */
    private void clearEditTextFocus(View focusedView) {
        if (focusedView instanceof EditText) {
            focusedView.clearFocus();
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupEditTextListeners() {
        for (int i = 2; i < editTexts.size(); i++) {
            EditText eT = editTexts.get(i);

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

    private boolean everyRadioGroupFinished() {
        missingRadioGroups.clear();
        missingInputFields.clear();
        checkProvidedInputs();

        for (RadioGroup radioGroup : radioGroups) {
            int selectedId = radioGroup.getCheckedRadioButtonId();

            if (selectedId == -1) {
                missingRadioGroups.add(radioGroup);
            } else {
                for (int j = 0; j < radioGroup.getChildCount(); j++) {
                    View view = radioGroup.getChildAt(j);
                    View nextView = radioGroup.getChildAt(j + 1);

                    if (selectedId == view.getId() && view instanceof RadioButton
                            && nextView instanceof EditText) {
                        EditText editText = (EditText) nextView;

                        if (editText.getText().toString().trim().length() == 0) {
                            missingInputFields.add(editText);
                        }
                    }
                }
            }

        }

        return missingRadioGroups.isEmpty() && missingInputFields.isEmpty();
    }

    private void checkProvidedInputs() {
        for (int i = 0; i < 2; i++) {
            if (editTexts.get(i).getText().length() == 0) {
                missingInputFields.add(editTexts.get(i));
            }
        }
    }

    private void getQuestionsAndAnswers() {
        LinearLayout linearLayout = findViewById(R.id.dq_layout);

        for (int i = 1; i < linearLayout.getChildCount(); i++) {
            View view = linearLayout.getChildAt(i);

            if (view instanceof TextView && !(view instanceof EditText) &&
                    !(view instanceof Button)) {
                questions.add(((TextView) view).getText().toString() + " ");
            } else if (view instanceof EditText && ((EditText) view).getText() != null) {
                answers.add(((EditText) view).getText().toString());
            } else if (view instanceof RadioGroup) {
                RadioGroup radioGroup = (RadioGroup) view;
                int checkedId = radioGroup.getCheckedRadioButtonId();
                RadioButton radioButton = findViewById(checkedId);

                if (radioButton.getText().equals(getString(R.string.dq_alternative_answer))) {
                    View editView = radioGroup.getChildAt(radioGroup.getChildCount() - 1);
                    answers.add(radioButton.getText().toString() + ((EditText) editView).getText()
                            .toString());
                } else if (radioButton.getText().equals(getString(R.string.dq_q5_student))) {
                    View editView = radioGroup.getChildAt(1);
                    answers.add(radioButton.getText().toString() + ((EditText) editView).getText()
                            .toString());
                } else {
                    answers.add(radioButton.getText().toString());
                }
            }
        }
    }

    private void saveDataToFile() {
        probNum = Integer.parseInt(((EditText) findViewById(R.id.dq_q1_input)).getText()
                .toString());
        Date date = new Date();
        String dateString = date.toString();
        File file = createdFile();

        try (FileOutputStream fOut = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fOut)) {

            osw.write(getString(R.string.dq_q1) + " " + probNum + "\n\n" + dateString + "\n\n");

            for (int i = 0; i < questions.size(); i++) {
                osw.write(questions.get(i) + "\n");
                osw.write(answers.get(i) + "\n\n");
            }

            osw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

    private void createDirectory(File directory, String errorMessage) {
        if (!directory.exists()) {
            boolean wasSuccessful = directory.mkdirs();

            if (!wasSuccessful) {
                Log.e(TAG, errorMessage);
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void highlightMissingFields() {
        resetBackgrounds();

        for (EditText inputField : missingInputFields) {
            inputField.setBackground(getDrawable(R.drawable.rounded_corners_highlighted));
        }

        for (RadioGroup radioGroup : missingRadioGroups) {
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

    @SuppressLint("UseCompatLoadingForDrawables")
    private void resetBackgrounds() {
        for (int i = 0; i < radioGroups.size(); i++) {
            RadioGroup radioGroup = radioGroups.get(i);
            radioGroup.setBackgroundColor(getColor(R.color.white));

            if (i < 2) {
                editTexts.get(i).setBackground(getDrawable(R.drawable.rounded_corners));
            }

            for (int j = 0; j < radioGroup.getChildCount(); j++) {
                View childView = radioGroup.getChildAt(j);
                if (childView instanceof EditText) {
                    childView.setBackground(getDrawable(R.drawable.rounded_corners));
                } else {
                    childView.setBackgroundColor(getColor(R.color.white));
                }
            }
        }
    }

    private void scrollToFirstMissingField() {
        final ScrollView scrollView = findViewById(R.id.dq_scroll_view);
        final int scrollViewHeight = scrollView.getHeight();

        View view;

        if (!missingInputFields.isEmpty()) {
            view = (View) missingInputFields.get(0).getParent();
        } else {
            view = missingRadioGroups.get(0);
        }

        assert view != null;
        int viewTop = view.getTop();
        int viewHeight = view.getHeight();

        int scrollY = Math.max(0, viewTop + (viewHeight / 2) - (scrollViewHeight / 2));
        int missingFieldY = Math.min(scrollY, scrollView.getChildAt(0).getHeight() - scrollViewHeight);

        scrollView.smoothScrollTo(0, missingFieldY);
    }


    private void checkStoragePermissions() {
        if (!StoragePermissionHelper.hasReadPermission(this)) {
            StoragePermissionHelper.requestReadPermission(this);
        }

        if (!StoragePermissionHelper.hasWritePermission(this)) {
            StoragePermissionHelper.requestWritePermission(this);
        }

        if (StoragePermissionHelper.hasReadPermission(this)) {
            StoragePermissionHelper.hasWritePermission(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (!StoragePermissionHelper.hasReadPermission(this)) {
            Toast.makeText(this, R.string.read_stor_needed, Toast.LENGTH_LONG).show();

            if (StoragePermissionHelper.shouldShowRequestReadPermissionRationale(this)) {
                StoragePermissionHelper.launchReadPermissionSettings(this);
            }

            finish();
        }

        if (!StoragePermissionHelper.hasWritePermission(this)) {
            Toast.makeText(this, R.string.write_stor_needed, Toast.LENGTH_LONG).show();

            if (StoragePermissionHelper.shouldShowRequestWritePermissionRationale(this)) {
                StoragePermissionHelper.launchWritePermissionSettings(this);
            }

            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkForStoragePermission();
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("You cannot")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

}