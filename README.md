# Smart Learning

An intelligent Android educational application that leverages local AI/LLM capabilities to provide interactive learning experiences. Smart Learning allows users to upload text files and interact with the content through AI-powered chat and quiz modes, all running locally on the device using Google's MediaPipe GenAI framework.

## 🚀 Features

### 📚 File Management
- **Import Text Files**: Upload and manage educational content from device storage
- **Automatic Processing**: Text chunking and vector embedding for semantic search
- **Search Functionality**: Find specific content across uploaded documents
- **Content Extraction**: Seamless file content processing and display

### 🤖 AI-Powered Learning
- **Interactive Chat**: Engage in conversations about lesson content with local AI
- **Smart Quiz Generation**: AI-generated questions based on uploaded materials
- **Answer Evaluation**: Intelligent feedback on quiz responses
- **Context Awareness**: AI responses tailored to specific lesson content

### 🎤 Voice Interaction
- **Speech Recognition**: Voice-to-text functionality for hands-free interaction
- **Real-time Processing**: Live speech recognition with partial results
- **Multi-language Support**: Supports device language settings

### 📱 User Experience
- **Material Design**: Modern, intuitive interface
- **Dual Modes**: Seamless switching between chat and quiz modes
- **Offline Operation**: All AI processing runs locally - no internet required
- **Responsive UI**: Proper loading states and error handling

## 🏗️ Architecture

### Technology Stack
- **Language**: Java
- **Architecture**: MVVM with ViewModel and LiveData
- **UI Framework**: AndroidX with Material Design
- **AI Framework**: Google MediaPipe GenAI
- **Vector Storage**: Local Agents RAG with SqliteVectorStore
- **Target SDK**: 36 (Android 14)
- **Min SDK**: 34 (Android 14)

### Core Components

#### Activities
- **`FileListActivity`**: Main entry point for file management
- **`InteractionActivity`**: Container for chat and quiz interactions
- **`DisplayTextActivity`**: Read-only file content display

#### AI/LLM Components
- **`LlmHelper`**: Manages local LLM inference using Gemma-3n model
- **`InteractionViewModel`**: Coordinates UI and AI operations
- **`FileListViewModel`**: Handles file operations and embeddings

#### UI Fragments
- **`ChatFragment`**: Interactive chat interface with speech recognition
- **`QuizFragment`**: Quiz generation and evaluation system

#### Data Management
- **`FileListAdapter`**: File list display management
- **`ChatMessagesAdapter`**: Chat message display handling
- **`FileItem`**: File data model

## 📦 Dependencies

### Core Dependencies
```kotlin
implementation(libs.appcompat)
implementation(libs.material)
implementation(libs.tasks.genai)        // Google MediaPipe GenAI
implementation(libs.car.ui.lib)         // Google Car UI Library
implementation(libs.localagents.rag)    // Local Agents RAG
```

### Testing Dependencies
```kotlin
testImplementation(libs.junit)
androidTestImplementation(libs.ext.junit)
androidTestImplementation(libs.espresso.core)
```

## 🔧 Setup & Installation

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 34+
- Java 11 or later
- Google MediaPipe GenAI model files

### Installation Steps

1. **Clone the Repository**
   ```bash
   git clone https://github.com/your-username/smartlearning.git
   cd smartlearning
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Open the project from the cloned directory

3. **Configure LLM Model**
   - Place your Gemma-3n model file in the appropriate assets directory
   - Update the model path in `InteractionActivity.java`:
   ```java
   interactionViewModel.initializeLlm("/data/local/tmp/llm/gemma-3n-E2B-it-int4.task");
   ```

4. **Build and Run**
   - Sync project with Gradle files
   - Build the project
   - Run on an Android device or emulator (API 34+)

### Permissions
The app requires the following permissions:
- `RECORD_AUDIO`: For speech recognition functionality

## 🎯 Usage Guide

### Getting Started
1. **Launch the App**: Open Smart Learning from your device
2. **Upload Files**: Tap the floating action button to import text files
3. **Select Content**: Choose a file from the list to interact with
4. **Choose Mode**: Switch between Chat and Quiz modes using the toggle button

### Chat Mode
- **Ask Questions**: Type or speak questions about the lesson content
- **Get AI Responses**: Receive contextual answers based on uploaded materials
- **Voice Input**: Tap the microphone button for speech recognition

### Quiz Mode
- **Generate Questions**: Tap "Generate Question" to create AI-powered quizzes
- **Answer Questions**: Type your responses in the provided field
- **Get Feedback**: Receive intelligent evaluation and feedback on your answers
- **Continue Learning**: Generate new questions to continue testing your knowledge

## 🔍 Technical Implementation

### LLM Integration
- **Framework**: Google MediaPipe GenAI
- **Model**: Gemma-3n (local deployment)
- **Acceleration**: GPU-accelerated inference
- **Context**: File-aware responses

### Data Flow
1. **File Upload** → Content extraction and processing
2. **Text Chunking** → Vector embedding and storage
3. **User Interaction** → Mode selection (chat/quiz)
4. **AI Processing** → Context-aware response generation
5. **Response Display** → User-friendly output presentation

### Vector Storage
- **Database**: SqliteVectorStore for document embeddings
- **Model**: GeckoEmbeddingModel for text vectorization
- **Search**: Semantic search across uploaded content

## 🛠️ Development

### Project Structure
```
smartlearning/
├── app/
│   ├── src/main/
│   │   ├── java/com/gemma3n/smartlearning/
│   │   │   ├── Activities/          # Main app activities
│   │   │   ├── Fragments/           # UI fragments
│   │   │   ├── ViewModels/          # MVVM view models
│   │   │   ├── Adapters/            # RecyclerView adapters
│   │   │   └── Helpers/             # Utility classes
│   │   ├── res/                     # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
└── build.gradle.kts
```

### Key Classes
- **`LlmHelper`**: Core AI functionality and model management
- **`InteractionViewModel`**: State management for chat/quiz modes
- **`FileListViewModel`**: File operations and vector storage
- **`ChatFragment`**: Chat interface with speech recognition
- **`QuizFragment`**: Quiz generation and evaluation

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Google MediaPipe**: For providing the GenAI framework
- **Gemma Team**: For the open-source language model
- **Android Community**: For continuous platform improvements

## 📞 Support

For support, please open an issue in the GitHub repository or contact the development team.

---

**Smart Learning** - Empowering education through local AI technology. 