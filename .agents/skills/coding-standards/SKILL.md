---
name: coding-standards
description: >-
  Coding, review, and architectural standards for Wupp: clean Kotlin/Compose code,
  memory leak prevention, JNI safety, WhatsApp ban risk safeguards, and DMCA compliance.
---

# Coding Standards & Quality Skill (Wupp)

This skill governs coding hygiene, architectural conventions, safety invariants, and compliance across the Wupp repository.

## 1. Kotlin & Jetpack Compose Standards
- **Compose Stability**: Prefer `@Immutable` and `@Stable` data models. Avoid passing unstable collections or lambdas that trigger unwanted recompositions.
- **State Flow**: Use Kotlin Flows and lifecycle-aware collection (`collectAsStateWithLifecycle()`).
- **Memory Management**: Do not hold references to Context or Views in ViewModels, singletons, or long-lived coroutine scopes.
- **Coroutines Scope**: Always tie async jobs to `viewModelScope`, `rememberCoroutineScope()`, or structured lifecycles.

## 2. Rust JNI Safety
- **Memory Boundaries**: Always convert JNI strings to Rust strings safely (`env.get_string(...)`), checking for null pointers.
- **Panic Boundary**: Wrap JNI entry points in `std::panic::catch_unwind` where appropriate so a Rust panic does not crash the JVM process.
- **Thread Safety**: Ensure multi-threaded Rust calls return results cleanly via registered JNI callbacks or polling channels.

## 3. Strict WhatsApp Flag Risk Safeguards
- **Client-Side Only**: Never make unnecessary calls to WhatsApp servers. Perform filtering, search, sorting, and UI operations entirely on-device.
- **Rate-Limiting & Pacing**: Enforce cooldown periods on user queries, profile picture fetches, and syncs.
- **Batching & Caching**: Cache contacts, group metadata, and chat history locally in SQLite/Room DB.

## 4. Strict DMCA & Takedown Safeguards
- **Zero Proprietary Assets**: Do not use official WhatsApp icons, proprietary audio/tones, or trademarked logos.
- **Visual Independence**: Bubble shapes must use standard rounded corners (avoid copyrighted speech-bubble tails).
- **Naming**: Avoid using the trademark "WhatsApp" in user-visible brand labels; use "Wupp" or generic descriptors.
