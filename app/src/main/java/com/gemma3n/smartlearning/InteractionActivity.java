package com.gemma3n.smartlearning;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

public class InteractionActivity extends AppCompatActivity {

    private InteractionViewModel interactionViewModel;
    private String fileContent;
    private Button toggleModeButton;
    private LottieAnimationView llmLoadingIndicator;
    private TextView llmLoadingText;
    private LinearLayout llmLoadingContainer;
    private static final String TAG = "InteractionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interaction); // Your main interaction layout

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toggleModeButton = findViewById(R.id.toggleModeButton);
        llmLoadingIndicator = findViewById(R.id.llmLoadingIndicator);
        llmLoadingText = findViewById(R.id.llmLoadingText);
        llmLoadingContainer = findViewById(R.id.llmLoadingContainer);

        fileContent = getIntent().getStringExtra(DisplayTextActivity.EXTRA_FILE_CONTENT);
        if (fileContent == null) {
            Toast.makeText(this, "Error: No file content provided.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        interactionViewModel = new ViewModelProvider(this).get(InteractionViewModel.class);

        //TODO: Replace adapter_model.safetensors with a flatbuffer converted file, the current version of mediapipe converter don't support gemma-3n
        interactionViewModel.initializeLlm("/data/local/tmp/llm/gemma-3n-E2B-it-int4.task", "/data/local/tmp/llm/adapter_model.safetensors");


        Log.d(TAG, "LLM Initialized");
        interactionViewModel.isLlmReady.observe(this, isReady -> {
            Log.d(TAG, "isLlmReady changed: " + isReady);
            if (isReady) {
                Log.d(TAG, "LLM Ready");
                llmLoadingIndicator.pauseAnimation();
                llmLoadingText.setVisibility(View.GONE);
                llmLoadingContainer.setVisibility(View.GONE);
                toggleModeButton.setVisibility(View.VISIBLE);
                interactionViewModel.setFileContext(fileContent); // Set context once LLM is ready
                // Load initial fragment (e.g., Chat)
                if (savedInstanceState == null) { // Load only if not restoring from a previous state
                    if (interactionViewModel.interactionMode.getValue() == InteractionModePojo.CHAT) {
                        loadFragment(new ChatFragment());
                    } else {
                        loadFragment(new QuizFragment());
                    }
                }
            } else {
                Log.d(TAG, "LLM not ready");
                llmLoadingContainer.setVisibility(View.VISIBLE);
                llmLoadingIndicator.playAnimation();
                llmLoadingText.setText("Initializing LLM... This may take a moment.");
                llmLoadingText.setVisibility(View.VISIBLE);
                toggleModeButton.setVisibility(View.GONE);
            }
        });


        interactionViewModel.interactionMode.observe(this, mode -> {
            if (Boolean.TRUE.equals(interactionViewModel.isLlmReady.getValue())) { // Only switch if LLM is ready
                if (mode == InteractionModePojo.CHAT) {
                    loadFragment(new ChatFragment()); // Create ChatFragment.java
                    toggleModeButton.setText("Switch to Quiz");
                } else {
                    loadFragment(new QuizFragment()); // Create QuizFragment.java
                    toggleModeButton.setText("Switch to Chat");
                }
            }
        });

        toggleModeButton.setOnClickListener(v -> interactionViewModel.toggleMode());

        // Initial setup for loading UI
        if (interactionViewModel.isLlmReady.getValue() == null || !interactionViewModel.isLlmReady.getValue()) {
            llmLoadingContainer.setVisibility(View.VISIBLE);
            llmLoadingIndicator.setVisibility(View.VISIBLE);
            llmLoadingText.setVisibility(View.VISIBLE);
            toggleModeButton.setVisibility(View.GONE);
        }
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        // transaction.addToBackStack(null); // Optional: if you want back navigation for fragments
        transaction.commit();
    }
}