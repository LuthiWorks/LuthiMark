# LuthiMark

A Markdown viewer and editor for Android, built with Kotlin and Jetpack Compose. Part of the [LuthiWorks](https://github.com/LuthiWorks) ecosystem.

**Status: Version 1.0.** Requires Android 8.0 (API 26) or later. There is no packaged release yet, so for now you build it from source.

## Features

- **Workspaces (optional):** add any folder as a workspace, keep several, and switch between them from the drawer. Browse subfolders with breadcrumbs.
- **View and edit:** switch between a rendered preview and the raw text, save your changes, and create new Markdown files.
- **Find in file:** search the open file and step through the matches.
- **Recents and stars:** the home screen shows recent files and the ones you've starred.
- **Syntax highlighting:** code blocks are highlighted for Kotlin, Java, Python, JavaScript, TypeScript, Rust, Swift, C, C++, C#, Ruby, Go, Shell, Perl, PHP and SQL.
- **Images:** images with relative paths render for files opened from a workspace.
- **Open from other apps:** open a file from a download, a file manager or the system chooser without setting up a workspace, and save a copy to a workspace if you want to keep it.
- **Other text files:** besides `.md` and `.markdown`, files opened from outside the app can include common text formats such as `.txt`, `.log`, `.json`, `.yaml`, `.toml`, `.csv`, `.py`, `.sh` and `.xml`. Workspace folders list Markdown files only.
- **Adapts to your device:** the layout adjusts to the screen size, follows the system's light or dark setting, and uses your wallpaper colours on Android 12 and later.

The app's manifest declares no Android permissions. It gets access to a folder or file only when you choose one in the system picker.

## Known issues

- **Save to workspace overwrites without asking.** If the workspace already has a file with the same name, saving replaces it. Until the fix is merged, check the name before saving.
- **Quick file switching:** opening files in quick succession can show or save one file's text under another's name.
- **Images in files opened from outside the app:** relative image paths don't render, because the app only has access to that one file. Workspaces aren't affected.

[Pull request #1](https://github.com/LuthiWorks/LuthiMark/pull/1) fixes the first two, along with some problems with back navigation and unsaved edits. It isn't merged yet.

## Building

You need JDK 21 and the Android SDK. Android Studio includes the SDK, and you can also open the project there and run it directly.

```bash
./gradlew assembleDebug        # builds the debug APK
./gradlew testDebugUnitTest    # runs the unit tests
```

On Windows, use `gradlew.bat`. The APK is written to `app/build/outputs/apk/debug/`.

## Project layout

| Path | What it is |
|---|---|
| `app/src/main/java/com/luthiworks/luthimark/MainActivity.kt` | App entry point and handling of files opened from other apps |
| `.../data/` | Reading and writing files, and saved preferences |
| `.../ui/` | The home, file and workspace screens, the view model, code highlighting and the theme |
| `to-do.md` | Planned work |

## Planned

Details are in [`to-do.md`](to-do.md):

- A first-launch tutorial
- Claude Code integration, so you can work on files with Claude from your phone
- iOS support
- Better handling of images in files opened from outside the app

## License

This project has no license file yet.
