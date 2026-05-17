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

## Images in externally-opened files

When a markdown file is opened from outside the app (system picker, download, share intent), embedded images with relative paths don't render. The app only has access to that single file, not its sibling images — that's how Android's Storage Access Framework works: tapping one file in the system picker grants permission for that file only, not the folder around it.

Acceptable for now: complex file work tends to happen in a workspace anyway, and workspaces *do* render images correctly. Revisit only if there's demand.

Planned approach when we come back to it: make the existing "Save to workspace" action smarter so it serves the in-place case naturally —

- User taps the action and picks the folder that already contains the open file
- Action walks the picked tree to find a file with the matching name (reusing the recents-validation search)
- If found, register the folder as a workspace and open the file from there — images then resolve through the existing workspace-aware path
- If not found, fall back to the current "save a copy" behavior
- Rename the action to **"Add to workspace"** to fit both cases
