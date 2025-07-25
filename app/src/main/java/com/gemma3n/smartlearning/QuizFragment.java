package com.gemma3n.smartlearning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

public class QuizFragment extends Fragment {

    private InteractionViewModel interactionViewModel;
    private TextView questionTextView, feedbackTextView;
    private EditText answerEditText;
    private Button generateQuestionButton, submitAnswerButton;
    private ProgressBar quizLoadingIndicator;
    private LinearLayout questionAnswerLayout, feedbackLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_quiz, container, false);

        // Initialize UI elements
        questionTextView = view.findViewById(R.id.questionTextView);
        feedbackTextView = view.findViewById(R.id.feedbackTextView);
        answerEditText = view.findViewById(R.id.answerEditText);
        generateQuestionButton = view.findViewById(R.id.generateQuestionButton);
        submitAnswerButton = view.findViewById(R.id.submitAnswerButton);
        quizLoadingIndicator = view.findViewById(R.id.quizLoadingIndicator);
        questionAnswerLayout = view.findViewById(R.id.questionAnswerLayout);
        feedbackLayout = view.findViewById(R.id.feedbackLayout);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get the ViewModel from the Activity scope
        interactionViewModel = new ViewModelProvider(requireActivity()).get(InteractionViewModel.class);

        // Observe LiveData from ViewModel
        setupObservers();

        // Setup button listeners
        setupButtonListeners();

        // Initial UI state based on ViewModel (in case of configuration changes)
        updateUiBasedOnViewModelState();
    }

    private void setupObservers() {
        interactionViewModel.currentQuestion.observe(getViewLifecycleOwner(), question -> {
            if (question != null && !question.isEmpty()) {
                questionTextView.setText(question);
                questionAnswerLayout.setVisibility(View.VISIBLE);
                answerEditText.setText(""); // Clear previous answer
                feedbackLayout.setVisibility(View.GONE); // Hide old feedback
                feedbackTextView.setText("");
                generateQuestionButton.setText("Next Question");
            } else {
                // If question is null or empty after a request, it might mean "no question generated"
                // or it's the initial state.
                if (Boolean.FALSE.equals(interactionViewModel.isLoading.getValue())) { // Only hide if not loading
                    questionAnswerLayout.setVisibility(View.GONE);
                }
                // If the question is cleared, reset button text
                if (question == null) {
                    generateQuestionButton.setText("Generate Question");
                }
            }
        });

        interactionViewModel.quizResponse.observe(getViewLifecycleOwner(), response -> {
            if (response != null && !response.isEmpty()) {
                feedbackTextView.setText(response);
                feedbackLayout.setVisibility(View.VISIBLE);
            } else {
                feedbackLayout.setVisibility(View.GONE);
            }
        });

        interactionViewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            // Only manage loading state if this fragment is for QUIZ mode
            if (interactionViewModel.interactionMode.getValue() == InteractionModePojo.QUIZ) {
                quizLoadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                generateQuestionButton.setEnabled(!isLoading);
                submitAnswerButton.setEnabled(!isLoading && questionAnswerLayout.getVisibility() == View.VISIBLE);
                answerEditText.setEnabled(!isLoading && questionAnswerLayout.getVisibility() == View.VISIBLE);

                if (isLoading) {
                    // If loading, hide question/answer and feedback until new data arrives
                    // questionAnswerLayout.setVisibility(View.GONE); // Covered by currentQuestion observer
                    // feedbackLayout.setVisibility(View.GONE); // Covered by quizResponse observer
                }
            }
        });

        // Ensure UI updates if mode changes while this fragment is visible
        interactionViewModel.interactionMode.observe(getViewLifecycleOwner(), mode -> {
            if (mode == InteractionModePojo.QUIZ) {
                // Potentially re-evaluate button states if needed when switching to quiz
                updateUiBasedOnViewModelState();
            }
        });
    }

    private void setupButtonListeners() {
        generateQuestionButton.setOnClickListener(v -> {
            interactionViewModel.requestNewQuestion();
            // Clear previous answer and feedback when requesting a new question
            answerEditText.setText("");
            feedbackTextView.setText("");
            feedbackLayout.setVisibility(View.GONE);
            questionAnswerLayout.setVisibility(View.GONE); // Hide until new question arrives
        });

        submitAnswerButton.setOnClickListener(v -> {
            String userAnswer = answerEditText.getText().toString().trim();
            if (!userAnswer.isEmpty()) {
                interactionViewModel.submitAnswer(userAnswer);
                answerEditText.setText(""); // Clear after submission
            } else {
                Toast.makeText(getContext(), "Please enter an answer.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUiBasedOnViewModelState() {
        // Handle current question display
        String currentQ = interactionViewModel.currentQuestion.getValue();
        if (currentQ != null && !currentQ.isEmpty()) {
            questionTextView.setText(currentQ);
            questionAnswerLayout.setVisibility(View.VISIBLE);
            generateQuestionButton.setText("Next Question");
        } else {
            questionAnswerLayout.setVisibility(View.GONE);
            generateQuestionButton.setText("Generate Question");
        }

        // Handle feedback display
        String currentFeedback = interactionViewModel.quizResponse.getValue();
        if (currentFeedback != null && !currentFeedback.isEmpty()) {
            feedbackTextView.setText(currentFeedback);
            feedbackLayout.setVisibility(View.VISIBLE);
        } else {
            feedbackLayout.setVisibility(View.GONE);
        }

        // Handle loading state
        Boolean isLoading = interactionViewModel.isLoading.getValue();
        if (isLoading != null && interactionViewModel.interactionMode.getValue() == InteractionModePojo.QUIZ) {
            quizLoadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            generateQuestionButton.setEnabled(!isLoading);
            submitAnswerButton.setEnabled(!isLoading && questionAnswerLayout.getVisibility() == View.VISIBLE);
            answerEditText.setEnabled(!isLoading && questionAnswerLayout.getVisibility() == View.VISIBLE);
        } else if (interactionViewModel.interactionMode.getValue() == InteractionModePojo.QUIZ) {
            // Default to not loading if value is null
            quizLoadingIndicator.setVisibility(View.GONE);
            generateQuestionButton.setEnabled(true);
            submitAnswerButton.setEnabled(questionAnswerLayout.getVisibility() == View.VISIBLE);
            answerEditText.setEnabled(questionAnswerLayout.getVisibility() == View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh UI state when fragment becomes visible, especially if returning from another screen
        // or if the underlying data might have changed while paused.
        updateUiBasedOnViewModelState();
    }
}