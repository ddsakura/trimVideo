# Video Trimmer

A modern Android application that allows users to select videos from their device, trim them to 30 seconds, and play the trimmed result - all with a clean Material 3 UI built using Jetpack Compose.

## Features

- **Video Selection**: Select videos from your device using the native Android file picker
- **Automatic Trimming**: Automatically trim selected videos to 30 seconds
- **Video Playback**: Play trimmed videos with Jetpack Media3 integration
- **Modern UI**: Built with Material 3 and Jetpack Compose for a beautiful, responsive interface
- **Permission Handling**: Runtime permission handling for accessing media files on all Android versions

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Media Processing**: Android MediaExtractor, MediaMuxer, and MediaCodec APIs
- **Video Playback**: Jetpack Media3
- **Minimum SDK**: 24 (Android 7.0 Nougat)
- **Target SDK**: 36 (Android 15)

## How It Works

The app uses Android's native media processing APIs to trim videos efficiently:

1. **MediaExtractor** extracts media data from the input source
2. **MediaMuxer** creates a new MP4 file for the output
3. **MediaCodec** handling for proper buffer processing
4. **Media3** for smooth playback of the trimmed video

The trimming is performed on a background thread to keep the UI responsive, and the app provides feedback during the process through a loading indicator and toast messages.

## Getting Started

### Prerequisites

- Android Studio Iguana or newer
- Android SDK 36
- Kotlin 2.0.21 or newer

### Installation

1. Clone this repository:
   ```
   git clone https://github.com/ddsakura/trimVideo.git
   ```

2. Open the project in Android Studio

3. Build and run the app on your device or emulator

## Usage

1. Launch the app
2. Tap the "Select Video" button
3. Grant storage permissions if prompted
4. Choose a video from your device
5. Wait for the trimming process to complete
6. Use the player controls to play, pause, or restart the trimmed video

All trimmed videos are stored in the app's private storage directory and can be found at:
`/storage/emulated/0/Android/data/com.example.myapplication/files/Movies/`

## Implementation Details

### Key Components

- **MainActivity**: Entry point of the application, handles permissions and video selection
- **VideoTrimmerScreen**: Compose UI component for displaying the video player and controls using Media3's PlayerView
- **VideoUtils**: Utility class containing the core video trimming functionality

### The Trimming Process

The `trimMp4Video` function:

1. Extracts both audio and video tracks from the source file
2. Creates a new MP4 container for the trimmed content
3. Copies the first 30 seconds of content to the new container
4. Returns the path to the trimmed video file

## License

*[Add your license information here]*

## Acknowledgments

- [Jetpack Media3](https://developer.android.com/jetpack/androidx/releases/media3) for video playback
- [Jetpack Compose](https://developer.android.com/jetpack/compose) for the modern UI toolkit
- [Material 3](https://m3.material.io/) for the design system

---

*Built with ❤️ by [Your Name]*
