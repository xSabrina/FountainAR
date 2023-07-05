package com.example.fountainar.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fountainar.R;
import com.example.fountainar.items.QuestionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * An Adapter to manage the display and interaction of TAM (Technology Acceptance Model) questions
 * in a RecyclerView. Provides methods to retrieve selected answers, update background colors, and
 * check if all radio groups are finished.
 */
public class TAMQuestionAdapter
        extends RecyclerView.Adapter<TAMQuestionAdapter.QuestionViewHolder> {

    private final Context context;
    private final List<String> questions;
    private final List<QuestionItem> questionItems = new ArrayList<>();

    public TAMQuestionAdapter(Context context, List<String> questions) {
        this.context = context;
        this.questions = questions;

        for (String question : questions) {
            QuestionItem questionItem = new QuestionItem(question);
            questionItems.add(questionItem);
        }
    }

    /**
     * Inflates the layout for a question item and creates a new QuestionViewHolder.
     *
     * @param parent   The parent ViewGroup.
     * @param viewType The view type.
     * @return The created QuestionViewHolder.
     */
    @NonNull
    @Override
    public QuestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.tam_item, parent,
                false);

        return new QuestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestionViewHolder holder, int position) {
        holder.bind(position);
    }

    /**
     * Returns the number of questions in the data set.
     *
     * @return The number of questions.
     */
    @Override
    public int getItemCount() {
        return questions.size();
    }

    /**
     * Checks if all radio groups have a selected answer.
     *
     * @param recyclerView The RecyclerView containing the radio groups.
     * @return true if all radio groups have a selected answer, false otherwise.
     */
    public boolean everyRadioGroupFinished(RecyclerView recyclerView) {
        for (int i = 0; i < questionItems.size(); i++) {
            if (questionItems.get(i).getSelectedAnswerId() == -1) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView
                        .getLayoutManager();

                if (layoutManager != null) {
                    int offset = -10;
                    layoutManager.scrollToPositionWithOffset(i, offset);
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the list of questions.
     *
     * @return The list of questions.
     */
    public List<String> getQuestions() {
        return questions;
    }

    /**
     * Returns a list of selected answer values.
     *
     * @return The list of selected answer values.
     */
    public List<String> getAnswerValues() {
        List<String> checkedValues = new ArrayList<>();

        for (QuestionItem questionItem : questionItems) {
            String answerValue = questionItem.getSelectedAnswer();
            checkedValues.add(answerValue);
        }

        return checkedValues;
    }

    /**
     * Updates the background colors of the question items based on selected answers.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void updateBackgroundColors() {
        for (QuestionItem questionItem : questionItems) {
            int color = questionItem.getSelectedAnswerId() == -1
                    ? ContextCompat.getColor(context, R.color.red_light)
                    : ContextCompat.getColor(context, R.color.white);
            questionItem.setBackgroundColor(color);
        }

        notifyDataSetChanged();
    }

    public class QuestionViewHolder extends RecyclerView.ViewHolder {

        private final TextView questionText;
        private final TextView leftValueText;
        private final TextView rightValueText;
        private final RadioGroup answerGroup;

        public QuestionViewHolder(@NonNull View itemView) {
            super(itemView);
            questionText = itemView.findViewById(R.id.question_text);
            leftValueText = itemView.findViewById(R.id.tam_verbal_scale_left);
            rightValueText = itemView.findViewById(R.id.tam_verbal_scale_right);
            answerGroup = itemView.findViewById(R.id.answer_group);
        }

        public void bind(int position) {
            QuestionItem questionItem = questionItems.get(position);
            String question = questionItem.getQuestion();
            questionText.setText(question);
            setScaleTextValues(question);
            answerGroup.setOnCheckedChangeListener(null);
            answerGroup.clearCheck();
            answerGroup.setOnCheckedChangeListener((group, checkedId) ->
                    questionItem.setSelectedAnswerId(checkedId));
            questionItem.setAnswerGroup(answerGroup);

            int selectedAnswerId = questionItem.getSelectedAnswerId();
            if (selectedAnswerId != -1) {
                answerGroup.check(selectedAnswerId);
            }

            int backgroundColor = questionItem.getBackgroundColor();
            if (backgroundColor != 0) {
                itemView.setBackgroundColor(backgroundColor);
            }
        }

        /**
         * Sets the text values for the left and right scale of a question.
         *
         * @param question The question text.
         */
        private void setScaleTextValues(String question) {
            int leftValueResId, rightValueResId;

            if (question.equals(context.getString(R.string.tam_pe1))) {
                leftValueResId = R.string.tam_disgusting;
                rightValueResId = R.string.tam_enjoyable;
            } else if (question.equals(context.getString(R.string.tam_pe2))) {
                leftValueResId = R.string.tam_dull;
                rightValueResId = R.string.tam_exciting;
            } else if (question.equals(context.getString(R.string.tam_pe3))) {
                leftValueResId = R.string.tam_unpleasant;
                rightValueResId = R.string.tam_pleasant;
            } else if (question.equals(context.getString(R.string.tam_pe4))) {
                leftValueResId = R.string.tam_boring;
                rightValueResId = R.string.tam_interesting;
            } else {
                leftValueResId = R.string.tam_strongly_disagree;
                rightValueResId = R.string.tam_strongly_agree;
            }

            leftValueText.setText(context.getString(leftValueResId));
            rightValueText.setText(context.getString(rightValueResId));
        }
    }
}