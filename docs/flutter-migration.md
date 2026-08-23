# Native-host Flutter migration

Verceltics migrates one screen body at a time. The installed native app stays
the host on both platforms so platform UI and existing user data retain their
original owners.

## Frozen iOS contracts

- App target and bundle ID remain `com.apoorvdarshan.verceltics`.
- Swift owns Keychain, UserDefaults, Application Support caches, RevenueCat,
  StoreKit, OAuth, provider clients, request validation, and confirmations.
- Existing Keychain service/account names, Codable payloads, UUIDs, defaults
  keys, and cache paths are not redefined in Dart.
- SwiftUI owns `WindowGroup`, `NavigationStack`, tab selection, sheets,
  toolbars, and every real iOS Liquid Glass surface.
- Flutter receives only display-safe DTOs and opaque native identifiers. It
  never receives provider credentials or unrestricted network access.

## Screen migration gate

Every migrated screen must keep its SwiftUI implementation available behind a
native feature switch until all of these pass:

1. DTO mapping tests prove credentials and persistence payloads are absent.
2. Flutter unit and widget tests cover loading, content, errors, and actions.
3. Existing native unit tests pass unchanged.
4. The screen is exercised on the dedicated headless simulator in light,
   dark, compact, regular-width, and accessibility text configurations.
5. An in-place real-device install preserves the same app data container and
   launches successfully without uninstalling the prior version.

If a gate fails, disable the screen switch and use the unchanged SwiftUI body.
No data migration is required to roll back because Flutter owns no persistence.
Each migrated screen exposes its renderer switch from a native themed toolbar
menu, so an installed build can immediately persist a return to SwiftUI.

Native snapshot changes are pushed through a generated Pigeon Flutter API.
Flutter then refetches the latest display DTO without blanking the current
content. It does not observe or mutate a native store directly.

## Clean checkout and CI

The generated Flutter Swift package is intentionally ignored. Run
`./scripts/bootstrap_flutter_ios.sh` before opening or building the Xcode
project from a clean checkout. Set `FLUTTER_BIN` when Flutter is not in PATH;
set `FLUTTER_CODESIGN_IDENTITY` when a developer machine has more than one
valid signing identity with the same name. CI installs the pinned Flutter SDK,
regenerates the package without signing, runs Flutter analysis and tests, and
only then invokes `xcodebuild`. Command-line Xcode builds use
`-hideShellScriptEnvironment` so the required Flutter scheme pre-action does
not print the build environment into CI logs.

Tag-triggered App Store archives use Xcode Cloud. Its executable
`ios/ci_scripts/ci_post_clone.sh` installs the same pinned Flutter SDK into the
job's derived-data directory and generates the package before Xcode resolves
the local dependency.

## iOS order

1. Registrar domain detail (read-only leaf; first proving screen)
2. Other read-only leaf details
3. Read-only inventory dashboards
4. Data-rich analytics and site detail screens
5. Display portions of mixed read/write screens
6. Explicit mutation workflows, with Swift remaining authoritative
7. Raw API tools last

Connection flows, account menus, purchases, OAuth, paywalls, and destructive
confirmations remain native during the iOS phase. Android host work starts only
after the iOS bridge and converted screens meet these gates.
