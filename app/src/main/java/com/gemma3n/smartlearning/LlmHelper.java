package com.gemma3n.smartlearning;

import android.content.Context;
import android.util.Log;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer; // Requires API 24+


public class LlmHelper {
    private static final String TAG = "LlmHelper";
    private final Context context;
    private final String modelPath;
    private LlmInference llmChatInference, llmQuizInference;
    private LlmInferenceSession llmChatSession, llmQuizSession;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean isLlmReady = false;
    private String quizPrompt;

    // Listener for readiness
    public interface LlmReadinessListener {
        void onLlmReady(boolean isReady);
    }
    final private LlmReadinessListener readinessListener;

    public LlmHelper(Context context, String modelPath, LlmReadinessListener listener) {
        this.context = context.getApplicationContext();
        this.modelPath = modelPath; // e.g., "your_llm_model.tflite"
        this.readinessListener = listener;
        initializeLlm();
    }

    private void initializeLlm() {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "LLM Start initialization.");
                LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(4096)
                        .setPreferredBackend(LlmInference.Backend.GPU)
                        .build();

                llmChatInference = LlmInference.createFromOptions(context, options);

                LlmInferenceSession.LlmInferenceSessionOptions sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTemperature(0)
                        .setTopK(40)
                        .build();

                llmChatSession = LlmInferenceSession.createFromOptions(llmChatInference, sessionOptions);
                isLlmReady = true;
                if (readinessListener != null) {
                    // Post to main thread if listener updates UI
                    new android.os.Handler(context.getMainLooper()).post(() -> readinessListener.onLlmReady(true));
                }
                Log.d(TAG, "LLM Initialized successfully.");
            } catch (Exception e) {
                isLlmReady = false;
                if (readinessListener != null) {
                    new android.os.Handler(context.getMainLooper()).post(() -> readinessListener.onLlmReady(false));
                }
                Log.e(TAG, "Error initializing LLM: " + e.getMessage(), e);
            }
        });
    }

    public boolean isLlmReady() {
        return isLlmReady;
    }

    public void setContext(String fileContent) {
        String initialContext = "you are a helpful teacher that helps a student to learn this lesson: ";
        llmChatSession.addQueryChunk(initialContext + fileContent);
        quizPrompt = "ask me a question about the lesson, in no more than 80 words";
    }

    // Using Consumer for callbacks (requires API 24+).
    // For lower API levels, define custom interfaces.
    public void generateChatResponse(String userInput, Consumer<String> callback) {
        if (!isLlmReady || llmChatInference == null || llmChatSession == null) {
            callback.accept("LLM is not ready.");
            return;
        }
        executorService.execute(() -> {
            try {
                llmChatSession.addQueryChunk(userInput);
                String result = llmChatSession.generateResponse();
                // Post to main thread if callback updates UI
                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept(result != null ? result : "No response from LLM."));
            } catch (Exception e) {
                Log.e(TAG, "Error generating chat response: " + e.getMessage(), e);
                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept("Error generating response: " + e.getMessage()));
            }
        });
    }

    public void generateQuestionFromContext(Consumer<String> callback) {
        if (!isLlmReady || llmChatInference == null || llmChatSession == null) {
            callback.accept("LLM is not ready.");
            return;
        }
        executorService.execute(() -> {
            try {
//                String prompt = "Ask me a question about the lesson.";
                String prompt = quizPrompt;
                llmChatSession.addQueryChunk(prompt);
                String result = llmChatSession.generateResponse();
                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept(result != null ? result : "Could not generate question."));
            } catch (Exception e) {
                Log.e(TAG, "Error generating question: " + e.getMessage(), e);
                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept("Error generating question: " + e.getMessage()));
            }
        });
    }

    public void evaluateAnswer(String question, String userAnswer, Consumer<String> callback) {
        if (!isLlmReady || llmChatInference == null || llmChatSession == null) {
            callback.accept("LLM is not ready.");
            return;
        }
        executorService.execute(() -> {
            try {
                String prompt = "Evaluate the following answer to the question in no more than 80 words: ";
                llmChatSession.addQueryChunk(prompt + userAnswer);
                String result = llmChatSession.generateResponse();
                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept(result != null ? result : "Could not evaluate answer."));
            } catch (Exception e) {
                Log.e(TAG, "Error evaluating answer: " + e.getMessage(), e);
                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept("Error evaluating answer: " + e.getMessage()));
            }
        });
    }

    public void reformatLesson(String fileContent, Consumer<String> callback) {
        if (!isLlmReady || llmChatInference == null) {
            callback.accept(null);
            return;
        }

        executorService.execute(() -> {
            try {
                String prompt = "Reformat the following text into a clear, structured, and readable lesson with a title, headings, subheadings, short paragraphs, lists for items/steps, bold for key terms, maintaining an informative tone, and using Markdown-like syntax for structure: ";
                String result = llmChatInference.generateResponse(prompt + fileContent);
                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept(result));
            } catch (Exception e) {
                Log.e(TAG, "Error reformatting the lesson: " + e.getMessage(), e);
                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept(null));
            }
        });
    }


    public void close() {
        executorService.execute(() -> {
            if (llmChatInference != null) {
                llmChatInference.close();
                llmChatInference = null;
            }
            if (llmChatSession != null) {
                llmChatSession.close();
                llmChatSession = null;
            }

            isLlmReady = false;
            Log.d(TAG, "LLM closed.");
        });
        executorService.shutdown();
    }
}