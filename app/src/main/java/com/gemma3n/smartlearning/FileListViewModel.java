package com.gemma3n.smartlearning;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileListViewModel extends AndroidViewModel {

    private final MutableLiveData<List<File>> _files = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<File>> files = _files;

    private final MutableLiveData<String> _selectedFileContent = new MutableLiveData<>(null);
    public LiveData<String> selectedFileContent = _selectedFileContent;

    private final MutableLiveData<String> _error = new MutableLiveData<>(null);
    public LiveData<String> error = _error;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public FileListViewModel(@NonNull Application application) {
        super(application);
    }
    
    private final String TAG = "FileListViewModel";

    public void loadFiles(String directoryPath) {
        executorService.execute(() -> {
            try {
                File directory = new File(directoryPath);
                if (directory.exists() && directory.isDirectory()) {
                    File[] foundFiles = directory.listFiles((dir, name) ->
                            name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".text"));
                    if (foundFiles != null) {
                        Log.d(TAG, "Found files: " + Arrays.toString(foundFiles));
                        _files.postValue(new ArrayList<>(Arrays.asList(foundFiles)));
                    } else {
                        _files.postValue(new ArrayList<>());
                    }
                } else {
                    _files.postValue(new ArrayList<>());
                    _error.postValue("Directory not found or is not a directory.");
                }
            } catch (SecurityException e) {
                _files.postValue(new ArrayList<>());
                _error.postValue("Permission denied to read storage.");
            } catch (Exception e) {
                _files.postValue(new ArrayList<>());
                _error.postValue("Error loading files: " + e.getMessage());
            }
        });
    }

    public void loadFiles(List<String> files) {
        executorService.execute(() -> {
            try {
                List<File> foundFiles = new ArrayList<>();
                for (String file : files) {
                    File filePath = new File(file);
                    foundFiles.add(filePath);
                }
                Log.d(TAG, "Search result files: " + foundFiles);
                _files.postValue(foundFiles);
            } catch (SecurityException e) {
                _files.postValue(new ArrayList<>());
                _error.postValue("Permission denied to read storage.");
            } catch (Exception e) {
                _files.postValue(new ArrayList<>());
                _error.postValue("Error loading files: " + e.getMessage());
            }
        });
    }

    public void readFileContent(File file) {
        executorService.execute(() -> {
            StringBuilder stringBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line).append('\n');
                }
                _selectedFileContent.postValue(stringBuilder.toString());
            } catch (IOException e) {
                _selectedFileContent.postValue(null);
                _error.postValue("Error reading file: " + e.getMessage());
            }
        });
    }

    public void clearSelectedFileContent() {
        _selectedFileContent.setValue(null);
    }

    public void clearError() {
        _error.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}