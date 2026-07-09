# NotePilot Project Context

## App

- Name: NotePilot
- Package: `com.smithware.notepilot`
- Repo: `https://github.com/BadBagger/notepilot`
- Purpose: voice-first local notes, tasks, lists, reminders, shopping lists, and idea capture.
- Promise: "Say it. NotePilot organizes it."

## Current MVP

- Kotlin, Jetpack Compose, Room, and DataStore.
- Android platform speech recognition with typed fallback.
- Local rule-based transcript formatter via `TranscriptFormatter` and `LocalRuleBasedFormatter`.
- Placeholder `FutureAiFormatter` for later optional AI work.
- Local reminders with Android notifications.
- Launcher icon uses the supplied NotePilot artwork with transparent outer background.

## Release State

- Initial publish target: `v0.1.0-mvp`.
- Release signing uses ignored local `keystore.properties` and a keystore outside the repo under `C:\Users\KyleB\.smithware\signing\com.smithware.notepilot\`.
- Do not commit APKs, keystores, passwords, or local SDK paths.
