package com.example.fountainar.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fountainar.R;
import com.example.fountainar.adapters.TAMQuestionAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TAMQuestionnaire extends AppCompatActivity {

    private static final String TAG = DemographicQuestionnaire.class.getSimpleName();

    private List<String> questions = new ArrayList<>();
    private final ArrayList<String> tamTags = new ArrayList<>();
    private List<String> answerValues = new ArrayList<>();

    private List<RadioGroup> radioGroups = new ArrayList<>();

    private long startTime;

    private TAMQuestionAdapter adapter;

    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tech_acc_questionnaire);
        startTime = System.currentTimeMillis();

        setupRecyclerView();
        setupFinishButton();
    }

    private void setupRecyclerView() {
        if (radioGroups.isEmpty()) {
            recyclerView = findViewById(R.id.recycler_view);
            List<String> questions = new ArrayList<>(20);

            int[] questionResourceIds = {
                    R.string.tam_pu1, R.string.tam_pu2, R.string.tam_pu3, R.string.tam_pu4,
                    R.string.tam_peou1, R.string.tam_peou2, R.string.tam_peou3, R.string.tam_peou4,
                    R.string.tam_pe1, R.string.tam_pe2, R.string.tam_pe3, R.string.tam_pe4,
                    R.string.tam_bi1, R.string.tam_bi2, R.string.tam_bi3, R.string.tam_bi4,
                    R.string.tam_bi5, R.string.tam_bi6, R.string.tam_bi7, R.string.tam_bi8
            };

            for (int resourceId : questionResourceIds) {
                String question = getString(resourceId);
                questions.add(question);
                String questionWithoutPrefix = getResources().getResourceEntryName(resourceId);
                int lastDotIndex = questionWithoutPrefix.lastIndexOf('.');
                String substringAfterDot = questionWithoutPrefix
                        .substring(lastDotIndex + 1);
                tamTags.add(substringAfterDot.substring(4));
            }

            adapter = new TAMQuestionAdapter(this, questions);
            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setupFinishButton() {
        Button finishButton = findViewById(R.id.tam_finished_button);

        finishButton.setOnClickListener(view -> {
            if (adapter.everyRadioGroupFinished(recyclerView)) {
                questions = adapter.getQuestions();
                answerValues = adapter.getAnswerValues();
                saveAnswersToFile();

                Intent intentMain = new Intent(getApplicationContext(), EndActivity.class);
                startActivity(intentMain);
            } else {
                Toast.makeText(getApplicationContext(), R.string.fill_out_first,
                        Toast.LENGTH_SHORT).show();
                adapter.updateBackgroundColors();
            }
        });
    }

    private void saveAnswersToFile() {
        Date date = new Date();
        String dateString = date.toString();
        String timeSpent = getString(R.string.time_spent_questionnaire) + " "
                + ((System.currentTimeMillis() - startTime) / 1000);

        File file = createdFile();

        try (FileOutputStream fOut = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fOut)) {

            osw.write(getString(R.string.dq_q1) + " " + DemographicQuestionnaire.probNum
                    + "\n\n" + dateString + "\n" + timeSpent + "\n\n");

            for (int i = 0; i < questions.size(); i++) {
                osw.write(questions.get(i) + "\n");
                osw.write(tamTags.get(i) + "\n");
                osw.write(answerValues.get(i) + "\n\n");
            }

            osw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File createdFile() {
        File rootDirectory = new File(this.getApplicationContext().getFilesDir(),
                "/Study_Data");
        File directory = new File(rootDirectory.getPath(), "/03_TAM_Questionnaires");

        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(TAG, "Creating directory for TAM questionnaires was not successful");
        }

        return new File(directory, "TAM_" + DemographicQuestionnaire.probNum + ".txt");
    }

    @Override
    public void onBackPressed() {}
}

