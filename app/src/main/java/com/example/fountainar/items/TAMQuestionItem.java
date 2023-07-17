package com.example.fountainar.items;

import android.widget.RadioButton;
import android.widget.RadioGroup;

/**
 * A question item with its associated properties and methods.
 */
public class TAMQuestionItem {
    private final String QUESTION;
    
    private RadioGroup answerGroup;
    private int backgroundColor;
    private int selectedId = -1;

    public TAMQuestionItem(String question) {
        this.QUESTION = question;
    }

    /**
     * Returns the question text.
     *
     * @return The question text.
     */
    public String getQuestion() {
        return QUESTION;
    }

    /**
     * Returns the ID of the selected answer.
     *
     * @return The ID of the selected answer.
     */
    public int getSelectedAnswerId() {
        return selectedId;
    }

    /**
     * Sets the ID of the selected answer.
     *
     * @param selectedId The ID of the selected answer.
     */
    public void setSelectedAnswerId(int selectedId) {
        this.selectedId = selectedId;
    }

    /**
     * Returns the selected answer as a string.
     *
     * @return The selected answer as a string.
     */
    public String getSelectedAnswer() {
        if (answerGroup != null) {
            RadioButton radioButton = answerGroup.findViewById(selectedId);
            int value = answerGroup.indexOfChild(radioButton) + 1;

            return String.valueOf(value);
        } else {
            return null;
        }
    }

    /**
     * Sets the answer RadioGroup for the question.
     *
     * @param answerGroup The RadioGroup containing the answer options.
     */
    public void setAnswerGroup(RadioGroup answerGroup) {
        this.answerGroup = answerGroup;
    }

    /**
     * Returns the background color of the question item.
     *
     * @return The background color of the question item.
     */
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * Sets the background color of the question item.
     *
     * @param backgroundColor The background color of the question item.
     */
    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }
}