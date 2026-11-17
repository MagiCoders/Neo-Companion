# Roadmap

MochiLife is pre-release. This is what is next, in order, and where help is welcome.

## Next
1. **Confirm the 3.3 voice fixes on more phones.** Tested so far on one device (Galaxy S22+). Reports from other devices are the most useful contribution right now.
2. **Accessibility evaluation.** The app is designed around voice but has not been tested with people who use screen readers or other assistive technology. Plan: TalkBack audit, then sessions with real users.
3. **Better Arabic and Persian listening on the phone.** The small on-phone Whisper model mishears everyday dialect. Evaluate larger models and dialect-tuned decoding.
4. **Faster on-phone answers.** Gemma takes 5 to 8 seconds per answer on the test phone; investigate smaller models and streaming the first sentence.
5. **Server-side Pro and credits** (Cloud Function verifying Google Play purchases).
6. **Google Play release** (internal testing, then closed testing).

## Good first issues
- Add unit tests for `NameMatcher` with more Arabic and Persian name spellings.
- Add a TalkBack content description audit for one screen.
- Translate a screen's strings into another language.
- Improve the Diagnostics screen's copy-to-clipboard feedback.
- Document one on-phone model's measured speed and memory on your device.

## Not planned
- Recording or uploading speech for analytics.
- Any action on the phone without a visible, cancellable confirmation.
