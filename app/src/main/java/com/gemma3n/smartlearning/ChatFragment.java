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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton; // Changed from Button
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import com.airbnb.lottie.LottieAnimationView;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.noties.markwon.Markwon;

import java.util.ArrayList;
import java.util.Locale;

/**
 * ChatFragment - Interactive Educational Chat Interface with Gemma 3n Integration
 * 
 * This fragment provides a comprehensive chat experience for students to interact
 * with educational content using Google's Gemma-3n model through MediaPipe GenAI.
 * 
 * KEY FEATURES:
 * - Real-time chat with AI teacher persona powered by Gemma-3n
 * - Voice-to-text input using Android Speech Recognition
 * - Markdown rendering for formatted AI responses 
 * - Context-aware conversations based on uploaded lesson content
 * - Adaptive UI with dynamic button states (record/send)
 * - Loading animations for better user experience
 * 
 * GEMMA 3N INTEGRATION:
 * - Connects to LlmHelper for Gemma-3n model interactions
 * - Maintains conversation context throughout session
 * - Optimized prompts for educational responses
 * - Real-time streaming responses with markdown formatting
 * 
 * SPEECH RECOGNITION FEATURES:
 * - Android SpeechRecognizer with partial results
 * - Automatic permission handling for RECORD_AUDIO
 * - Multilingual support based on device locale
 * - Visual feedback during voice input
 * - Error handling for recognition failures
 * 
 * UI ARCHITECTURE:
 * - MVVM pattern with InteractionViewModel
 * - RecyclerView with custom ChatMessagesAdapter
 * - Reactive UI updates through LiveData observation
 * - Material Design with Lottie animations
 * 
 * USAGE FLOW:
 * 1. Student loads educational content in previous activity
 * 2. Fragment initializes with lesson context set in ViewModel
 * 3. Student asks questions via text input or voice recording
 * 4. Gemma-3n generates context-aware educational responses
 * 5. Responses displayed with markdown formatting in chat bubbles
 * 
 * TECHNICAL IMPLEMENTATION:
 * - Fragment lifecycle management for speech recognizer
 * - Background thread operations for AI inference
 * - Main thread UI updates through LiveData observers
 * - Memory-efficient RecyclerView with view recycling
 * - Proper resource cleanup on fragment destruction
 * 
 * PERMISSIONS REQUIRED:
 * - android.permission.RECORD_AUDIO (for voice input)
 * 
 * DEPENDENCIES:
 * - InteractionViewModel (Gemma-3n model management)
 * - ChatMessagesAdapter (conversation display)
 * - Markwon library (markdown rendering)
 * - Lottie animations (loading indicators)
 * 
 * @see InteractionViewModel for Gemma-3n model interactions
 * @see LlmHelper for core AI functionality
 * @see ChatMessagesAdapter for conversation UI
 */
public class ChatFragment extends Fragment {

    // Permission request code for audio recording functionality
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    
    // MVVM Architecture Components
    // ViewModel managing Gemma-3n interactions and chat state
    private InteractionViewModel interactionViewModel;
    
    // UI Components for Chat Interface
    // RecyclerView displaying conversation history with AI
    private RecyclerView chatRecyclerView;
    
    // Custom adapter for rendering user/AI message bubbles
    private ChatMessagesAdapter chatAdapter;
    
    // Text input field for typing questions or viewing speech results
    private EditText inputEditText;
    
    // Dynamic button that switches between microphone (record) and send icons
    private ImageButton sendOrRecordButton;
    
    // Lottie animation for visual feedback during AI response generation
    private LottieAnimationView chatLoadingIndicator;

    // Speech Recognition Components
    // Android SpeechRecognizer for voice-to-text conversion
    private SpeechRecognizer speechRecognizer;
    
    // Intent configured for educational speech recognition
    private Intent speechRecognizerIntent;
    
    // Flag tracking current speech recognition state
    private boolean isListening = false;
    
    // Markdown processor for rendering formatted AI responses from Gemma-3n
    // Handles bold text, lists, headers, and other educational formatting
    private Markwon markwon;

    /**
     * Button state enum for adaptive UI behavior
     * - RECORD: Shows microphone icon, enables voice input
     * - SEND: Shows send icon, enables text message sending
     */
    private enum ButtonState {
        RECORD,  // Microphone mode for voice input
        SEND     // Send mode for text message transmission
    }
    
    // Current button state, automatically switches based on text input presence
    private ButtonState currentButtonState = ButtonState.RECORD;


    /**
     * Initializes the chat interface UI components and sets up the conversation view
     * 
     * SETUP PROCESS:
     * 1. Inflates fragment_chat.xml layout
     * 2. Initializes RecyclerView with optimized LinearLayoutManager
     * 3. Sets up ChatMessagesAdapter for conversation display
     * 4. Configures Markwon for rendering Gemma-3n's formatted responses
     * 5. Initializes speech recognition for voice input
     * 
     * OPTIMIZATION FEATURES:
     * - setStackFromEnd(true) for automatic scrolling to latest messages
     * - Empty ArrayList initialization for immediate adapter attachment
     * - Context-specific Markwon configuration for educational content
     * 
     * @param inflater Layout inflater for creating views
     * @param container Parent container for the fragment
     * @param savedInstanceState Previous state (unused in this implementation)
     * @return Configured view ready for educational chat interactions
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the chat interface layout
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        // Initialize core UI components for chat functionality
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView);
        inputEditText = view.findViewById(R.id.inputEditText);
        sendOrRecordButton = view.findViewById(R.id.sendOrRecordButton);
        chatLoadingIndicator = view.findViewById(R.id.chatLoadingIndicator);

        // Configure RecyclerView for optimal chat experience
        chatAdapter = new ChatMessagesAdapter(new ArrayList<>());
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);  // Auto-scroll to newest messages
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setAdapter(chatAdapter);

        // Initialize Markwon for rendering Gemma-3n's formatted responses
        // Supports bold text, lists, headers for educational content
        markwon = Markwon.create(requireContext());
        chatAdapter.setMarkwon(markwon);

        // Set up speech recognition for voice input capabilities
        setupSpeechRecognizer();
        return view;
    }

    /**
     * Completes fragment initialization and establishes Gemma-3n connectivity
     * 
     * This method is called after onCreateView and handles:
     * 1. ViewModel connection for Gemma-3n model access
     * 2. LiveData observers for reactive UI updates
     * 3. Input event listeners for user interaction
     * 4. Initial UI state configuration
     * 
     * MVVM ARCHITECTURE:
     * - Gets shared ViewModel from parent Activity for model consistency
     * - Establishes observers for chat messages, loading states
     * - Connects UI interactions to ViewModel commands
     * 
     * @param view The view returned by onCreateView
     * @param savedInstanceState Previous state (unused)
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Get shared ViewModel containing Gemma-3n model and chat state
        // Activity scope ensures consistency across fragments
        interactionViewModel = new ViewModelProvider(requireActivity()).get(InteractionViewModel.class);

        // Set up reactive UI updates through LiveData observation
        observeViewModel();
        
        // Configure user input handlers for text and voice
        setupInputListeners();
        
        // Set initial UI state to microphone (record) mode
        updateButtonUI(ButtonState.RECORD);
    }

    /**
     * Configures Android Speech Recognition for educational voice input
     * 
     * SPEECH RECOGNITION SETUP:
     * - Free-form language model for natural educational questions
     * - Device locale for optimal recognition accuracy
     * - Partial results enabled for real-time feedback
     * - Comprehensive error handling for recognition failures
     * 
     * EDUCATIONAL OPTIMIZATIONS:
     * - Natural language model better for learning conversations
     * - Partial results provide immediate visual feedback
     * - Automatic text insertion into input field
     * - Seamless integration with send functionality
     * 
     * FALLBACK BEHAVIOR:
     * - Disables voice button if recognition unavailable
     * - Shows user-friendly error messages
     * - Graceful degradation to text-only input
     */
    private void setupSpeechRecognizer() {
        // Check if speech recognition is available on this device
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            // Create speech recognizer instance
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
            
            // Configure recognition intent for educational conversations
            speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);  // Natural language for education
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, 
                Locale.getDefault());  // Use device locale for accuracy
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, 
                true);  // Enable real-time transcription feedback

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d("SpeechRecognizer", "Ready for speech");
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d("SpeechRecognizer", "Beginning of speech");
                    isListening = true;
                }

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    Log.d("SpeechRecognizer", "End of speech");
                    isListening = false;
                    // Change UI back if needed
                    sendOrRecordButton.setImageResource(R.drawable.ic_mic); // Reset icon
                }

                @Override
                public void onError(int error) {
                    Log.e("SpeechRecognizer", "Error: " + getErrorText(error));
                    isListening = false;
                    sendOrRecordButton.setImageResource(R.drawable.ic_mic);
                    // Handle common errors like no speech, network issues, etc.
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(getContext(), "No speech input", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Speech recognition error", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String recognizedText = matches.get(0);
                        inputEditText.setText(recognizedText); // Put recognized text in EditText
                        inputEditText.setSelection(recognizedText.length()); // Move cursor to end
                        // No need to send here, the TextWatcher will change button to SEND
                        // and user can then press send. Or you can send directly:
                         sendMessage(recognizedText);
                    }
                    isListening = false;
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String partialText = matches.get(0);
                        inputEditText.setText(partialText);
                        inputEditText.setSelection(partialText.length());
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        } else {
            Toast.makeText(getContext(), "Speech recognition not available", Toast.LENGTH_LONG).show();
            sendOrRecordButton.setEnabled(false); // Or hide it
        }
    }


    /**
     * Establishes reactive UI updates through LiveData observation
     * 
     * This method sets up observers for key ViewModel data that drives the chat interface:
     * 
     * CHAT MESSAGES OBSERVER:
     * - Monitors conversation history from Gemma-3n interactions
     * - Updates RecyclerView with new user/AI message exchanges
     * - Auto-scrolls to latest message for optimal UX
     * - Handles both text and markdown-formatted AI responses
     * 
     * LOADING STATE OBSERVER:
     * - Tracks Gemma-3n inference operations in progress
     * - Disables input controls during AI response generation
     * - Provides visual feedback through button/input states
     * - Prevents user spam while model is processing
     * 
     * MVVM BENEFITS:
     * - Automatic UI synchronization with model state
     * - Lifecycle-aware observers prevent memory leaks
     * - Separation of concerns between UI and business logic
     * - Reactive programming patterns for smooth UX
     */
    private void observeViewModel() {
        // Observer for chat message updates from Gemma-3n conversations
        interactionViewModel.chatMessages.observe(getViewLifecycleOwner(), messages -> {
            // Update conversation display with latest messages
            chatAdapter.setMessages(messages);
            
            // Auto-scroll to newest message for natural chat flow
            if (messages != null && messages.size() > 0) {
                chatRecyclerView.smoothScrollToPosition(messages.size() - 1);
            }
        });

        // Observer for Gemma-3n inference loading states
        interactionViewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            Log.d("ChatFragment", "Gemma-3n loading state: " + isLoading + 
                  ", Mode: " + interactionViewModel.interactionMode.getValue());
                  
            // Only respond to loading states when in chat mode
            if (interactionViewModel.interactionMode.getValue() == InteractionModePojo.CHAT) {
                // Disable input controls during AI inference to prevent conflicts
                sendOrRecordButton.setEnabled(!isLoading);
                inputEditText.setEnabled(!isLoading);
                
                Log.d("ChatFragment", "Input controls " + 
                      (isLoading ? "disabled for Gemma-3n processing" : "enabled for user input"));
            } else {
                Log.d("ChatFragment", "Not in CHAT mode, ignoring loading state");
            }
        });
    }

    /**
     * Configures input field and button event listeners for adaptive UI behavior
     * 
     * INPUT FIELD MONITORING:
     * - TextWatcher tracks typing activity in real-time
     * - Empty input → microphone icon (record mode)
     * - Text present → send arrow icon (send mode)
     * - Smooth transitions preserve user experience
     * 
     * BUTTON BEHAVIOR:
     * - Single button with dual functionality
     * - SEND mode: Submits typed message to Gemma-3n
     * - RECORD mode: Activates speech recognition
     * - Mode automatically determined by input field state
     * 
     * USER EXPERIENCE OPTIMIZATIONS:
     * - Instant visual feedback on text entry
     * - No mode switching confusion
     * - Consistent interaction patterns
     * - Speech recognition integrates seamlessly with text input
     */
    private void setupInputListeners() {
        // Monitor text input changes to update button state automatically
        inputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Switch to microphone when text field is empty
                if (s.toString().trim().isEmpty()) {
                    if (currentButtonState != ButtonState.RECORD && !isListening) {
                        updateButtonUI(ButtonState.RECORD);
                    }
                } else {
                    // Switch to send button when text is present
                    if (currentButtonState != ButtonState.SEND) {
                        updateButtonUI(ButtonState.SEND);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Configure primary action button with dual functionality
        sendOrRecordButton.setOnClickListener(v -> {
            if (currentButtonState == ButtonState.SEND) {
                // Send typed message to Gemma-3n for processing
                String message = inputEditText.getText().toString().trim();
                sendMessage(message);
            } else { // RECORD state
                // Activate speech recognition for voice input
                handleRecordButtonPress();
            }
        });
    }

    /**
     * Handles microphone button press events for speech recognition control
     * 
     * This method provides toggle functionality for voice input:
     * - Start listening if currently stopped
     * - Stop listening if currently active
     * 
     * SPEECH RECOGNITION WORKFLOW:
     * 1. Check current listening state
     * 2. Toggle between start/stop appropriately
     * 3. Update UI feedback for user awareness
     * 4. Handle permission requests if needed
     * 
     * EDUCATIONAL USE CASE:
     * - Enables hands-free questioning during study
     * - Supports multilingual voice input
     * - Integrates seamlessly with Gemma-3n processing
     */
    private void handleRecordButtonPress() {
        // Toggle speech recognition based on current state
        if (!isListening) {
            // Begin voice input session
            startListening();
        } else {
            // End current voice input session
            stopListening();
        }
    }


    /**
     * Updates button visual appearance and accessibility based on current mode
     * 
     * This method synchronizes button UI with the current interaction state:
     * 
     * RECORD MODE:
     * - Microphone icon indicates voice input capability
     * - Content description for screen readers: "Record Audio"
     * - Visual cue for speech recognition availability
     * 
     * SEND MODE:
     * - Send arrow icon indicates message transmission
     * - Content description for screen readers: "Send Message"
     * - Clear indication that typed text will be sent
     * 
     * ACCESSIBILITY FEATURES:
     * - Proper content descriptions for screen readers
     * - Clear visual distinction between modes
     * - Consistent icon usage throughout app
     * 
     * @param state The new button state (RECORD or SEND)
     */
    private void updateButtonUI(ButtonState state) {
        // Store the new state for future reference
        currentButtonState = state;
        
        if (state == ButtonState.RECORD) {
            // Configure for voice input mode
            sendOrRecordButton.setImageResource(R.drawable.ic_mic);
            sendOrRecordButton.setContentDescription("Record Audio");
        } else {
            // Configure for text message sending mode
            sendOrRecordButton.setImageResource(R.drawable.ic_send);
            sendOrRecordButton.setContentDescription("Send Message");
        }
    }

    /**
     * Sends user message to Gemma-3n for educational response generation
     * 
     * This method handles the core communication with the AI model:
     * 
     * MESSAGE PROCESSING FLOW:
     * 1. Validates message is not empty
     * 2. Passes message to InteractionViewModel for Gemma-3n processing
     * 3. Clears input field for next interaction
     * 4. Automatically switches button back to microphone mode
     * 
     * GEMMA-3N INTEGRATION:
     * - Message sent with lesson context for relevant responses
     * - AI generates educational explanations and guidance
     * - Response displayed in chat bubble format
     * - Maintains conversation history for context
     * 
     * UI STATE MANAGEMENT:
     * - Input field cleared after sending
     * - TextWatcher automatically switches to RECORD mode
     * - Loading indicators activated during AI processing
     * 
     * @param message User's question or comment about the lesson content
     */
    private void sendMessage(String message) {
        // Validate message content before sending
        if (!message.isEmpty()) {
            // Send message to Gemma-3n through ViewModel
            interactionViewModel.sendChatMessage(message);
            
            // Clear input field for next interaction
            inputEditText.setText("");
            
            // Note: TextWatcher will automatically switch button back to RECORD mode
        }
    }

    /**
     * Initiates speech recognition session for voice-to-text conversion
     * 
     * PERMISSION HANDLING:
     * - Checks for RECORD_AUDIO permission before starting
     * - Requests permission if not already granted
     * - Graceful fallback if permission denied
     * 
     * RECOGNITION ACTIVATION:
     * - Starts Android SpeechRecognizer with educational optimizations
     * - Updates UI hint to show "Listening..." status
     * - Prevents multiple simultaneous recognition sessions
     * 
     * EDUCATIONAL BENEFITS:
     * - Enables hands-free questioning during study
     * - Supports natural language queries to Gemma-3n
     * - Real-time transcription with partial results
     * - Automatic integration with send functionality
     * 
     * ERROR PREVENTION:
     * - Validates speechRecognizer availability
     * - Checks current listening state to prevent conflicts
     * - Handles permission edge cases gracefully
     */
    private void startListening() {
        // Check and request audio recording permission if needed
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Request permission from user
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            // Permission granted, start speech recognition
            if (speechRecognizer != null && !isListening) {
                // Update UI to show listening state
                inputEditText.setHint("Listening...");
                
                // Begin speech recognition with configured intent
                speechRecognizer.startListening(speechRecognizerIntent);
            }
        }
    }

    /**
     * Terminates active speech recognition session and restores UI state
     * 
     * RECOGNITION TERMINATION:
     * - Safely stops current SpeechRecognizer session
     * - Prevents resource leaks from abandoned recognition
     * - Updates listening state flag for accurate tracking
     * 
     * UI STATE RESTORATION:
     * - Restores default input field hint text
     * - Clears "Listening..." status indicator
     * - Returns interface to ready state for next interaction
     * 
     * RESOURCE MANAGEMENT:
     * - Properly releases speech recognition resources
     * - Prevents battery drain from active microphone
     * - Ensures clean state for subsequent recognition attempts
     * 
     * USAGE SCENARIOS:
     * - User manually stops recording
     * - Recognition error requires restart
     * - Fragment lifecycle events (pause, destroy)
     */
    private void stopListening() {
        // Validate recognition session is active before stopping
        if (speechRecognizer != null && isListening) {
            // Terminate current speech recognition session
            speechRecognizer.stopListening();
            
            // Restore default UI state
            inputEditText.setHint("Type or record a message...");
            
            // Update state tracking
            isListening = false;
        }
    }

    /**
     * Handles user response to audio recording permission request
     * 
     * This callback processes the result of RECORD_AUDIO permission requests
     * initiated by the speech recognition functionality.
     * 
     * PERMISSION GRANTED:
     * - Automatically attempts to start speech recognition
     * - Seamless user experience without additional taps
     * - Enables voice input for educational interactions
     * 
     * PERMISSION DENIED:
     * - Shows user-friendly error message
     * - Graceful degradation to text-only input
     * - App remains fully functional without voice features
     * 
     * EDUCATIONAL IMPACT:
     * - Voice input enhances accessibility for learning
     * - Supports hands-free study sessions
     * - Accommodates different learning preferences
     * 
     * @param requestCode The permission request identifier
     * @param permissions Array of requested permissions
     * @param grantResults Results for each requested permission
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        // Handle audio recording permission result
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted - start speech recognition automatically
                startListening();
            } else {
                // Permission denied - inform user and continue with text input
                Toast.makeText(getContext(), "Audio permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Converts Android SpeechRecognizer error codes to user-friendly messages
     * 
     * This utility method provides meaningful error descriptions for various
     * speech recognition failures that may occur during educational interactions.
     * 
     * ERROR CATEGORIES:
     * - Hardware issues (audio recording, microphone)
     * - Network problems (connectivity, timeouts)
     * - Recognition failures (no speech, no match)
     * - System issues (busy service, insufficient permissions)
     * 
     * EDUCATIONAL CONTEXT:
     * - Clear error messages help students understand issues
     * - Encourages retry attempts for better learning experience
     * - Maintains user confidence in voice input feature
     * 
     * USER EXPERIENCE:
     * - Friendly language instead of technical error codes
     * - Actionable feedback when possible
     * - Consistent messaging across recognition errors
     * 
     * @param errorCode Android SpeechRecognizer error constant
     * @return Human-readable error message for display to user
     */
    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: 
                message = "Audio recording error"; break;
            case SpeechRecognizer.ERROR_CLIENT: 
                message = "Client side error"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: 
                message = "Insufficient permissions"; break;
            case SpeechRecognizer.ERROR_NETWORK: 
                message = "Network error"; break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: 
                message = "Network timeout"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: 
                message = "No match"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: 
                message = "RecognitionService busy"; break;
            case SpeechRecognizer.ERROR_SERVER: 
                message = "error from server"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: 
                message = "No speech input"; break;
            default: 
                message = "Didn't understand, please try again."; break;
        }
        return message;
    }


    /**
     * Performs cleanup when fragment view is destroyed
     * 
     * This lifecycle method ensures proper resource disposal when the chat
     * interface is no longer needed, preventing memory leaks and battery drain.
     * 
     * SPEECH RECOGNITION CLEANUP:
     * - Destroys SpeechRecognizer instance
     * - Releases microphone resources
     * - Cancels any pending recognition operations
     * - Frees system audio recording resources
     * 
     * RESOURCE MANAGEMENT:
     * - Prevents memory leaks from abandoned speech recognizer
     * - Stops background audio processing
     * - Releases Android system resources properly
     * - Ensures clean state for future fragment instances
     * 
     * LIFECYCLE INTEGRATION:
     * - Called automatically during fragment destruction
     * - Handles configuration changes (rotation, etc.)
     * - Manages activity pausing and resuming
     * - Coordinates with Android memory management
     * 
     * PERFORMANCE BENEFITS:
     * - Reduces memory footprint when chat not active
     * - Improves battery life by stopping audio processing
     * - Prevents resource conflicts with other apps
     */
    @Override
    public void onDestroyView() {
        // Clean up speech recognition resources
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        
        // Call superclass cleanup
        super.onDestroyView();
    }
}