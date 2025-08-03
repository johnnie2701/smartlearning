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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.button.MaterialButton;

public class WelcomeActivity extends AppCompatActivity {

    private MaterialButton getStartedButton;
    private MaterialButton howItWorksButton;
    private LottieAnimationView lottieAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Initialize views
        getStartedButton = findViewById(R.id.getStartedButton);
        howItWorksButton = findViewById(R.id.howItWorksButton);
        lottieAnimation = findViewById(R.id.lottieAnimation);

        // Set up click listeners
        setupClickListeners();
        
        // Start the animation
        startLottieAnimation();
    }

    private void setupClickListeners() {
        getStartedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to the main app (FileListActivity)
                Intent intent = new Intent(WelcomeActivity.this, FileListActivity.class);
                startActivity(intent);
                finish(); // Close welcome screen
            }
        });

        howItWorksButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show how it works information
                showHowItWorksDialog();
            }
        });
    }

    private void showHowItWorksDialog() {
        // Create and show a dialog explaining how the app works
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("How Smart Learning Works")
                .setMessage("1. Upload your study materials as text files\n\n" +
                        "2. Choose between Chat or Quiz mode\n\n" +
                        "3. Chat Mode: Ask questions about your content and get AI-powered answers\n\n" +
                        "4. Quiz Mode: Generate practice questions and get instant feedback\n\n" +
                        "5. All AI processing happens locally on your device - no internet required!")
                .setPositiveButton("Got it!", null)
                .setIcon(R.drawable.ic_format_text)
                .show();
    }
    
    private void startLottieAnimation() {
        // The animation will auto-play due to lottie_autoPlay="true" in XML
        // You can add additional animation controls here if needed
        lottieAnimation.setRepeatCount(-1); // Infinite loop
        lottieAnimation.playAnimation();
    }
} 