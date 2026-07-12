# AGENTS.md — VniDrop agent guide

Instructions for AI agents and humans working in this repository. Read this
before making changes. Prefer following existing code over inventing patterns.

---

## 1. Mission and stack

**VniDrop** is a cross-platform local file-transfer app: share files/folders with
nearby devices via tickets (copy, QR, NFC), with optional sender approval.

| Layer | Location | Role |
|--------|----------|------|
| Rust core | `crates/vnidrop/` | Iroh networking, blob store, SQLite, approval, tickets, streaming |
| Shared UI | `shared/` | Compose Multiplatform UI, ViewModels, platform bridges |
| Apps | `androidApp/`, `iosApp/`, `desktopApp/` | Thin hosts |
| UniFFI | Gobley + `crates/vnidrop/uniffi.toml` | Kotlin bindings to `VnidropCore` |

**Non-negotiable product rule:** platform/UI code opens files and reacts to
events; **Rust owns byte streaming**. Do not pull transfer payloads through
Kotlin heap as the default path.

Primary docs (do not re-copy wholesale into PRs):

- Core send/receive flow: [`crates/vnidrop/CORE_FLOW.md`](crates/vnidrop/CORE_FLOW.md)
- Rust test layout: [`crates/vnidrop/tests/README.md`](crates/vnidrop/tests/README.md)

---

## 2. Hard rules (always)

1. **Default to PRs into `master`.** Do not merge locally to `master` unless the
   user explicitly asks.
2. **Do not push, force-push, or open a PR** unless the user asks.
3. **Signed commits** when the repo enables signing (`commit.gpgsign`). If
   signing fails (empty agent / passphrase), stop and ask the user to unlock
   the key (`ssh-add …`). Do not silently create unsigned commits.
4. **Minimal scope.** Change only what the task requires. No drive-by refactors,
   dependency bumps, or reformatting unrelated files.
5. **Do not force architecture migrations.** Adapt to existing patterns
   (ViewModels, feature packages, Rust modules). Structural rewrites only when
   requested or when fixing a clear violation.
6. **Never commit secrets**, API keys, keychains, or passphrases. Never paste
   user passphrases into chat or files.
7. **Destructive git** (`reset --hard`, `push --force`, dropping data) only with
   explicit user approval.
8. **Bug fixes need tests** at the correct layer (see §6).

---

## 3. Compose Multiplatform — use `compose-skill`

### When to load it

For **any** Kotlin UI / presentation work (screens, components, theme,
navigation, resources, ViewModel↔UI wiring, accessibility, lists, animation),
load and follow:

```text
.codex/skills/compose-skill/SKILL.md
```

Do **not** invent a parallel Compose style guide. The skill is the source of
truth for Compose/CMP defaults.

### How to use it

1. Read existing feature code first (conventions beat generic tutorials).
2. Apply the skill’s core rules (state in ViewModel, dumb UI, unidirectional data).
3. For advanced topics only, open **one** file under
   `.codex/skills/compose-skill/references/` using the skill’s Quick Routing table
   (e.g. `testing.md`, `performance.md`, `cross-platform.md`, `resources.md`).
4. Do **not** load the entire `references/` tree “just in case.”

### VniDrop-specific overrides (compose-skill adapts; do not “fix” these)

| Topic | VniDrop convention |
|--------|-------------------|
| Architecture | **MVVM-style** feature ViewModels: immutable `*State` data class, `StateFlow`, **named public methods** (not a mandatory MVI `onEvent` sealed hierarchy). One-shot UI feedback often via shared snackbar/`UiMessage` rather than a formal Effect channel—match the feature you edit. |
| Packages | `com.vnidrop.app.feature.<send\|receive\|settings\|approvals\|app>` + `ui/*` + `core/*` |
| Route / Screen split | Prefer thin `*Route` (wiring) + `*Screen` / feature composables (render + callbacks). |
| Theme | Use `LocalVniDropColors` / `VniDropThemeTokens` in `shared/.../ui/theme/VniDropTheme.kt`. Do not hard-code one-off brand colors. **Brand primary (light):** HSL `271, 91%, 65%` ≈ `#A855F7`. |
| Strings / drawables | Compose Multiplatform resources (`composeResources`, `Res.string.*`), not Android `R` in `commonMain`. |
| DI | Follow existing `AppGraph` / construction patterns; do not introduce Hilt/Koin migrations unprompted. |
| Platform code | `expect`/`actual` or interfaces under `androidMain` / `iosMain` / `jvmMain`. Android SAF/tree/FDs and iOS security-scoped URLs stay on the platform side. |

Compose-skill’s “Existing Project Policy” applies: preserve working structure.

---

## 4. Code comments

Comments exist for **future readers who already know the language**.

### Do comment

- Non-obvious **why** (concurrency, cancel ordering, durability, security).
- Platform traps (SAF cannot pass directory FDs; security-scoped lease lifetime;
  MediaStore Downloads vs path probes).
- Invariants and failure modes (“exactly one of finish/abort after start_file”).
- Public or crate-boundary contracts that tests rely on.

### Do not comment

- Restating the next line of code (`// increment i`, `// return result`).
- Section banners that only repeat the function name.
- Tutorial-style narration of self-explanatory control flow.
- Large blocks duplicated from `CORE_FLOW.md` or this file.

Prefer a short module/`//!` doc or one high-signal line over many low-signal lines.
Match the density of existing Rust comments in `crates/vnidrop/src/runtime/` and
Kotlin comments on Android expand/share paths.

---

## 5. Architecture map (where to edit)

### Rust core (`crates/vnidrop/src/`)

| Area | Path |
|------|------|
| UniFFI + runtime entry | `runtime/facade.rs` (`VnidropCore`) |
| Share / import | `runtime/share.rs` |
| Receive / export / sinks | `runtime/receive.rs` |
| Cancel / delete / status / access | `runtime/lifecycle.rs` |
| Provider events, send progress per peer | `runtime/provider.rs` |
| Startup, `CoreInner`, emit helpers | `runtime/mod.rs` |
| SQLite | `repository.rs` |
| Paths, import collect, atomic publish | `filesystem.rs` |
| Approval handshake | `approval.rs`, `handshake.rs` |
| Tickets | `ticket.rs` |
| Access policy | `access_policy.rs` |
| Events | `event_hub.rs`, `api.rs` (`CoreEvent`) |

Runtime was split intentionally: **keep modules focused**; do not reassemble a
monolithic `runtime.rs`.

### Shared KMP (`shared/src/`)

| Area | Path |
|------|------|
| Core gateway / models | `commonMain/.../core/` |
| Send UI | `commonMain/.../feature/send/` |
| Receive UI | `commonMain/.../feature/receive/` |
| Approvals | `commonMain/.../feature/approvals/` |
| Settings | `commonMain/.../feature/settings/` |
| Theme / shell / components | `commonMain/.../ui/` |
| Android pickers, SAF, MediaStore, NFC/QR | `androidMain/` |
| iOS pickers, security scope, NFC/QR | `iosMain/` |
| Desktop paths / pickers | `jvmMain/` |

### Platform file rules (summary)

- **Desktop / path-based iOS:** `SourceKind::Path` or security-scoped URL→path;
  directories walked in Rust when `is_directory`.
- **Android share:** open documents as **FDs**; **never** a directory FD.
  Folder share expands SAF trees in Kotlin (`expandShareDirectory`) into
  per-file FDs with relative `displayName` paths.
- **Android receive default:** system Downloads via MediaStore sink when
  available; custom trees via SAF write sink.
- **Receive publish:** no-overwrite temps + hard link / exclusive rename (see
  `CORE_FLOW.md`).

### Progress / multi-receiver

- Send-side byte progress is attributed with `endpoint_id` (and `connection_id`)
  on provider transfer events; UI aggregates via
  `progressForReceiver` / `activeSendProgress` in
  `shared/.../ui/state/AppUiModels.kt`.
- Delivery completion is a separate `delivery` phase / receiver request status.

---

## 6. Testing

### Policy

- **Every bug fix** includes a regression test at the lowest layer that catches it.
- Prefer **deterministic** setups (latches/gates, fixed sizes) over multi-second
  sleeps. If polling is required: short interval + **bounded timeout** + useful
  assertion message.
- Integration tests use the **public** UniFFI API (`tests/support/`), not
  private internals exposed only for tests.
- Failure tests should check durable status and/or events when relevant, not
  only the error string.

### Where tests live

| Layer | Location |
|--------|----------|
| Rust unit / crate-private | `crates/vnidrop/src/tests/` |
| Rust integration | `crates/vnidrop/tests/` + `tests/support/` |
| Shared pure logic | `shared/src/commonTest/` |
| Shared Compose / JVM | `shared/src/jvmTest/` |

See [`crates/vnidrop/tests/README.md`](crates/vnidrop/tests/README.md) for Rust
organization rules.

### Commands agents should run (as relevant)

```bash
# Rust format / lint / tests
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test -p vnidrop

# Shared KMP (desktop JVM suite)
./gradlew :shared:jvmTest
```

CI (path-filtered):

- `.github/workflows/rust-core.yml` — fmt, clippy `-D warnings`, tests
- `.github/workflows/shared-kmp.yml` — `jvmTest` (and related shared paths)

If you change Rust cancel/export concurrency, at least run:

```bash
cargo test -p vnidrop --test output_sink
```

---

## 7. Git and PR conventions

### Branches

Name after **what changes**, not roadmap steps:

- Good: `feat/folder-share`, `fix/cancel-export-hang`, `refactor/split-runtime-module`, `docs/agents-md`
- Bad: `feat/step3-remaining`, `temp`, `wip`

Delete merged feature branches **locally and on origin** after the PR lands;
start the next task from updated `master`.

### Commits

- Prefer conventional style used in history: `feat(scope):`, `fix(scope):`,
  `refactor(scope):`, `docs:`, `style:`, `ci:`.
- Subject focuses on **why / user-visible outcome**, complete sentences in the
  body when needed.
- Keep commits reviewable; do not mix unrelated features.

### Pull requests

- Title matches the main change.
- Summary: short bullets of **what** and **why**.
- **Test plan must be useful for this PR**:
  - Exact commands to run, and/or
  - 1–2 concrete device/UI scenarios that would catch a regression.
- Avoid generic filler (“CI green”, “test everything”) with no commands or
  scenarios.

### Workflow preferences established in this project

- User often requires **say-so before push/PR**.
- Prefer cleaning up remote feature branches after merge.
- When SSH signing fails, unblock with agent unlock—not policy bypass.

---

## 8. Implementation checklist (before finishing a task)

1. Read surrounding code and this file; for UI, load `compose-skill`.
2. Implement the smallest correct change.
3. Add/adjust tests for behavior changes and bug fixes.
4. Run the relevant format/lint/test commands.
5. Comments only where they add non-obvious information.
6. Commit (signed) if asked; push/PR only if asked.
7. Leave a concise summary of what changed and how it was verified.

---

## 9. Anti-patterns (do not)

- Streaming multi-MB transfer data through Kotlin as the primary design.
- Passing Android **directory** FDs into Rust.
- Nested exclusive `Runtime::block_on` patterns that deadlock cancel during
  receive (use the existing handle-based entry / sync cancel signal approach).
- Holding locks across `.await` (Clippy `await_holding_lock` fails CI).
- Rebuilding a single 1.7k-line `runtime.rs`.
- Migrating the whole app to MVI/Hilt/Nav3 “because best practice.”
- Adding dependencies without verifying multiplatform target support.
- Flaky tests that sleep for tens of seconds hoping races resolve.
- Unsigned commits when signing is required, or force-push without consent.

---

## 10. Quick file index for common tasks

| Task | Start here |
|------|------------|
| Share import / multi-file / folders | `runtime/share.rs`, `filesystem.rs`, platform `FileSystemService.*` |
| Receive path / sink export | `runtime/receive.rs`, Android/iOS sinks |
| Cancel / delete / stop share | `runtime/lifecycle.rs`, `facade.rs` cancel path |
| Per-receiver send progress | `runtime/provider.rs`, `AppUiModels.kt`, `TransferDetails.kt` |
| Approvals UI | `feature/approvals/`, `approval.rs` |
| Ticket / QR / NFC | `TransferShareActions.*`, `ReceiveInvitationActions.*` |
| Theme / brand color | `ui/theme/VniDropTheme.kt` |
| Compose skill | `.codex/skills/compose-skill/SKILL.md` |

---

*Last aligned with post–PR #10 tree (`runtime/` split, multi-file/folder share,
iOS QR/NFC, per-receiver progress, cancel-export fixes).*
