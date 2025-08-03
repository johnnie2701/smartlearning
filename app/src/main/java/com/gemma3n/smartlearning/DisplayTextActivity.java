package com.gemma3n.smartlearning;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;
import io.noties.markwon.Markwon;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class DisplayTextActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_CONTENT = "com.gemma3n.smartlearning.FILE_CONTENT";
    public static final String EXTRA_FILE_PATH = "com.gemma3n.smartlearning.FILE_PATH";

    private TextView textContentTextView;
    private Button startChatButton;
    private Button actionButton;
    private LinearLayout llmLoadingContainer;
    private LottieAnimationView reformatLoadingIndicator;
    private TextView loadingMessageText;
    private String fileContent;
    private InteractionViewModel interactionViewModel;
    private String filePath;
    private Markwon markwon;

    private enum ButtonState {
        READY_TO_REFORMAT,
        READY_TO_SAVE
    }
    private ButtonState currentButtonState = ButtonState.READY_TO_REFORMAT;
    private String contentToSave;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_text);

        Window window = getWindow();
        WindowInsetsController insetsController = window.getInsetsController();
        if (insetsController != null) {
            // APPEARANCE_LIGHT_STATUS_BARS requests dark icons for a light status bar
            insetsController.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, // Value to set
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS  // Mask
            );
        }

        textContentTextView = findViewById(R.id.textContentTextView);
        startChatButton = findViewById(R.id.startChatButton);
        actionButton = findViewById(R.id.reformatButton);
        llmLoadingContainer = findViewById(R.id.llmLoadingContainer);
        reformatLoadingIndicator = findViewById(R.id.reformatLoadingIndicator);
        loadingMessageText = findViewById(R.id.loadingMessageText);

        // Initialize Markwon for markdown rendering
        markwon = Markwon.create(this);

        interactionViewModel = new ViewModelProvider(this).get(InteractionViewModel.class);

        Intent intent = getIntent();
        if  (intent != null) {
            if (intent.hasExtra(EXTRA_FILE_CONTENT)) {
                fileContent = intent.getStringExtra(EXTRA_FILE_CONTENT);
                // Render markdown content
                markwon.setMarkdown(textContentTextView, fileContent);
            } else {
                Toast.makeText(this, "No file content provided.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (intent.hasExtra(EXTRA_FILE_PATH)) {
                filePath = intent.getStringExtra(EXTRA_FILE_PATH);
            } else {
                Toast.makeText(this, "File path not provided. Saving will be disabled.", Toast.LENGTH_LONG).show();
                actionButton.setEnabled(false);
                actionButton.setAlpha(1.0f);
                actionButton.setTextColor(getResources().getColor(R.color.black));
            }
        } else {
            Toast.makeText(this, "Error: Intent is null.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        updateButtonAppearance();

        // Set OnClickListeners for the buttons (no action for now)
        startChatButton.setOnClickListener(v -> {

            Intent intentInteractionActivity = new Intent(DisplayTextActivity.this, InteractionActivity.class);
            intentInteractionActivity.putExtra(DisplayTextActivity.EXTRA_FILE_CONTENT, fileContent);
            startActivity(intentInteractionActivity);
        });

        actionButton.setOnClickListener(v -> {
            if (currentButtonState == ButtonState.READY_TO_REFORMAT) {
                Log.d("DisplayTextActivity", "Reformat action initiated.");
                if (fileContent != null && !fileContent.isEmpty()) {
                    // Show initial message about reformat duration
                    Toast.makeText(this, "Starting reformat... This may take a few minutes.", Toast.LENGTH_LONG).show();
                    
                    interactionViewModel.initializeLlm("/data/local/tmp/llm/gemma-3n-E2B-it-int4.task");

                    interactionViewModel.isLlmReady.observe(this, isReady -> {
                        Log.d("DisplayTextActivity", "isLlmReady changed: " + isReady);
                        if (isReady) {
                            interactionViewModel.reformatLesson(fileContent);
                        } else {
                            llmLoadingContainer.setVisibility(View.VISIBLE);
                            reformatLoadingIndicator.playAnimation();
                        }
                    });
                } else {
                    Toast.makeText(this, "No content to reformat.", Toast.LENGTH_SHORT).show();
                }
            } else if (currentButtonState == ButtonState.READY_TO_SAVE) {
                Log.d("DisplayTextActivity", "Save action initiated.");
                if (contentToSave != null && filePath != null) {
                    saveContentToFile(contentToSave, filePath);
                } else if (filePath == null) {
                    Toast.makeText(this, "Cannot save: Original file path is missing.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "No reformatted content to save.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        interactionViewModel.isLoading.observe(this, isLoading -> {
            Log.d("DisplayTextActivity", "isLoading: " + isLoading);
            if (isLoading) {
                llmLoadingContainer.setVisibility(View.VISIBLE);
                reformatLoadingIndicator.playAnimation();
            } else {
                llmLoadingContainer.setVisibility(View.GONE);
                reformatLoadingIndicator.pauseAnimation();
            }
        });

        interactionViewModel.reformattedLesson.observe(this, reformattedLesson -> {
            if (reformattedLesson != null && !reformattedLesson.isEmpty()) {
                Log.d("DisplayTextActivity", "reformattedLesson received");
                fileContent = reformattedLesson;
                if (textContentTextView != null) {
                    // Render markdown content
                    markwon.setMarkdown(textContentTextView, reformattedLesson);
                }
                contentToSave = reformattedLesson;
                currentButtonState = ButtonState.READY_TO_SAVE;
                updateButtonAppearance();
                Toast.makeText(this, "Reformatting complete. Ready to save.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateButtonAppearance() {
        if (actionButton == null) return;
        if (currentButtonState == ButtonState.READY_TO_REFORMAT) {
            actionButton.setText("Reformat");
        } else if (currentButtonState == ButtonState.READY_TO_SAVE) {
            actionButton.setText("Save");
        }
    }

    private void saveContentToFile(String content, String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "Error: File path is invalid for saving.", Toast.LENGTH_LONG).show();
            Log.e("DisplayTextActivity", "Save attempt with invalid file path.");
            return;
        }

        File file = new File(filePath);
        actionButton.setEnabled(false); // Disable button during save operation

        // Consider running file I/O on a background thread if files can be large
        // For simplicity here, it's on the main thread. For large files, use an Executor or Coroutine.
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos)) {
            osw.write(content);
            Toast.makeText(this, "Content saved successfully to " + file.getName(), Toast.LENGTH_LONG).show();
            Log.i("DisplayTextActivity", "Content saved to: " + filePath);

            // After saving, decide the next state
            fileContent = content; // The saved content is now the current content
            contentToSave = null; // Clear content staged for saving
            currentButtonState = ButtonState.READY_TO_REFORMAT; // Reset to reformat
            updateButtonAppearance();

        } catch (IOException e) {
            Log.e("DisplayTextActivity", "Error saving file: " + e.getMessage(), e);
            Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            actionButton.setEnabled(true); // Re-enable button
        }
    }
}