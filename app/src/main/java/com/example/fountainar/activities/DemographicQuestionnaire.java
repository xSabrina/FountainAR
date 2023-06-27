package com.example.fountainar.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
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
        }

        for (EditText editText : editTexts) {
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

    private void clearEditTextFocus(View focusedView){
        if (focusedView instanceof EditText) {
            focusedView.clearFocus();
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }

    /**
     * Sets up the necessary layout listeners to remove the focus from previous used EditTexts
     * and close they keyboard.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupLayoutListeners() {
        LinearLayout parentLayout = findViewById(R.id.dq_layout);

        parentLayout.setOnTouchListener((view, motionEvent) -> {
            View focusedView = getCurrentFocus();

            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                clearEditTextFocus(focusedView);
            }

            return false;
        });

        for (int i = 0; i < radioGroups.size(); i++) {
            RadioGroup rg = radioGroups.get(i);

            for(int j = 0; j < rg.getChildCount(); j++){
                if(rg.getChildAt(j) instanceof RadioButton) {
                    RadioButton rb = (RadioButton) rg.getChildAt(j);

                    rb.setOnTouchListener((view, motionEvent) -> {
                        View focusedView = getCurrentFocus();

                        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                            clearEditTextFocus(focusedView);
                        }

                        return false;
                    });
                }
            }
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

        for (int i = 0; i < radioGroups.size(); i++) {
            int selectedId = radioGroups.get(i).getCheckedRadioButtonId();

            if (selectedId == -1) {
                missingRadioGroups.add(radioGroups.get(i));
            } else {
                for (int j = 0; j < radioButtons.size(); j++) {

                    if (selectedId == radioButtons.get(j).getId()) {
                        if (editTexts.get(j + 2).getText().toString().trim().length() == 0) {
                            missingInputFields.add(editTexts.get(j + 2));
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
                int checkedId = ((RadioGroup) view).getCheckedRadioButtonId();
                RadioButton radioButton = findViewById(checkedId);

                if (radioButton.getText().equals(getString(R.string.dq_alternative_answer))) {
                    View editView = ((RadioGroup) view).getChildAt(((RadioGroup) view)
                            .getChildCount() - 1);
                    answers.add(radioButton.getText().toString() + ((EditText) editView).getText()
                            .toString());
                } else if (radioButton.getText().equals(getString(R.string.dq_q5_student))) {
                    View editView = ((RadioGroup) view).getChildAt(1);
                    answers.add(radioButton.getText().toString() + ((EditText) editView).getText()
                            .toString());
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

        File file = createdFile();

        try {
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fOut);

            try {

                osw.write(getString(R.string.dq_q1) + " " + probNum + "\n\n" + dateString
                        + "\n\n");

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

        File directory = new File(rootDirectory.getPath(), "/01_Demographic_Questionnaires");
        if (!directory.exists()) {
            boolean wasSuccessful = directory.mkdirs();
            if (!wasSuccessful) {
                Log.e(TAG, "Creating directory for demographic questionnaires " +
                        "was not successful");
            }
        }

        return new File(directory, "DQ_" + probNum + ".txt");
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void highlightMissingFields() {
        resetBackgrounds();

        if (!missingInputFields.isEmpty()) {
            for (int i = 0; i < missingInputFields.size(); i++) {
                missingInputFields.get(i)
                        .setBackground(getDrawable(R.drawable.rounded_corners_highlighted));
            }
        }

        if (!missingRadioGroups.isEmpty()) {
            for (int i = 0; i < missingRadioGroups.size(); i++) {
                RadioGroup rG = missingRadioGroups.get(i);
                rG.setBackgroundColor(getColor(R.color.red_light));
                for (int j = 0; j < rG.getChildCount(); j++) {
                    if (missingRadioGroups.get(i).getChildAt(j) instanceof EditText) {
                        missingRadioGroups.get(i).getChildAt(j)
                                .setBackground(getDrawable(R.drawable.rounded_corners_highlighted));
                    } else {
                        rG.getChildAt(j).setBackgroundColor(getColor(R.color.red_light));
                    }
                }
            }
        }

        scrollToFirstMissingField();
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void resetBackgrounds() {
        for (int i = 0; i < radioGroups.size(); i++) {

            if (i < 2) {
                editTexts.get(i).setBackground(getDrawable(R.drawable.rounded_corners));
            }

            radioGroups.get(i).setBackgroundColor(getColor(R.color.white));

            for (int j = 0; j < radioGroups.get(i).getChildCount(); j++) {

                if (radioGroups.get(i).getChildAt(j) instanceof EditText) {
                    radioGroups.get(i).getChildAt(j)
                            .setBackground(getDrawable(R.drawable.rounded_corners));
                } else {
                    radioGroups.get(i).getChildAt(j).setBackgroundColor(getColor(R.color.white));
                }
            }
        }
    }

    private void scrollToFirstMissingField() {
        ScrollView scrollView = findViewById(R.id.dq_scroll_view);
        float missingFieldY = scrollView.getTop();

        if (!missingInputFields.isEmpty()) {
            missingFieldY = missingInputFields.get(0).getY() + 400;
        }

        if (!missingRadioGroups.isEmpty()) {

            if (!missingInputFields.isEmpty()) {

                if (missingRadioGroups.get(0).getY() < missingFieldY) {
                    missingFieldY = missingRadioGroups.get(0).getY() - 300;
                }
            } else {
                missingFieldY = missingRadioGroups.get(0).getY() - 300;
            }
        }

        scrollView.scrollTo(0, (int) missingFieldY);
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
            Toast.makeText(this, R.string.read_stor_needed,
                    Toast.LENGTH_LONG).show();

            if (StoragePermissionHelper.shouldShowRequestReadPermissionRationale(this)) {
                StoragePermissionHelper.launchReadPermissionSettings(this);
            }

            finish();
        }

        if (!StoragePermissionHelper.hasWritePermission(this)) {
            Toast.makeText(this, R.string.write_stor_needed,
                    Toast.LENGTH_LONG).show();

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
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Optional: Perform any additional actions when the OK button is clicked
                        // For example, you can close the dialog or take other actions
                        dialog.dismiss();
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

}