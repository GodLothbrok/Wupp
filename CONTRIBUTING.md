# Contributing to Wupp

## Reporting issues and requesting features

- **Questions, setup help, and open ideas:** Use [GitHub Discussions](https://github.com/hanzalaramz/Wupp/discussions) (`Q&A`, `Ideas`, `General`, or `Show and tell`).
- **Bugs and confirmed feature requests:** File an issue from inside Wupp (**Settings > Updates & GitHub Support**) or through the GitHub issue forms. Include your Wupp version, Android version, device model, and steps to reproduce when reporting a bug.

## Pull requests

1. Fork the repository and create a branch from `main`, for example `fix/delivery-ticks` or `feat/voice-waveform`.
2. Build and check the Rust bridge before committing bridge changes:
   ```bash
   cd rustbridge && cargo check --lib
   ```
3. Do not alter Room `@Entity` definitions without handling the database version and migration path.
4. Keep keystores (`*.jks`, `*.keystore`), `local.properties`, compiled native libraries (`app/src/main/jniLibs/`, `*.so`), and `.apk` or `.xapk` files out of Git.
5. Open your pull request against `main` and attach before/after screenshots if you changed the UI.
