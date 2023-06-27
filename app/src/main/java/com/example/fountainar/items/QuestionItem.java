package com.example.fountainar.items;

import android.widget.RadioButton;
import android.widget.RadioGroup;

public class QuestionItem {
    private final String question;
    private RadioGroup answerGroup;
    private int backgroundColor;
    private int selectedId = -1;

    public QuestionItem(String question) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }


    public int getSelectedAnswerId() {
        return selectedId;
    }

    public void setSelectedAnswerId(int selectedId) {
        this.selectedId = selectedId;
    }

    public String getSelectedAnswer() {
        if (answerGroup != null) {
            RadioButton radioButton = answerGroup.findViewById(selectedId);
            int value = answerGroup.indexOfChild(radioButton) + 1;

            return String.valueOf(value);
        } else {
            return null;
        }
    }

    public RadioGroup getAnswerGroup() {
        return answerGroup;
    }

    public void setAnswerGroup(RadioGroup answerGroup) {
        this.answerGroup = answerGroup;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }
}