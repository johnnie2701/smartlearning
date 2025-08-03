/*
 * Smart Learning - AI-Powered Educational Android Application
 * 
 * This project was developed as part of the Google Gemma 3n Impact Challenge.
 * 
 * Competition: https://kaggle.com/competitions/google-gemma-3n-hackathon
 * Authors: Glenn Cameron, Omar Sanseviero, Gus Martins, Ian Ballantyne, 
 *            Kat Black, Mark Sherwood, Milen Ferev, Ronghui Zhu, 
 *            Nilay Chauhan, Pulkit Bhuwalka, Emily Kosa, Addison Howard
 * Year: 2025
 */

package com.gemma3n.smartlearning;

import android.content.Context;
import android.util.Log;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer; // Requires API 24+

/**
 * LlmHelper - Core class for managing Google MediaPipe GenAI LLM operations
 * 
 * This class encapsulates all interactions with the Gemma-3n model, providing:
 * - Asynchronous LLM initialization with GPU acceleration
 * - Context-aware chat responses for educational content
 * - Dynamic quiz question generation from lesson material  
 * - Intelligent answer evaluation with feedback
 * - Content reformatting with markdown structuring
 * 
 * GEMMA 3N IMPLEMENTATION DETAILS:
 * - Uses MediaPipe GenAI framework for local inference
 * - Configured for GPU acceleration when available
 * - Temperature=0 for deterministic educational responses
 * - TopK=40 for balanced creativity in question generation
 * - Max tokens=4096 for comprehensive responses
 * 
 * USAGE EXAMPLE:
 * ```java
 * LlmHelper llmHelper = new LlmHelper(context, modelPath, new LlmReadinessListener() {
 *     @Override
 *     public void onLlmReady(boolean isReady) {
 *         if (isReady) {
 *             llmHelper.setContext("Lesson content here...");
 *             llmHelper.generateChatResponse("What is photosynthesis?", response -> {
 *                 // Handle AI response
 *             });
 *         }
 *     }
 * });
 * ```
 * 
 * SETUP REQUIREMENTS:
 * 1. Place Gemma-3n model file in device storage
 * 2. Ensure RECORD_AUDIO permission for voice features
 * 3. Minimum API level 24 for Consumer interface
 * 4. GPU-capable device recommended for performance
 */
public class LlmHelper {
    private static final String TAG = "LlmHelper";
    
    // Core Android context for accessing resources and main thread
    private final Context context;
    
    // Path to the Gemma-3n model file on device storage
    // Expected format: "/data/local/tmp/llm/gemma-3n-E2B-it-int4.task"
    private final String modelPath;
    
    // MediaPipe GenAI inference engine for Gemma-3n model
    // Handles the actual LLM computation and GPU acceleration
    private LlmInference llmChatInference;
    
    // Session for maintaining conversation context and parameters
    // Configured with temperature=0 and topK=40 for educational use
    private LlmInferenceSession llmChatSession;
    
    // Single-threaded executor for all LLM operations to avoid conflicts
    // All AI inference runs on background thread to prevent UI blocking
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    // Flag indicating if the LLM is successfully initialized and ready for use
    private boolean isLlmReady = false;
    
    // Pre-configured prompt for quiz question generation
    // Optimized for concise educational questions (80 words max)
    private String quizPrompt;

    /**
     * Callback interface for LLM initialization status
     * Notifies when the model is ready for inference operations
     */
    public interface LlmReadinessListener {
        /**
         * Called when LLM initialization completes
         * @param isReady true if initialization successful, false if failed
         */
        void onLlmReady(boolean isReady);
    }
    
    // Listener for notifying when LLM initialization completes
    final private LlmReadinessListener readinessListener;

    /**
     * Constructs a new LlmHelper instance and begins asynchronous model initialization
     * 
     * @param context Android application context for resource access
     * @param modelPath Full path to the Gemma-3n model file on device storage
     *                  Example: "/data/local/tmp/llm/gemma-3n-E2B-it-int4.task"
     * @param listener Callback to receive initialization status updates
     */
    public LlmHelper(Context context, String modelPath, LlmReadinessListener listener) {
        this.context = context.getApplicationContext();
        this.modelPath = modelPath; // Store the complete path to Gemma-3n model
        this.readinessListener = listener;
        // Begin model initialization on background thread immediately
        initializeLlm();
    }

    /**
     * Initializes the Gemma-3n LLM with optimized settings for educational use
     * 
     * GEMMA 3N CONFIGURATION:
     * - GPU backend for maximum performance
     * - 4096 max tokens for comprehensive responses
     * - Temperature=0 for deterministic educational answers
     * - TopK=40 for balanced creativity in question generation
     * 
     * This method runs asynchronously to avoid blocking the UI thread
     */
    private void initializeLlm() {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Starting Gemma-3n model initialization...");
                
                // Configure LLM inference options optimized for educational content
                LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)              // Path to Gemma-3n model file
                        .setMaxTokens(4096)                   // Support for detailed explanations
                        .setPreferredBackend(LlmInference.Backend.GPU)  // GPU acceleration when available
                        .build();

                // Create the inference engine with MediaPipe GenAI
                llmChatInference = LlmInference.createFromOptions(context, options);

                // Configure session parameters for educational interactions
                LlmInferenceSession.LlmInferenceSessionOptions sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTemperature(0)    // Deterministic responses for consistency
                        .setTopK(40)          // Balanced creativity for question generation
                        .build();

                // Create session for maintaining conversation context
                llmChatSession = LlmInferenceSession.createFromOptions(llmChatInference, sessionOptions);
                
                // Mark as ready and notify listener on main thread
                isLlmReady = true;
                if (readinessListener != null) {
                    // Switch to main thread for UI updates
                    new android.os.Handler(context.getMainLooper()).post(() -> readinessListener.onLlmReady(true));
                }
                Log.d(TAG, "Gemma-3n model initialized successfully and ready for inference.");
                
            } catch (Exception e) {
                // Handle initialization failure
                isLlmReady = false;
                if (readinessListener != null) {
                    new android.os.Handler(context.getMainLooper()).post(() -> readinessListener.onLlmReady(false));
                }
                Log.e(TAG, "Failed to initialize Gemma-3n model: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Checks if the Gemma-3n model is ready for inference operations
     * 
     * @return true if model is initialized and ready, false otherwise
     */
    public boolean isLlmReady() {
        return isLlmReady;
    }

    /**
     * Sets the educational context for all subsequent AI interactions
     * 
     * This method prepares the Gemma-3n model with lesson content, enabling:
     * - Context-aware responses in chat mode
     * - Relevant question generation in quiz mode  
     * - Content-specific answer evaluation
     * 
     * @param fileContent The lesson text content to use as context
     *                   Should be educational material for optimal results
     */
    public void setContext(String fileContent) {
        // Set teacher persona for educational interactions
        String initialContext = "you are a helpful teacher that helps a student to learn this lesson: ";
        
        // Add the lesson content to the session context
        llmChatSession.addQueryChunk(initialContext + fileContent);
        
        // Configure optimized prompt for quiz question generation
        quizPrompt = "ask me a question about the lesson, in no more than 80 words";
    }

    /**
     * Generates context-aware educational responses using Gemma-3n
     * 
     * This method processes user questions about the lesson content and returns
     * AI-generated explanations, definitions, and educational guidance.
     * 
     * GEMMA 3N FEATURES USED:
     * - Session context for lesson-aware responses
     * - Teacher persona for educational tone
     * - Deterministic generation (temperature=0)
     * 
     * @param userInput Student's question or message about the lesson
     * @param callback  Consumer to receive the AI response on main thread
     *                 Callback receives response string or error message
     * 
     * EXAMPLE USAGE:
     * llmHelper.generateChatResponse("What is photosynthesis?", response -> {
     *     // Update UI with AI explanation
     *     textView.setText(response);
     * });
     */
    public void generateChatResponse(String userInput, Consumer<String> callback) {
        // Validate that the model is ready for inference
        if (!isLlmReady || llmChatInference == null || llmChatSession == null) {
            callback.accept("LLM is not ready.");
            return;
        }
        
        // Execute on background thread to avoid blocking UI
        executorService.execute(() -> {
            try {
                // Add user question to conversation context
                llmChatSession.addQueryChunk(userInput);
                
                // Generate response using Gemma-3n model
                String result = llmChatSession.generateResponse();
                
                // Return result on main thread for UI updates
                new android.os.Handler(context.getMainLooper()).post(() -> 
                    callback.accept(result != null ? result : "No response from LLM."));
                    
            } catch (Exception e) {
                Log.e(TAG, "Error generating chat response: " + e.getMessage(), e);
                // Return error message on main thread
                new android.os.Handler(context.getMainLooper()).post(() -> 
                    callback.accept("Error generating response: " + e.getMessage()));
            }
        });
    }

    /**
     * Generates educational quiz questions from lesson content using Gemma-3n
     * 
     * This method creates context-aware questions that test student understanding
     * of the uploaded educational material. Questions are concise (≤80 words)
     * and designed to assess comprehension, analysis, and application.
     * 
     * GEMMA 3N OPTIMIZATION:
     * - Uses lesson context for relevant questions
     * - TopK=40 for creative question variety
     * - Optimized prompts for educational assessment
     * 
     * @param callback Consumer to receive generated question on main thread
     * 
     * EXAMPLE OUTPUT:
     * "What are the main stages of photosynthesis and how do they convert 
     *  light energy into chemical energy? Explain the role of chloroplasts."
     */
    public void generateQuestionFromContext(Consumer<String> callback) {
        // Ensure model is ready before generating questions
        if (!isLlmReady || llmChatInference == null || llmChatSession == null) {
            callback.accept("LLM is not ready.");
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Use pre-configured quiz prompt optimized for education
                String prompt = quizPrompt;
                llmChatSession.addQueryChunk(prompt);
                
                // Generate question using Gemma-3n model
                String result = llmChatSession.generateResponse();
                
                // Return question on main thread for UI updates
                new android.os.Handler(context.getMainLooper()).post(() -> 
                    callback.accept(result != null ? result : "Could not generate question."));
                    
            } catch (Exception e) {
                Log.e(TAG, "Error generating question: " + e.getMessage(), e);
                new android.os.Handler(context.getMainLooper()).post(() -> 
                    callback.accept("Error generating question: " + e.getMessage()));
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
                String prompt = "Reformat this educational content into clear, structured markdown:\n\n" +
                        "1. Create a main title using #\n" +
                        "2. Use ## for main sections\n" +
                        "3. Use ### for subsections\n" +
                        "4. Make key terms and concepts **bold**\n" +
                        "5. Use bullet points (*) for lists\n" +
                        "6. Keep paragraphs short (2-3 sentences max)\n" +
                        "7. Organize information logically\n" +
                        "8. Make it easy to read and study\n\n" +
                        "Content to reformat:\n" + fileContent;
                String result = llmChatInference.generateResponse(prompt);
                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept(result));
            } catch (Exception e) {
                Log.e(TAG, "Error reformatting the lesson: " + e.getMessage(), e);
                new android.os.Handler(context.getMainLooper()).post(() -> callback.accept(null));
            }
        });
    }


    /**
     * Properly shuts down the Gemma-3n LLM and releases all resources
     * 
     * This method ensures clean disposal of:
     * - MediaPipe GenAI inference engine
     * - LLM session and conversation context
     * - Background thread executor
     * - GPU memory resources (if used)
     * 
     * IMPORTANT: Call this method when the LlmHelper is no longer needed
     * to prevent memory leaks and free GPU resources for other apps.
     * 
     * USAGE:
     * - In Activity.onDestroy()
     * - In ViewModel.onCleared() 
     * - When switching to a different model
     */
    public void close() {
        executorService.execute(() -> {
            // Close MediaPipe GenAI inference engine
            if (llmChatInference != null) {
                llmChatInference.close();
                llmChatInference = null;
            }
            
            // Close conversation session and free context memory
            if (llmChatSession != null) {
                llmChatSession.close();
                llmChatSession = null;
            }

            // Mark as no longer ready for operations
            isLlmReady = false;
            Log.d(TAG, "Gemma-3n LLM closed and resources released.");
        });
        
        // Shutdown background thread executor
        executorService.shutdown();
    }
}