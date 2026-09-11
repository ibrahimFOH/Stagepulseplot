# StagePulseMix

Professional universal digital mixing console control application.

## Status

Release Candidate: 0.9.2

Midas/X32 protocol work is the current focus. Android release packaging is prepared separately and real-console validation remains required before production release.

## Local verification

```bash
npm install
npm test
npm run check
npm run start:lan
```

## Android

Open `android/` in Android Studio and build `app` -> `assembleRelease`.

The final production gate is physical X32/M32 validation, followed by clean-install and reconnect testing.
