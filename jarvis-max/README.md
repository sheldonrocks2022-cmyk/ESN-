# JARVIS MAX v1 (Android-first)

A mobile-friendly browser prototype with a command console, optional speech synthesis, browser speech recognition when supported, and local-device memory.

## Run
Open index.html in a modern browser, or serve this folder using any static hosting service. For microphone support, use HTTPS or localhost and grant permission.

## Current limitations
This is not a native Android APK, always-listening assistant, cloud AI, or connected ESN operator. No API keys or credentials belong in frontend JavaScript. Future AI integrations require a server-side authenticated API and user approval for consequential actions.

## Commands
help, time, date, clear, remember TEXT, memories, forget all.

## Safety
Local memory uses browser localStorage and is not encrypted. Do not store secrets. This feature is scoped to this browser/device.
