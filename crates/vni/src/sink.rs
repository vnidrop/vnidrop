use std::{
    io::Write,
    sync::atomic::{AtomicU64, Ordering},
    sync::Arc,
};

use vnidrop::{CoreEvent, CoreEventSink, ReceiverRequest};

/// Prints core events as human progress lines or JSON Lines.
pub struct PrintingSink {
    json: bool,
    verbose: bool,
    /// Newest total byte count seen for the active transfer, for percentages.
    /// Progress events carry the total only once the size is known.
    total_bytes: AtomicU64,
}

impl PrintingSink {
    pub fn new(json: bool, verbose: bool) -> Arc<Self> {
        Arc::new(Self {
            json,
            verbose,
            total_bytes: AtomicU64::new(0),
        })
    }

    pub fn emit_json(&self, value: &serde_json::Value) {
        if self.json {
            println!("{value}");
        }
    }

    /// Human-only output; suppressed so `--json` stays machine-parseable.
    pub fn note(&self, message: &str) {
        if !self.json {
            println!("{message}");
        }
    }

    pub fn is_json(&self) -> bool {
        self.json
    }

    /// Redraws a single in-place progress line. `total == 0` means unknown.
    fn progress_line(&self, label: &str, current: u64, total: u64) {
        if total > 0 {
            let percent = (current.min(total) as f64 / total as f64) * 100.0;
            print!("\r  {label}: {percent:>5.1}%  {current} / {total} bytes");
        } else {
            print!("\r  {label}: {current} bytes");
        }
        let _ = std::io::stdout().flush();
    }
}

impl CoreEventSink for PrintingSink {
    fn on_event(&self, event: CoreEvent) {
        let data: serde_json::Value =
            serde_json::from_str(&event.data_json).unwrap_or(serde_json::Value::Null);

        if self.json {
            println!(
                "{}",
                serde_json::json!({
                    "type": "event",
                    "phase": event.phase,
                    "kind": event.kind,
                    "transferId": event.transfer_id,
                    "data": data,
                })
            );
            return;
        }

        if self.verbose {
            eprintln!("[{}/{}] {}", event.phase, event.kind, event.data_json);
        }

        // Each phase reports progress with its own payload keys; see
        // runtime/receive.rs and runtime/provider.rs.
        match (event.phase.as_str(), event.kind.as_str()) {
            ("download", "progress") => {
                let downloaded = u64_field(&data, "downloaded");
                if let Some(total) = data.get("total_size").and_then(|value| value.as_u64()) {
                    if total > 0 {
                        self.total_bytes.store(total, Ordering::Relaxed);
                    }
                }
                self.progress_line(
                    "download",
                    downloaded,
                    self.total_bytes.load(Ordering::Relaxed),
                );
            }
            ("export", "progress") => {
                let name = data
                    .get("file_name")
                    .and_then(|value| value.as_str())
                    .unwrap_or("file");
                self.progress_line(
                    &format!("save {name}"),
                    u64_field(&data, "exported"),
                    u64_field(&data, "file_size"),
                );
            }
            // Sender side: per-connection byte offset, with no total to compare.
            ("transfer", "progress") => {
                self.progress_line("send", u64_field(&data, "end_offset"), 0);
            }
            ("transfer", "completed") | ("delivery", _) => {
                println!();
            }
            ("error", _) => eprintln!("error: {}", event.data_json),
            _ => {}
        }
    }
}

fn u64_field(data: &serde_json::Value, key: &str) -> u64 {
    data.get(key).and_then(|value| value.as_u64()).unwrap_or(0)
}

/// Renders a receiver request for the interactive approval prompt.
pub fn describe_request(request: &ReceiverRequest) -> String {
    let who = request
        .receiver_name
        .as_deref()
        .or(request.receiver_device_name.as_deref())
        .unwrap_or("unnamed receiver");
    let endpoint: String = request.remote_endpoint_id.chars().take(12).collect();
    format!("{who} ({endpoint}…) requests \"{}\"", request.transfer_name)
}
