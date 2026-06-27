# iosApp — SwiftUI front-end

Native SwiftUI UI over the shared KMP `:shared` module. The business logic
(models, `LocalStore`, `ReminderService`) is 100% shared Kotlin; only the UI and
the `UNUserNotificationCenter` scheduler are Swift.

> ⚠️ **This target cannot be built in the Linux dev sandbox** — it requires
> **macOS + Xcode**. The Swift sources and Kotlin/Native `iosMain` code compile
> cleanly (the shared iOS klibs build on Linux), but linking the framework and
> running the app must be done on your Mac. Nothing here has been run on a
> simulator/device yet.

## Files

| File | Role |
|------|------|
| `iosApp/iOSApp.swift` | App entry; requests notification permission |
| `iosApp/ContentView.swift` | TabView: Notes / Reminders / Plan (SwiftUI) |
| `iosApp/AppModel.swift` | `ObservableObject` bridging shared Kotlin → SwiftUI (awaits Kotlin `suspend` funcs as Swift `async`) |
| `iosApp/IosReminderScheduler.swift` | Implements the shared `ReminderScheduler` protocol via `UNUserNotificationCenter` |
| `iosApp/Info.plist` | App metadata |
| `project.yml` | XcodeGen spec that generates `iosApp.xcodeproj` |

## Build & run (on a Mac)

```bash
# 1. Generate the Xcode project from project.yml
brew install xcodegen        # one-time
cd iosApp
xcodegen generate

# 2. Open and run
open iosApp.xcodeproj
#    Select an iPhone simulator and Run (⌘R).
#    The "Build shared KMP framework" pre-build phase runs
#    ./gradlew :shared:embedAndSignAppleFrameworkForXcode automatically.
```

Prefer not to use XcodeGen? Create a new SwiftUI App target in Xcode, add the
four `iosApp/*.swift` files, then add a Run Script build phase that runs
`./gradlew :shared:embedAndSignAppleFrameworkForXcode` and set
`FRAMEWORK_SEARCH_PATHS` to
`$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.

## Acceptance check (Notes + a reminder that fires)

1. Run on a simulator; grant the notification prompt.
2. Notes tab → add a note → confirm it appears (and survives an app relaunch —
   persistence is the shared `PersistentLocalStore` over `NSUserDefaults`).
3. Reminders tab → title + "1 min" → **Set reminder**.
4. Background the app; ~1 min later the local notification fires. ✅
