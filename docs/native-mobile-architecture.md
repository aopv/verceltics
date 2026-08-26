# Native mobile architecture

Verceltics has two independent native mobile applications:

- `ios/` is SwiftUI-only. Its existing provider clients, state, Keychain records, local snapshots, navigation, and Liquid Glass integrations remain native iOS code.
- `android/` is Kotlin and Jetpack Compose-only. Android-native lifecycle, input, haptics, accessibility, storage, and Material behavior live in this project.

There is no Flutter or Dart runtime, generated Flutter bridge, or shared cross-platform UI module in either application. Provider identifiers and API behavior are ported deliberately, but platform UI, lifecycle, and secure storage are not shared binaries.

## Data and backend boundaries

- Restoring the SwiftUI application does not migrate, rewrite, or delete existing iOS Keychain records, preferences, or protected snapshots.
- Android has a separate application sandbox. Credentials are encrypted with keys held by Android Keystore and stored only in app-private, backup-excluded storage; ordinary preferences must never contain credentials.
- Credentials and provider data are not copied between iOS and Android. Installing or updating one platform cannot alter the other platform's data.
- The existing iOS provider clients and request policies remain unchanged during the Android migration.
- Provider requests continue to go directly from the device to the selected provider's HTTPS API. The Android port must preserve the same host validation, redirect, timeout, response-size, cancellation, and destructive-action safeguards before a provider is marked complete.

## Screen-by-screen migration rule

Each Android provider moves through the same gates:

1. Preserve the canonical provider identifier and authentication contract.
2. Build the native Compose connection, loading, empty, error, search, refresh, and detail states.
3. Add Android Keystore-backed account persistence and the provider HTTPS client.
4. Cover domain and request behavior with unit tests.
5. Exercise the user-visible flow on a dedicated Android emulator and inspect screenshots, the accessibility tree, and runtime logs.

A catalog entry is not considered provider parity. A provider is complete only after all relevant gates pass.

## Screen-by-screen status

| Platform / slice | Status | Scope |
|---|---|---|
| iOS SwiftUI | Preserved | Existing UI, Liquid Glass integration, local data, and all 27 provider integrations remain native and operational. |
| Android app shell and catalog | Implemented | Native Compose navigation and discoverability for 10 hosting providers, 8 registrars, and 9 site services. |
| Android Vercel | First end-to-end slice | Token connection and validation, protected account persistence, projects, loading/error/empty states, search, and refresh establish the Android architecture. |
| Android remaining 26 providers | Catalogued, not yet parity-complete | Provider API clients and detail workflows will be migrated and tested one screen at a time. |

The matrix describes source parity, not store availability. It should be updated whenever a provider passes or falls back from the completion gates above.

## Continuous integration

CI keeps the native projects independent:

- iOS tests run with `xcodebuild` against an iOS simulator.
- Android runs JVM tests, Android Lint, and a debug assembly through the Gradle wrapper.
- No Flutter bootstrap, analysis, test, or framework-generation step is required.
