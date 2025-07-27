package com.gemma3n.smartlearning;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.List;

// POJO for Chat Message
class ChatMessagePojo {
    public final String text;
    public final boolean isUser;
    public final boolean isLoading;

    public ChatMessagePojo(String text, boolean isUser) {
        this.text = text;
        this.isUser = isUser;
        this.isLoading = false;
    }
    
    public ChatMessagePojo(String text, boolean isUser, boolean isLoading) {
        this.text = text;
        this.isUser = isUser;
        this.isLoading = isLoading;
    }
    
    public static ChatMessagePojo createLoadingMessage() {
        return new ChatMessagePojo("", false, true);
    }
}

enum InteractionModePojo { CHAT, QUIZ }

public class InteractionViewModel extends AndroidViewModel implements LlmHelper.LlmReadinessListener {

    private LlmHelper llmHelper; // To be initialized

    private final MutableLiveData<InteractionModePojo> _interactionMode = new MutableLiveData<>(InteractionModePojo.CHAT);
    public LiveData<InteractionModePojo> interactionMode = _interactionMode;

    // Chat State
    private final MutableLiveData<List<ChatMessagePojo>> _chatMessages = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<ChatMessagePojo>> chatMessages = _chatMessages;

    // Quiz State
    private final MutableLiveData<String> _currentQuestion = new MutableLiveData<>(null);
    public LiveData<String> currentQuestion = _currentQuestion;

    private final MutableLiveData<String> _quizResponse = new MutableLiveData<>(null);
    public LiveData<String> quizResponse = _quizResponse;

    private final MutableLiveData<String> _reformattedLesson = new MutableLiveData<>(null);
    public LiveData<String> reformattedLesson = _reformattedLesson;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<Boolean> _isLlmReady = new MutableLiveData<>(false);
    public LiveData<Boolean> isLlmReady = _isLlmReady;

    private String pendingFileContext = null;

    public InteractionViewModel(@NonNull Application application) {
        super(application);
        // LlmHelper will be set via a setter or factory method after creation,
        // as it needs context and might be long-running to initialize.
    }

    // Call this after ViewModel creation and before use
    public void initializeLlm(String modelAssetPath) {
        if (llmHelper == null) {
            this.llmHelper = new LlmHelper(getApplication(), modelAssetPath, this);
        }
    }


    @Override
    public void onLlmReady(boolean isReady) {
        _isLlmReady.postValue(isReady);
        if (isReady && pendingFileContext != null) {
            setFileContext(pendingFileContext);
            pendingFileContext = null; // Clear after use
        }
    }

    public void setFileContext(String content) {
        if (llmHelper != null && llmHelper.isLlmReady()) {
            llmHelper.setContext(content);
            // Optionally add an initial message
            // addChatMessage("Context loaded.", false);
        } else {
            pendingFileContext = content; // Store if LLM not ready yet
        }
    }


    public void toggleMode() {
        if (_interactionMode.getValue() == InteractionModePojo.CHAT) {
            _interactionMode.setValue(InteractionModePojo.QUIZ);
        } else {
            _interactionMode.setValue(InteractionModePojo.CHAT);
        }
        _currentQuestion.setValue(null);
        _quizResponse.setValue(null);
    }

    // --- Chat Methods ---
    public void sendChatMessage(String message) {
        if (message == null || message.trim().isEmpty() || Boolean.TRUE.equals(_isLoading.getValue())) {
            Log.d("InteractionViewModel", "sendChatMessage: Skipping - message empty or already loading");
            return;
        }
        Log.d("InteractionViewModel", "sendChatMessage: Starting - " + message);
        addChatMessage(message, true);
        addLoadingMessage();
        _isLoading.setValue(true);
        Log.d("InteractionViewModel", "sendChatMessage: Loading set to true");
        if (llmHelper != null) {
            llmHelper.generateChatResponse(message, response -> {
                Log.d("InteractionViewModel", "sendChatMessage: Response received");
                removeLoadingMessage();
                addChatMessage(response, false);
                _isLoading.setValue(false);
                Log.d("InteractionViewModel", "sendChatMessage: Loading set to false");
            });
        } else {
            removeLoadingMessage();
            addChatMessage("LLM not initialized.", false);
            _isLoading.setValue(false);
            Log.d("InteractionViewModel", "sendChatMessage: LLM not initialized, loading set to false");
        }
    }

    private void addChatMessage(String text, boolean isUser) {
        List<ChatMessagePojo> currentMessages = _chatMessages.getValue();
        if (currentMessages == null) {
            currentMessages = new ArrayList<>();
        }
        ArrayList<ChatMessagePojo> updatedMessages = new ArrayList<>(currentMessages);
        updatedMessages.add(new ChatMessagePojo(text, isUser));
        _chatMessages.setValue(updatedMessages);
    }
    
    private void addLoadingMessage() {
        List<ChatMessagePojo> currentMessages = _chatMessages.getValue();
        if (currentMessages == null) {
            currentMessages = new ArrayList<>();
        }
        ArrayList<ChatMessagePojo> updatedMessages = new ArrayList<>(currentMessages);
        updatedMessages.add(ChatMessagePojo.createLoadingMessage());
        _chatMessages.setValue(updatedMessages);
    }
    
    private void removeLoadingMessage() {
        List<ChatMessagePojo> currentMessages = _chatMessages.getValue();
        if (currentMessages != null && !currentMessages.isEmpty()) {
            ArrayList<ChatMessagePojo> updatedMessages = new ArrayList<>(currentMessages);
            // Remove the last message if it's a loading message
            if (updatedMessages.size() > 0 && updatedMessages.get(updatedMessages.size() - 1).isLoading) {
                updatedMessages.remove(updatedMessages.size() - 1);
                _chatMessages.setValue(updatedMessages);
            }
        }
    }

    // --- Quiz Methods ---
    public void requestNewQuestion() {
        if (Boolean.TRUE.equals(_isLoading.getValue())) return;
        _isLoading.setValue(true);
        _currentQuestion.setValue(null);
        _quizResponse.setValue(null);
        if (llmHelper != null) {
            llmHelper.generateQuestionFromContext(question -> {
                _currentQuestion.setValue(question);
                _isLoading.setValue(false);
            });
        } else {
            _currentQuestion.setValue("LLM not initialized.");
            _isLoading.setValue(false);
        }
    }

    public void submitAnswer(String userAnswer) {
        String question = _currentQuestion.getValue();
        if (question == null || question.trim().isEmpty() ||
                userAnswer == null || userAnswer.trim().isEmpty() ||
                Boolean.TRUE.equals(_isLoading.getValue())) return;

        _isLoading.setValue(true);
        _quizResponse.setValue(null);
        if (llmHelper != null) {
            llmHelper.evaluateAnswer(question, userAnswer, response -> {
                _quizResponse.setValue(response);
                _isLoading.setValue(false);
            });
        } else {
            _quizResponse.setValue("LLM not initialized.");
            _isLoading.setValue(false);
        }
    }

    public void reformatLesson(String lessonText) {
        _isLoading.setValue(true);
        if (llmHelper != null) {
            llmHelper.reformatLesson(lessonText, reformattedLesson -> {
                _reformattedLesson.setValue(reformattedLesson);
                _isLoading.setValue(false);
            });
        } else {
            _isLoading.setValue(false);
        }
    }

    public void clearQuiz() {
        _currentQuestion.setValue(null);
        _quizResponse.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (llmHelper != null) {
            Log.d("InteractionViewModel", "Clearing LLM resources.");
            llmHelper.close();
        }
    }
}