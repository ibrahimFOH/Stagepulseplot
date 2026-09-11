# StagePulseMix Architecture

StagePulseMix uses a vendor-neutral command surface so the application UI and API remain stable while console-specific drivers are added underneath.

## Driver boundary

- `M32Driver`: Midas/X32 OSC transport and control surface.
- `AllenHeathDriver`: isolated future vendor adapter.
- Future drivers should implement the same logical operations without leaking vendor-specific protocol details into UI code.

## Core state

`src/core/state.js` is the normalized state model. Protocol feedback should be retained in `rawFeedback` and mapped into normalized blocks where a reliable mapping exists.

## Android

The Android application is a WebView shell around the StagePulseMix web interface. The release build must be validated against a real console before production release.
