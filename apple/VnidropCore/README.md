# VnidropCore (Swift package)

Swift bindings for the VniDrop Rust transfer core (`crates/vnidrop`), generated
with UniFFI in library mode. The Rust crate is never modified for this — the
Swift surface is produced from the compiled staticlib.

## Regenerate

From the repository root:

```bash
apple/scripts/build-core.sh release   # or: debug
```

This builds `libvnidrop.a` for `aarch64-apple-ios`, `aarch64-apple-ios-sim`, and
`aarch64-apple-darwin`, generates `Sources/VnidropCore/Vnidrop.swift`, and
assembles `vnidrop.xcframework`.

## Generated / ignored artifacts

- `vnidrop.xcframework/`
- `Sources/VnidropCore/Vnidrop.swift`

Both are gitignored. A clean checkout must run the build script before opening
the Xcode project.
