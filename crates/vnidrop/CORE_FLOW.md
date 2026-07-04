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
