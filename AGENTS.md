# Personal EPUB Reader (Android)

## Goal
A lightweight, fast, offline-only EPUB reader for my own use on Android.
I'm also using this project to learn agentic coding, so explain non-obvious
decisions briefly and keep changes small and reviewable.

## Stack
- Kotlin, Jetpack Compose (Material 3), single-activity app
- Navigation Compose
- Readium Kotlin Toolkit for EPUB parsing and rendering (do NOT write a custom renderer)
- Room for the library (books, reading progress, bookmarks)
- DataStore for settings (theme, font size, line spacing)
- Gradle with a version catalog (libs.versions.toml)

## Principles
- Minimal dependencies. Ask before adding any new library.
- No network permissions, no analytics, no accounts. Everything stays on device.
- Performance matters: no blocking work on the main thread, lazy lists for the library,
  small memory footprint when opening large books.
- Prefer simple, readable code over clever abstractions.

## Library API rule
Readium and AndroidX APIs change between versions. Before writing code against them,
check the current official documentation or the library's source and use the version
in the version catalog. Never guess method names. If unsure, say so.

## Workflow rules
- Work on ONE milestone at a time. Don't build ahead.
- For anything touching more than 2-3 files, outline your plan first and wait for my OK.
- After changes, make sure the project builds (`./gradlew assembleDebug`) and fix errors.
- Tell me exactly what I should test on the device/emulator.
- Do not commit, push, or delete files unless I ask. I commit after each working step.

## Project layout (keep to this)
- ui/       Compose screens and components (library, reader, settings)
- data/     Room database, DAOs, repositories
- reader/   Readium integration
- util/     small helpers

## Current status
Milestone 2: import EPUBs and library view completed.
