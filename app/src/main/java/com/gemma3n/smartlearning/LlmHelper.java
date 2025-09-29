package com.gemma3n.smartlearning;

import android.content.Context;
import android.util.Log;

import com.google.ai.edge.localagents.core.proto.Content;
import com.google.ai.edge.localagents.core.proto.FunctionCall;
import com.google.ai.edge.localagents.core.proto.FunctionDeclaration;
import com.google.ai.edge.localagents.core.proto.FunctionResponse;
import com.google.ai.edge.localagents.core.proto.GenerateContentResponse;
import com.google.ai.edge.localagents.core.proto.Part;
import com.google.ai.edge.localagents.core.proto.Tool;
import com.google.ai.edge.localagents.fc.ChatSession;
import com.google.ai.edge.localagents.fc.GemmaFormatter;
import com.google.ai.edge.localagents.fc.GenerativeModel;
import com.google.ai.edge.localagents.fc.LlmInferenceBackend;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
//import com.google.ai.generativelanguage.v1main.Content;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer; // Requires API 24+


public class LlmHelper {
    private static final String TAG = "LlmHelper";
    private final Context context;
    private final String modelPath;
    private final String loraPath;
    private LlmInference llmChatInference;
    private LlmInferenceSession llmChatSession;
    private LlmInferenceBackend llmInferenceBackend;
    private Tool tool;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean isLlmReady = false;
    private String quizPrompt;
    final private LlmReadinessListener readinessListener;
    // Listener for readiness
    public interface LlmReadinessListener {
        void onLlmReady(boolean isReady);
    }

    static class ToolsForLlm {
        public static String getDeviceLanguage() {
            Locale currentLocale = Locale.getDefault();
            String lang = currentLocale.toString();
            Log.d("ToolsForLlm", "Device language: " + lang);
            lang = "en_US";
            return lang;
        }

        private ToolsForLlm() {}
    }

    public LlmHelper(Context context, String modelPath, String loraPath, LlmReadinessListener listener) {
        this.context = context.getApplicationContext();
        this.modelPath = modelPath;
        this.loraPath = loraPath;
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
                        .setTopK(50)
                        .setTopP(0.1f)
//                        .setLoraPath(loraPath)
                        .build();

                llmChatSession = LlmInferenceSession.createFromOptions(llmChatInference, sessionOptions);
                FunctionDeclaration getLanguage = FunctionDeclaration.newBuilder()
                        .setName("getDeviceLanguage")
                        .setDescription("Returns the system language set on the device.")
                        .build();
                tool = Tool.newBuilder().addFunctionDeclarations(getLanguage).build();
                llmInferenceBackend = new LlmInferenceBackend(llmChatInference, new GemmaFormatter());
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

    public void evaluateAnswer(String userAnswer, Consumer<String> callback) {
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
                String msg = "Translate this educational content into device's language only if it is in a different language and then reformat it into clear, structured markdown:\n\n" +
                        "1. Create a main title using #\n" +
                        "2. Use ## for main sections\n" +
                        "3. Use ### for subsections\n" +
                        "4. Make key terms and concepts **bold**\n" +
                        "5. Use bullet points (*) for lists\n" +
                        "6. Keep paragraphs short (2-3 sentences max)\n" +
                        "7. Organize information logically\n" +
                        "8. Make it easy to read and study\n\n" +
                        "Content to reformat:\n" + fileContent;

                Content systemInstruction = Content.newBuilder()
                        .setRole("system")
                        .addParts(Part.newBuilder().setText("You are a helpful teacher that helps a student to structure their lesson into clear, structured markdown and translate it into the specified language."))
                        .build();
                GenerativeModel generativeModel = new GenerativeModel(
                        llmInferenceBackend,
                        systemInstruction,
                        List.of(tool));
                ChatSession chat = generativeModel.startChat();
                GenerateContentResponse response = chat.sendMessage(msg);
                Part message = response.getCandidates(0).getContent().getParts(0);
                if (message.hasFunctionCall()) {
                    FunctionCall functionCall = message.getFunctionCall();
                    Log.d(TAG, "Function call: " + functionCall.getName());
                    String result = null;
                    switch (functionCall.getName()) {
                        case "getDeviceLanguage":
                            result = ToolsForLlm.getDeviceLanguage();
                            break;
                        default:
                            Log.e(TAG, "Unknown function call: " + functionCall.getName());
                            throw new Exception("Unknown function call: " + functionCall.getName());
                    }
                    FunctionResponse functionResponse = FunctionResponse.newBuilder()
                            .setName(functionCall.getName())
                            .setResponse(
                                    Struct.newBuilder()
                                            .putFields("result", Value.newBuilder().setStringValue(result).build())
                            ).build();
                    Content functionResponseContent = Content.newBuilder()
                            .setRole("user")
                            .addParts(Part.newBuilder().setFunctionResponse(functionResponse))
                            .build();
                    GenerateContentResponse resp = chat.sendMessage(functionResponseContent);
                    message = resp.getCandidates(0).getContent().getParts(0);
                    if (message.hasText()) {
                        String reformattedLesson = message.getText();
                        new android.os.Handler(context.getMainLooper()).post(() -> callback.accept(reformattedLesson));
                    } else {
                        Log.e(TAG, "No text in response");
                        throw new Exception("No text in response");
                    }
                } else if (message.hasText()) {
                    String reformattedLesson = message.getText();
                    new android.os.Handler(context.getMainLooper()).post(() -> callback.accept(reformattedLesson));
                }

//                String result = llmChatInference.generateResponse(prompt);
//                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept(result));
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