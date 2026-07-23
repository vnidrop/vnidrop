# vni — VniDrop command-line client

A terminal client for the VniDrop transfer core. It ships the `vnidrop` binary
and drives the same public `VnidropCore` facade the Android, iOS, and desktop
apps use — so its behaviour tracks the apps rather than reaching into internals.

This crate depends on `vnidrop`; the reverse is never true. CLI-only
dependencies such as `clap` stay here so the core library keeps cross-compiling
for iOS and Android.

## Build

```bash
cargo build -p vni            # produces the `vnidrop` binary
cargo run  -p vni -- --help
```

## Commands

```
vnidrop send    [options] <path>...
vnidrop receive [options] <ticket | path/to/invitation.vnd> [output-dir]
```

### Options

| Flag | Meaning |
| --- | --- |
| `--relay <default\|none\|staging\|<url>...>` | Relay mode. Repeat `--relay` for multiple custom URLs. `none` is local-network only. |
| `--access <approve\|anyone>` | Share access policy (`send`). `approve` prompts per receiver. |
| `--name <string>` | Sender / receiver display name. |
| `--out <path>` | Also write the invitation as a `.vnd` file (`send`). |
| `--json` | Machine-readable JSON Lines output. |
| `-v, --verbose` | Print the core event log. |
| `--data-dir <path>` | Core data directory (identity, history, blobs). Defaults to a per-user CLI directory, overridable with `VNIDROP_DATA_DIR`. |

`send` prints the ticket to stdout. With `--access approve` it prompts to
approve or refuse each incoming receiver.

## Measuring relay usage

Both commands print the startup relay status and, on receive, whether the
payload moved over a `direct` or `relay` path — sampled after connect and again
after the transfer, since a relayed connection can upgrade to direct mid-way:

```
relays: default (connected)
path (connected): direct
path (downloaded): direct
```

## Examples

```bash
# Share a file, no interactive approval, also save the invitation.
vnidrop send --access anyone --out invite.vnd ./report.pdf

# Receive from a saved invitation into ./downloads.
vnidrop receive ./invite.vnd ./downloads

# Local-network-only transfer (no relays; same network required).
vnidrop send --relay none ./photo.jpg
```
