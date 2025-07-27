package com.gemma3n.smartlearning;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReceiveTranscriptActivity extends AppCompatActivity {

    private static final String TAG = "ReceiveTranscriptActivity";
    private EditText contentEditText;
    private EditText fileNameEditText;
    private Button saveButton;
    private Button cancelButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private String sharedContent;
    private String originalFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_transcript);

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Import Shared Content");
        }

        // Initialize views
        contentEditText = findViewById(R.id.contentEditText);
        fileNameEditText = findViewById(R.id.fileNameEditText);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);

        // Handle incoming intent
        handleIncomingIntent();

        // Setup button listeners
        setupButtonListeners();
    }

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("text/")) {
                handleSharedText(intent);
            } else if (type.equals("application/pdf") || 
                       type.equals("application/msword") || 
                       type.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                handleSharedFile(intent);
            }
        } else {
            // No valid shared content
            showError("No shared content found");
        }
    }

    private void handleSharedText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null && !sharedText.trim().isEmpty()) {
            sharedContent = sharedText;
            contentEditText.setText(sharedText);
            
            // Generate default filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            originalFileName = "shared_content_" + timestamp + ".txt";
            fileNameEditText.setText(originalFileName);
            
            showSuccess("Text content received successfully");
        } else {
            showError("No text content found");
        }
    }

    private void handleSharedFile(Intent intent) {
        Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (fileUri != null) {
            try {
                // Read file content
                sharedContent = readFileContent(fileUri);
                contentEditText.setText(sharedContent);
                
                // Extract original filename
                originalFileName = getFileNameFromUri(fileUri);
                if (originalFileName == null || originalFileName.isEmpty()) {
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                    originalFileName = "shared_file_" + timestamp + ".txt";
                }
                fileNameEditText.setText(originalFileName);
                
                showSuccess("File content imported successfully");
            } catch (IOException e) {
                Log.e(TAG, "Error reading shared file", e);
                showError("Error reading file: " + e.getMessage());
            }
        } else {
            showError("No file found");
        }
    }

    private String readFileContent(Uri uri) throws IOException {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        try {
            // Try to get filename from content resolver
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get filename from URI", e);
        }
        
        // Fallback: extract from URI path
        if (fileName == null || fileName.isEmpty()) {
            String path = uri.getPath();
            if (path != null) {
                fileName = new File(path).getName();
            }
        }
        
        return fileName;
    }

    private void setupButtonListeners() {
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSharedContent();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void saveSharedContent() {
        if (sharedContent == null || sharedContent.trim().isEmpty()) {
            showError("No content to save");
            return;
        }

        String fileName = fileNameEditText.getText().toString().trim();
        if (fileName.isEmpty()) {
            showError("Please enter a filename");
            return;
        }

        // Ensure filename has .txt extension
        if (!fileName.toLowerCase().endsWith(".txt")) {
            fileName += ".txt";
            fileNameEditText.setText(fileName);
        }

        showProgress("Saving content...");

        // Save to app's internal storage
        try {
            File internalDir = getFilesDir();
            File destinationDir = new File(internalDir, "imported_notes");
            if (!destinationDir.exists()) {
                destinationDir.mkdirs();
            }

            File file = new File(destinationDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStream os = fos) {
                os.write(sharedContent.getBytes());
            }

            showSuccess("Content saved successfully!");
            
            // Navigate to file list after a short delay
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(ReceiveTranscriptActivity.this, FileListActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }
            }, 1500);

        } catch (IOException e) {
            Log.e(TAG, "Error saving file", e);
            showError("Error saving file: " + e.getMessage());
        }
    }

    private void showProgress(String message) {
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
    }

    private void showSuccess(String message) {
        progressBar.setVisibility(View.GONE);
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
        saveButton.setEnabled(true);
        cancelButton.setEnabled(true);
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        statusText.setText("Error: " + message);
        statusText.setVisibility(View.VISIBLE);
        saveButton.setEnabled(false);
        cancelButton.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 