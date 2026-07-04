# Phase 5 — Polish + production slim

**Status:** complete. Debug APK builds.

## What changed

### Onboarding / setup guide (the hardest Path-A UX)
- `ConnectFlow` wraps the Connect form with a one-tap **setup guide**
  (`SetupGuideScreen`): concrete, copy-pasteable steps to `pip install
  hermes-agent`, enable the API server (`API_SERVER_ENABLED` / `API_SERVER_KEY`),
  `hermes gateway run`, and find the address from a phone (LAN IP / tunnel).
- Connect verifies with `GET /health` before saving and gives plain-language,
  fixable errors; plaintext-remote gets a non-blocking warning.

### Hermes-centric Settings
- Rewrote `SettingsScreen`: **Your Hermes** (connected address + "Change /
  disconnect", which forgets the backend but keeps the memory-scope key so a
  reconnect lands in the same memory), **Appearance**, **About** (with the
  privacy posture: your data lives on your Hermes). Removed the on-device-model
  and BYO-cloud-key sections entirely.

### Production slim — retired the on-device AI stack
Hermes is the brain, so the app no longer ships any on-device ML:
- **Removed deps:** ONNX Runtime Mobile + MediaPipe GenAI (the native `.so`
  hogs), and the bundled `all-MiniLM-L6-v2` model asset (~22 MB).
- **Removed code:** `embedding/`, `llm/` (Android), plus the now-dead
  Memory / on-device-model-setup / BYO-cloud UI, and the model-provisioning
  Gradle tasks. `AppContainer` was rewritten down to what a thin client needs
  (connection, secure storage, reminder polling, reflection, crisis spine).
- **Manifest:** dropped the model-download foreground-service declaration +
  `FOREGROUND_SERVICE*` permissions.
- **Result:** the debug APK drops from **~73 MB to ~21 MB** — a genuinely thin,
  arch-agnostic client (the remaining size is mostly Compose + the extended
  material-icons set).
- The shared pure-Kotlin modules (which iOS adapters + tests still reference) are
  left in place; they add negligible size and aren't on the app's active path.

### Accessibility / polish
- Content descriptions on icon buttons; empty states for chat/reminders/goals;
  honest, actionable error copy throughout; a calm dedicated "Reflections"
  notification channel the user can silence independently.

## Verification
- `:androidApp:compileDebugKotlin` + `assembleDebug` green; APK size confirmed.
- All shared unit + live-Hermes integration tests still pass (Phases 1–4).
- Nothing on the chat/notes/reminders/goals/reflection path touches the removed
  stack.

## 🔒 REVIEW REQUIRED (final list — carried to the summary)
1. **Credential + session-key storage** (Phase 1).
2. **Crisis handling** (Phase 3; reflection tone rides alongside).
3. **Trust boundary** (Phase 1).
