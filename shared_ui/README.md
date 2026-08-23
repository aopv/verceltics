# Verceltics shared UI

This Flutter application is the shared presentation layer embedded by the
existing native Verceltics hosts. It is not a replacement iOS application.

On iOS, SwiftUI remains responsible for app lifecycle, navigation, Liquid
Glass chrome, credentials, persistence, purchases, provider networking, and
mutations. Flutter receives versioned display snapshots over generated Pigeon
channels and returns typed user intents to Swift.

From the repository root, generate both the bridge code and local Swift
package before opening Xcode from a clean checkout:

```sh
./scripts/bootstrap_flutter_ios.sh
```

Set `FLUTTER_BIN` if Flutter is not in PATH. A machine with duplicate signing
identity names can set the exact certificate hash in
`FLUTTER_CODESIGN_IDENTITY`.

To regenerate only the typed bridge after editing
`pigeons/verceltics_bridge.dart`:

```sh
cd shared_ui
dart run pigeon --input pigeons/verceltics_bridge.dart
```

The generated `build/` directory is intentionally not committed.
