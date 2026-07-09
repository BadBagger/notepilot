# NotePilot

NotePilot is a fast voice-first Android note app for Smithware Studios.

Core promise: **Say it. NotePilot organizes it.**

## MVP

- Voice capture with Android speech recognition when available.
- Typed fallback when microphone permission is denied or recognition is unavailable.
- Review-before-save for every voice capture.
- Local rule-based transcript cleanup for notes, tasks, checklists, shopping lists, reminders, and ideas.
- Room local storage and DataStore settings.
- Local reminders with Android notifications.
- No login, cloud account, or paid API required.

## Build

```powershell
$env:JAVA_HOME='C:\Users\KyleB\Documents\Codex\2026-07-04\build-a-native-android-app-using\.local-jdk\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```
