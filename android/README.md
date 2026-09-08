# JARVIS Android

Android version of the existing Python JARVIS/NEXA assistant.

## Current phase
- Native Android app shell using Kotlin + Jetpack Compose
- Microphone permission
- Command input and execute UI
- Android branch kept separate from the existing PC code

## Architecture plan
The existing PC implementation contains desktop-only modules such as Tkinter, pyautogui/pynput, Windows window control, Selenium automation and Windows TTS. These should not be copied directly into Android.

The Android app will reuse the assistant's AI/persona/tool concepts while replacing desktop-only capabilities with Android APIs and a mobile-safe backend.

Planned phases:
1. Android shell and permissions
2. Voice input/output
3. Gemini/LiveKit connection
4. Persistent memory
5. Search + weather
6. Android intents (apps, browser, calls, messages where permitted)
7. Camera/object detection
8. Settings and secure API configuration
9. Release build
