package com.gemma3n.smartlearning;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.ai.edge.localagents.rag.memory.ColumnConfig;
import com.google.ai.edge.localagents.rag.memory.VectorStoreRecord;
import com.google.ai.edge.localagents.rag.models.EmbedData;
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest;
import com.google.ai.edge.localagents.rag.models.GeckoEmbeddingModel;
import com.google.ai.edge.localagents.rag.memory.SqliteVectorStore;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class FileListActivity extends AppCompatActivity {

    private FileListViewModel fileListViewModel;
    private FileListAdapter fileListAdapter;
    private File selectedFile;
    private Toolbar toolbar;
    private SearchView searchView;
    private FloatingActionButton fabAddFile;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private String lessonDirectoryPath;
    private File destinationDir;
    private GeckoEmbeddingModel embeddingModel;
    private SqliteVectorStore vectorStore;
    private Executor backgroundExecutor;
    private static final String TAG = "FileListActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);

        toolbar = findViewById(R.id.toolbar_file_list);
        setSupportActionBar(toolbar);

        RecyclerView recyclerView = findViewById(R.id.fileRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fabAddFile = findViewById(R.id.fabAddFile);

        fileListAdapter = new FileListAdapter(file -> {
            // When a file is clicked
            fileListViewModel.readFileContent(file);
            selectedFile = file;
        });
        recyclerView.setAdapter(fileListAdapter);


        fileListViewModel = new ViewModelProvider(this).get(FileListViewModel.class);
        File internalDir = getFilesDir(); // Gets the root of your app's internal storage: /data/data/your.package.name/files
        destinationDir = new File(internalDir, "imported_notes");
        lessonDirectoryPath = destinationDir.getAbsolutePath();

        fileListViewModel.loadFiles(lessonDirectoryPath);

        fileListViewModel.files.observe(this, files -> {
            if (files != null) {
                fileListAdapter.setFiles(files);
            }
        });

        fileListViewModel.selectedFileContent.observe(this, content -> {
            if (content != null) {
                Intent intent = new Intent(FileListActivity.this, DisplayTextActivity.class);
                intent.putExtra(DisplayTextActivity.EXTRA_FILE_CONTENT, content);
                intent.putExtra(DisplayTextActivity.EXTRA_FILE_PATH, selectedFile != null ? selectedFile.getAbsolutePath() : null);
                startActivity(intent);
                fileListViewModel.clearSelectedFileContent(); // Clear after navigating
            }
        });

        fileListViewModel.error.observe(this, errorMsg -> {
            if (errorMsg != null) {
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                fileListViewModel.clearError();
            }
        });

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getData() != null) {
                            Uri selectedFileUri = data.getData();
                            Log.d(TAG, "File selected: " + selectedFileUri.toString());
                            handleSelectedFile(selectedFileUri);
                        } else {
                            Log.w(TAG, "File selection returned null data or URI.");
                            Toast.makeText(this, "Failed to get selected file.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(TAG, "File selection cancelled or failed. Result code: " + result.getResultCode());
                    }
                });

        fabAddFile.setOnClickListener(view -> openFilePicker());

        backgroundExecutor = Executors.newSingleThreadExecutor();

        String geckoModelPath = "/data/local/tmp/llm/Gecko_256_quant.tflite";
        String sentencePieceModelPath = "/data/local/tmp/llm/sentencepiece.model";
        embeddingModel = new GeckoEmbeddingModel(geckoModelPath, Optional.of(sentencePieceModelPath), true);

        // Initialize the SqliteVectorStore
        File dbFile = new File(getFilesDir(), "text_search.db");
        vectorStore = new SqliteVectorStore(768,
                dbFile.getAbsolutePath(),
                "text",
                "embeddings",
                SqliteVectorStore.DEFAULT_TABLE_CONFIG.toBuilder().addColumn(ColumnConfig.create("file_name", "TEXT")).build());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.file_list_menu, menu); // Inflate your menu

        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchItem.getActionView();

        if (searchView != null) {
            searchView.setQueryHint("Search files...");
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    EmbedData<String> embedData = EmbedData.create(query, EmbedData.TaskType.RETRIEVAL_QUERY);
                    EmbeddingRequest<String> embeddingRequest = EmbeddingRequest.create(Collections.singletonList(embedData));

                    ListenableFuture<ImmutableList<Float>> embeddingFuture =
                            embeddingModel.getEmbeddings(embeddingRequest);

                    backgroundExecutor.execute(() -> {
                        try {
                            ImmutableList<Float> embedding = embeddingFuture.get();
                            ImmutableList<VectorStoreRecord<String>> records =  vectorStore.getNearestRecords(embedding, 10, 0.7f);
                            List<String> filesList = new ArrayList<>();
                            for (VectorStoreRecord<String> record : records) {
                                Object file = record.getMetadata().get("file_name");
                                if (file != null && !filesList.contains(file.toString()))
                                    filesList.add(file.toString());
                            }
                            Log.d(TAG, "Search result files: " + filesList);
                            fileListViewModel.loadFiles(filesList);
                        } catch (InterruptedException | ExecutionException e) {
                            Log.e(TAG, "Error embedding or adding record: " + e.getMessage(), e);
                        }
                    });
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false;
                }
            });

            // This listener is called when the SearchView closes (e.g., after onQueryTextSubmit or onClose)
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    Log.d(TAG, "Collapse searchview ");
                    fileListViewModel.loadFiles(lessonDirectoryPath);
                    return true; // Return true to allow collapse action
                }

                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    Log.d(TAG, "Expand searchview ");
                    return true; // Return true to allow expand action
                }
            });

            // Set listener for the 'X' or collapse button within the SearchView
            searchView.setOnCloseListener(new SearchView.OnCloseListener() {
                @Override
                public boolean onClose() {
                    Log.d(TAG, "Close searchview ");
                    fileListViewModel.loadFiles(lessonDirectoryPath);
                    return true;
                }
            });

        } else {
            Log.e(TAG, "SearchView is null in onCreateOptionsMenu");
        }
        return true; // True to display the menu
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Set MIME types for .txt, .md, .text
        intent.setType("*/*"); // Start broad, then filter by specific types
        String[] mimeTypes = {"text/plain", "text/markdown"}; // Common MIME types
        // Note: ".text" doesn't have a standard MIME type, so text/plain is the closest.
        // For broad compatibility, some file managers might need "*/*" if they don't map .md or .text well.
        // If you strictly want only these extensions, you might need to filter after selection based on file name.
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        try {
            filePickerLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening file picker", e);
            Toast.makeText(this, "Cannot open file picker. Is a file manager app installed?", Toast.LENGTH_LONG).show();
        }
    }

    private void handleSelectedFile(Uri uri) {
        String fileName = getFileNameFromUri(uri);
        if (fileName == null || (!fileName.toLowerCase().endsWith(".txt") &&
                !fileName.toLowerCase().endsWith(".md") &&
                !fileName.toLowerCase().endsWith(".text"))) {
            Toast.makeText(this, "Invalid file type. Please select a .txt, .md, or .text file.", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Selected file is not of the allowed types: " + fileName);
            return;
        }

        if (destinationDir == null) {
            Toast.makeText(this, "Error accessing app storage directory.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to get external files directory 'imported_notes'.");
            return;
        }
        if (!destinationDir.exists()) {
            if (!destinationDir.mkdirs()) {
                Toast.makeText(this, "Error creating destination directory.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Failed to create directory: " + destinationDir.getAbsolutePath());
                return;
            }
        }

        File destinationFile = new File(destinationDir, fileName);

        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(destinationFile)) {

            if (inputStream == null) {
                throw new IOException("Unable to open input stream for URI: " + uri);
            }

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
            Log.i(TAG, "File copied successfully to: " + destinationFile.getAbsolutePath());
            Toast.makeText(this, fileName + " imported successfully!", Toast.LENGTH_SHORT).show();

//            String fileContent = readFileFromAssets(destinationFile.getAbsolutePath());

            String fileContent = readFileFromAssets(uri);
            List<String> textChunks = chunkText(fileContent, 100); // Chunk size of 100 words

            List<EmbedData<String>> embedDataList = new ArrayList<>();
            for (String chunk : textChunks) {
                EmbedData<String> embedData = EmbedData.create(chunk, EmbedData.TaskType.RETRIEVAL_DOCUMENT);
                embedDataList.add(embedData);
            }
            EmbeddingRequest<String> embeddingRequest =
                    EmbeddingRequest.create(embedDataList);

            ListenableFuture<ImmutableList<ImmutableList<Float>>> embeddingFuture =
                    embeddingModel.getBatchEmbeddings(embeddingRequest);

            backgroundExecutor.execute(() -> {
                try {
                    ImmutableList<ImmutableList<Float>> embeddings = embeddingFuture.get();

                    for (ImmutableList<Float> embedding : embeddings) {

                        // Build the metadata for the record
                        ImmutableMap<String, Object> metadata = ImmutableMap.of("file_name", destinationFile.getAbsolutePath());

                        // Create the Record to be stored in the VectorStore
                        VectorStoreRecord<String> vectorStoreRecord = VectorStoreRecord.create("", embedding, metadata);

                        vectorStore.insert(vectorStoreRecord);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    Log.e(TAG, "Error embedding or adding record: " + e.getMessage(), e);
                    // Consider adding more robust error handling, e.g., callback to UI
                }
            });

            fileListViewModel.loadFiles(lessonDirectoryPath);

        } catch (IOException e) {
            Log.e(TAG, "Error copying file", e);
            Toast.makeText(this, "Error importing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Helper method to get file name from content URI
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file name from URI", e);
            }
        }
        if (fileName == null) {
            fileName = uri.getLastPathSegment();
            // Basic sanitation if it's a path segment
            if (fileName != null) {
                fileName = new File(fileName).getName();
            }
        }
        return fileName;
    }

    private String readFileFromAssets(Uri uri) throws IOException {

        try (InputStream is = getContentResolver().openInputStream(uri)) {
//        InputStream is = getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private List<String> chunkText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder chunkBuilder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            chunkBuilder.append(words[i]).append(" ");
            if ((i + 1) % chunkSize == 0 || i == words.length - 1) {
                chunks.add(chunkBuilder.toString().trim());
                chunkBuilder = new StringBuilder();
            }
        }
        return chunks;
    }
}