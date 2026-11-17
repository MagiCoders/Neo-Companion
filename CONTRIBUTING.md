# Contributing to MochiLife

Thank you for helping. Please read this before opening a pull request.

## Ground rules
- Be respectful ([Code of Conduct](CODE_OF_CONDUCT.md)).
- **Never commit secrets**: `google-services.json`, keystores, `.env`, `local.properties`, API keys. They are git-ignored; keep it that way. Use your own Firebase project (see the README).
- Security issues go through [SECURITY.md](SECURITY.md), not public issues.
- No mock, hard-coded or placeholder behaviour in the app. Features must work on a real device.
- Do not change `signingConfigs` in `app/build.gradle.kts`.

## Workflow
1. Open an issue first for anything larger than a small fix.
2. Fork, branch from `main` (`fix/…`, `feat/…`).
3. Build and test locally:
   ```
   ./gradlew :app:testDebugUnitTest
   ```
   Firestore rules tests: `firebase emulators:exec --only firestore,auth --project demo-test "./gradlew :app:testDebugUnitTest"`.
4. Add tests for new logic (voice pipeline, command parsing, rules, diagnostics).
5. Add a plain-words entry to `CHANGELOG.md`; bump `versionName`/`versionCode` for app changes.
6. Open a pull request using the template. Keep it focused.

## Code style
Kotlin official style, 2-space indent (see `.editorconfig`). Prefer small, testable functions; keep Android-specific code out of pure logic so it can be unit tested.

## Accessibility and privacy
Changes must keep voice-first use working, keep user actions confirmable and cancellable, and must not record or upload speech beyond what is documented.

## Licence
By contributing you agree that your contribution is licensed under the Apache License 2.0 (see [LICENSE](LICENSE)).
