package com.gemma3n.smartlearning;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

public class InteractionActivity extends AppCompatActivity {

    private InteractionViewModel interactionViewModel;
    private String fileContent;
    private Button toggleModeButton;
    private ProgressBar llmLoadingIndicator;
    private TextView llmLoadingText;
    private LinearLayout llmLoadingContainer;


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
        // IMPORTANT: Initialize LLM helper here, passing the model path from assets
        interactionViewModel.initializeLlm("/data/local/tmp/llm/gemma-3n-E2B-it-int4.task"); // Replace with actual model name


        Log.d("InteractionActivity", "LLM Initialized");
        interactionViewModel.isLlmReady.observe(this, isReady -> {
            Log.d("InteractionActivity", "isLlmReady changed: " + isReady);
            if (isReady) {
                Log.d("InteractionActivity", "LLM Ready");
                llmLoadingIndicator.setVisibility(View.GONE);
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
                Log.d("InteractionActivity", "LLM not ready");
                // Check if the LLM is still loading or if there was an error
                // You might need a more specific state from LlmHelper/ViewModel for errors
//                if (interactionViewModel.isLoading.getValue() != null && !interactionViewModel.isLoading.getValue()){
//                    Log.d("InteractionActivity", "LLM Failed to Initialize");
//                    //This implies LLM setup might have failed if it's not loading and not ready
//                    llmLoadingIndicator.setVisibility(View.GONE);
//                    llmLoadingText.setText("LLM Failed to Initialize. Please check logs.");
//                    llmLoadingText.setVisibility(View.VISIBLE);
//                } else {
//                    Log.d("InteractionActivity", "LLM not ready yet");
//                    llmLoadingIndicator.setVisibility(View.VISIBLE);
//                    llmLoadingText.setText("Initializing LLM... This may take a moment.");
//                    llmLoadingText.setVisibility(View.VISIBLE);
//                    toggleModeButton.setVisibility(View.GONE);
//                }

                llmLoadingContainer.setVisibility(View.VISIBLE);
                llmLoadingIndicator.setVisibility(View.VISIBLE);
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