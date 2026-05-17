# LuthiMark — Planned Work

Log of features and improvements planned but not yet implemented.

## First-launch tutorial

Add an onboarding flow shown the first time the app is opened, walking new users through:

- How workspaces work (and that they're optional)
- Opening a single file from outside the app (download / share / chooser)
- Saving an external file into a workspace
- Editing vs. viewing
- Starring and recents

Should be skippable. State stored in `DataStore` ("tutorial seen") so it doesn't reappear.

## Claude Code integration

Investigate building Claude Code functionality directly into LuthiMark so users can interact with files using Claude on their phone — similar to the JetBrains / Android Studio plugin, but for mobile.

Open questions to work through with Brian:

- API auth model: bring-your-own Anthropic key, OAuth, or backend proxy?
- Tool surface: which Claude Code tools translate sensibly to a phone (Edit, Read, Bash, Grep, Glob, WebFetch, etc.)?
- File system sandbox: limit Claude to the active workspace tree (SAF) — never the wider device.
- UI: chat panel alongside the editor, or a separate mode?
- Cost / billing transparency in-app.
- Privacy: clear consent for sending file contents to the API.

This is a substantial body of work and is intentionally deferred until the current scope settles.

## iOS compatibility

Make LuthiMark available on iPhone (targeting iPhone 13 and newer).

The current app is Android-native (Kotlin + Jetpack Compose), so this isn't a "port" — it's a parallel implementation. Options to evaluate:

- **Kotlin Multiplatform + Compose Multiplatform**: reuse data/repository layer and the Compose UI. Best code-share story but Compose-on-iOS is still maturing.
- **Native SwiftUI rewrite**: cleanest iOS-native feel, two codebases to maintain in parallel.
- **Flutter / React Native rewrite**: single codebase across both, but loses native idioms.

iOS file-system access is also different from Android SAF — iOS uses `UIDocumentPickerViewController` and security-scoped bookmarks. The current workspace concept will translate, but the implementation is non-trivial.

Recommended first step: evaluate Compose Multiplatform's current iOS support and prototype the file-viewer screen to gauge fidelity and developer experience before committing.
