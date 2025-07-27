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

import java.util.ArrayList;
import java.util.Locale;

// https://ai.google.dev/edge/mediapipe/solutions/genai/function_calling/android

public class ChatFragment extends Fragment {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private InteractionViewModel interactionViewModel;
    private RecyclerView chatRecyclerView;
    private ChatMessagesAdapter chatAdapter;
    private EditText inputEditText;
    private ImageButton sendOrRecordButton; // Changed
    private LottieAnimationView chatLoadingIndicator;

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;

    // Enum to manage button state
    private enum ButtonState {
        RECORD,
        SEND
    }
    private ButtonState currentButtonState = ButtonState.RECORD;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        chatRecyclerView = view.findViewById(R.id.chatRecyclerView);
        inputEditText = view.findViewById(R.id.inputEditText);
        sendOrRecordButton = view.findViewById(R.id.sendOrRecordButton); // Changed ID
        chatLoadingIndicator = view.findViewById(R.id.chatLoadingIndicator);

        chatAdapter = new ChatMessagesAdapter(new ArrayList<>());
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setAdapter(chatAdapter);

        setupSpeechRecognizer();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        interactionViewModel = new ViewModelProvider(requireActivity()).get(InteractionViewModel.class);

        observeViewModel();
        setupInputListeners();
        updateButtonUI(ButtonState.RECORD); // Initial state
    }

    private void setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
            speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true); // Get partial results for better UX

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d("SpeechRecognizer", "Ready for speech");
                    // You could change UI here, e.g., mic icon to glowing
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


    private void observeViewModel() {
        interactionViewModel.chatMessages.observe(getViewLifecycleOwner(), messages -> {
            chatAdapter.setMessages(messages);
            if (messages != null && messages.size() > 0) {
                chatRecyclerView.smoothScrollToPosition(messages.size() - 1);
            }
        });

        interactionViewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            if (interactionViewModel.interactionMode.getValue() == InteractionModePojo.CHAT) {
                // Keep the button enabled, but maybe change icon if loading a response
                sendOrRecordButton.setEnabled(!isLoading);
                inputEditText.setEnabled(!isLoading);
                if (isLoading) {
                    chatLoadingIndicator.playAnimation();
                } else {
                    chatLoadingIndicator.pauseAnimation();
                }
            }
        });
    }

    private void setupInputListeners() {
        inputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().isEmpty()) {
                    if (currentButtonState != ButtonState.RECORD && !isListening) {
                        updateButtonUI(ButtonState.RECORD);
                    }
                } else {
                    if (currentButtonState != ButtonState.SEND) {
                        updateButtonUI(ButtonState.SEND);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        sendOrRecordButton.setOnClickListener(v -> {
            if (currentButtonState == ButtonState.SEND) {
                String message = inputEditText.getText().toString().trim();
                sendMessage(message);
            } else { // RECORD state
                handleRecordButtonPress();
            }
        });

        // Optional: Use OnTouchListener for press-and-hold to record
        // This is a more common UX for voice input
//        sendOrRecordButton.setOnTouchListener((view, motionEvent) -> {
//            if (currentButtonState == ButtonState.RECORD) {
//                switch (motionEvent.getAction()) {
//                    case MotionEvent.ACTION_DOWN:
//                        if (!isListening) {
//                            startListening();
//                            sendOrRecordButton.setImageResource(R.drawable.ic_mic); // Can use a "listening" mic icon
//                            view.performClick(); // For accessibility
//                        }
//                        return true; // Consume event
//                    case MotionEvent.ACTION_UP:
//                    case MotionEvent.ACTION_CANCEL:
//                        if (isListening) {
//                            stopListening();
//                            sendOrRecordButton.setImageResource(R.drawable.ic_mic); // Back to normal mic
//                        }
//                        return true; // Consume event
//                }
//            }
//            return false; // Let onClickListener handle if it's SEND state or other cases
//        });
    }

    private void handleRecordButtonPress() {
        // This method is now primarily for the OnClickListener fallback
        // The OnTouchListener provides a better UX for tap-and-hold recording
        if (!isListening) {
            startListening();
        } else {
            stopListening();
        }
    }


    private void updateButtonUI(ButtonState state) {
        currentButtonState = state;
        if (state == ButtonState.RECORD) {
            sendOrRecordButton.setImageResource(R.drawable.ic_mic);
            sendOrRecordButton.setContentDescription("Record Audio");
        } else {
            sendOrRecordButton.setImageResource(R.drawable.ic_send);
            sendOrRecordButton.setContentDescription("Send Message");
        }
    }

    private void sendMessage(String message) {
        if (!message.isEmpty()) {
            interactionViewModel.sendChatMessage(message);
            inputEditText.setText("");
            // After sending, the TextWatcher will switch button back to RECORD
        }
    }

    private void startListening() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            if (speechRecognizer != null && !isListening) {
                inputEditText.setHint("Listening...");
                speechRecognizer.startListening(speechRecognizerIntent);
            }
        }
    }

    private void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            inputEditText.setHint("Type or record a message...");
            isListening = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening(); // Try starting again if permission was granted
            } else {
                Toast.makeText(getContext(), "Audio permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Helper for error messages from SpeechRecognizer
    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: message = "Audio recording error"; break;
            case SpeechRecognizer.ERROR_CLIENT: message = "Client side error"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Insufficient permissions"; break;
            case SpeechRecognizer.ERROR_NETWORK: message = "Network error"; break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Network timeout"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: message = "No match"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "RecognitionService busy"; break;
            case SpeechRecognizer.ERROR_SERVER: message = "error from server"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "No speech input"; break;
            default: message = "Didn't understand, please try again."; break;
        }
        return message;
    }


    @Override
    public void onDestroyView() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroyView();
    }
}