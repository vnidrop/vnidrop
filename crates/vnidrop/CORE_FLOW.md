# VniDrop Core Send/Receive Flow

This crate owns the transfer backend. Platform/UI code should pass file handles
or paths into Rust and react to `CoreEvent` updates; it should not move file
bytes through Kotlin memory.

## Send

1. `initialize(app_data_dir, event_sink)` starts the Iroh endpoint, blob
   provider, handshake protocol, SQLite repository, and event hub.
2. `share_files(sources, metadata)` validates platform sources, streams each
   file into `iroh-blobs`, stores a collection, and returns a VniDrop ticket.
3. New VniDrop shares are `ApprovalRequired` by default. A copied ticket is not
   enough to read bytes until the sender approves the receiver endpoint.
4. The sender observes receiver requests through `CoreEvent` entries with
   `phase="approval"` and can query them with
   `list_receiver_requests(transfer_id)`.
5. `respond_receiver_request(request_id, accepted, reason)` accepts or refuses a
   pending request. Accepted requests create a time-limited access session for
   the receiver endpoint.

## Receive

1. `receive(ticket, output_dir, receiver_name)` parses and validates the ticket.
2. VniDrop tickets first connect to the handshake ALPN
   `/vnidrop/handshake/1` and send `RequestTransfer` metadata to the sender.
3. If approved, the receiver connects to the blobs ALPN, downloads the
   collection, and streams files to `output_dir`.
4. If refused, expired, unknown, or cancelled, the receive transfer is marked
   `failed` or `cancelled` and emits an error/lifecycle event.
5. Legacy raw `BlobTicket` values do not carry VniDrop metadata, so they bypass
   the app approval handshake and use the underlying blob ticket directly.

## Core States And Events

- Transfer statuses: `sharing`, `receiving`, `done`, `failed`, `cancelled`,
  `stopped`.
- Main event phases: `endpoint`, `import`, `ticket`, `handshake`, `approval`,
  `access`, `transfer`, `download`, `export`, `lifecycle`, `error`.
- Events are sent to `CoreEventSink` immediately and persisted through the event
  hub. `list_events` flushes queued persistence before reading SQLite.
- `shutdown()` is idempotent and flushes events before stopping the router.

## Platform File Rules

- Desktop uses normal filesystem paths.
- Android opens SAF/content URIs in Kotlin and passes a borrowed file
  descriptor; Rust duplicates the descriptor before streaming.
- iOS starts the security-scoped URL lease in Kotlin and keeps it alive while
  Rust streams from the accessible file URL/path.

## Durability And Filesystem Policy

- SQLite records have a local UUID in addition to the protocol transfer ID.
  Schema-v2 records are migrated in place and keep their tickets and history.
- Imports and receives are recorded before work begins. A process restart marks
  interrupted work failed and expires approval requests that no longer have an
  in-memory responder.
- Persisted shares are restored only when their root collection is complete and
  readable. Missing or corrupt roots fail closed and emit a recovery event.
- Receive destinations use a no-overwrite policy. Rust writes a uniquely named
  temporary file in the destination directory, syncs it, and publishes it with
  a no-clobber hard link when the filesystem supports it. On platforms that
  reject hard links (notably Android emulated external storage), publication
  falls back to an exclusive rename (`renameat2(RENAME_NOREPLACE)` /
  `renamex_np(RENAME_EXCL)`). Failure or cancellation removes the temporary
  file. Stale VniDrop temporary files are cleaned on later writes.
- Android defaults to the app-specific external Downloads directory
  (`getExternalFilesDir`), which is always writable by the process. Shared
  system folders require a SAF tree URI via the folder picker; those receives
  stream through `ReceiveOutputSink` instead of raw filesystem paths.
- Foreign output sinks receive exactly one terminal callback after a successful
  `start_file`: `finish_file` or `abort_file`.

## Blob Retention Policy

Stopping a share immediately removes its provider mapping and approval state,
so neither VniDrop nor legacy blob tickets can read it. Physical blob chunks are
not force-deleted at stop time because content-addressed chunks may be shared by
another active collection. They remain eligible for the blob store's garbage
collection. Restart reconciliation never restores a stopped share.

## Resource Limits

`CoreLimits` controls source count, collection files and bytes, path and ticket
sizes, metadata, retained events, pending approvals, concurrent transfers, and
the event persistence queue. `initialize` uses conservative defaults;
`initialize_with_limits` supports stricter deployments and tests. Cheap limits
are checked before durable or network work, while remote collection limits are
checked before downloading file content.
