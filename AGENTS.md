# NotePilot Agent Instructions

NotePilot is a standalone Smithware Studios Android app. Do not fold general notes or voice capture behavior into Workday Planner.

Before publishing:

1. Preserve package name `com.smithware.notepilot`.
2. Keep the MVP local-first: no login, no cloud requirement, no paid AI API.
3. Preserve raw transcripts when saving captures.
4. Use local-only release signing from ignored `keystore.properties`.
5. Publish GitHub Releases with APK assets; source pushes alone are not enough for DevHub.
