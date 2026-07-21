//! `vnidrop` — command-line client for the VniDrop transfer core.
//!
//! Drives the same public `VnidropCore` facade the Android, desktop, and Apple
//! apps use, so CLI behaviour tracks shipped behaviour rather than internals.

mod cli;
mod sink;

use std::{
    collections::HashSet,
    io::{BufRead, Write},
    path::{Path, PathBuf},
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

use anyhow::{bail, Context, Result};
use clap::Parser;
use vnidrop::{ShareMetadataInput, ShareSource, SourceKind, TransferAccessMode, VnidropCore};

use crate::{
    cli::{AccessArg, Cli, Command, CommonArgs, ReceiveArgs, RelayArg, SendArgs},
    sink::{describe_request, PrintingSink},
};

const APPROVAL_POLL_INTERVAL: Duration = Duration::from_millis(500);

fn main() -> Result<()> {
    let cli = Cli::parse();
    match cli.command {
        Command::Send(args) => run_send(args),
        Command::Receive(args) => run_receive(args),
    }
}

fn run_send(args: SendArgs) -> Result<()> {
    let relay = cli::parse_relay(&args.common.relay)?;
    let printer = PrintingSink::new(args.common.json, args.common.verbose);
    let core = start_core(&args.common, &relay, &printer)?;

    let sources = args
        .paths
        .iter()
        .map(share_source)
        .collect::<Result<Vec<_>>>()?;

    let access_mode = match args.access {
        AccessArg::Approve => TransferAccessMode::ApprovalRequired,
        AccessArg::Anyone => TransferAccessMode::Public,
    };
    let transfer_id = new_transfer_id();
    let result = core
        .share_files(
            sources,
            ShareMetadataInput {
                transfer_id,
                transfer_name: None,
                sender_name: args.common.name.clone(),
                access_mode,
            },
        )
        .context("failed to share files")?;

    if let Some(out) = &args.out {
        std::fs::write(out, &result.ticket)
            .with_context(|| format!("failed to write invitation to {}", out.display()))?;
    }

    if printer.is_json() {
        printer.emit_json(&serde_json::json!({
            "type": "share",
            "transferId": result.transfer_id,
            "ticket": result.ticket,
            "hash": result.hash,
            "transferName": result.transfer_name,
            "fileCount": result.file_count,
            "totalSize": result.total_size,
            "invitationFile": args.out.as_ref().map(|path| path.display().to_string()),
        }));
    } else {
        printer.note(&format!(
            "Sharing \"{}\" — {} file(s), {} bytes",
            result.transfer_name, result.file_count, result.total_size
        ));
        if let Some(out) = &args.out {
            printer.note(&format!("Invitation written to {}", out.display()));
        }
        printer.note("\nTicket:");
        println!("{}", result.ticket);
        printer.note("");
    }

    match args.access {
        AccessArg::Approve => {
            printer.note("Waiting for receivers. Ctrl-C to stop sharing.");
            approval_loop(&core, transfer_id, &printer)?;
        }
        AccessArg::Anyone => {
            printer.note("Anyone with this invitation may download. Ctrl-C to stop sharing.");
            park();
        }
    }
    Ok(())
}

fn run_receive(args: ReceiveArgs) -> Result<()> {
    let relay = cli::parse_relay(&args.common.relay)?;
    let printer = PrintingSink::new(args.common.json, args.common.verbose);
    let ticket = read_invitation(&args.invitation)?;
    let output_dir = match args.output_dir {
        Some(dir) => dir,
        None => std::env::current_dir().context("failed to resolve the current directory")?,
    };
    std::fs::create_dir_all(&output_dir)
        .with_context(|| format!("failed to create {}", output_dir.display()))?;

    let core = start_core(&args.common, &relay, &printer)?;
    let inspection = core
        .inspect_ticket(ticket.clone())
        .context("failed to read the invitation")?;
    printer.note(&format!(
        "Receiving \"{}\" — {} file(s), {} bytes into {}",
        inspection.metadata.transfer_name,
        inspection.metadata.file_count,
        inspection.metadata.total_size,
        output_dir.display()
    ));

    core.receive(
        ticket,
        output_dir.to_string_lossy().to_string(),
        args.common.name.clone(),
    )
    .context("receive failed")?;

    if printer.is_json() {
        printer.emit_json(&serde_json::json!({
            "type": "receive",
            "transferId": inspection.metadata.transfer_id,
            "transferName": inspection.metadata.transfer_name,
            "outputDir": output_dir.display().to_string(),
        }));
    } else {
        printer.note("\nDone.");
    }
    core.shutdown();
    Ok(())
}

/// Prompts for each new receiver request until interrupted.
fn approval_loop(core: &VnidropCore, transfer_id: u64, printer: &PrintingSink) -> Result<()> {
    let mut handled: HashSet<String> = HashSet::new();
    loop {
        let requests = core
            .list_receiver_requests(transfer_id)
            .context("failed to list receiver requests")?;
        for request in requests {
            if request.status != "requested" || !handled.insert(request.id.clone()) {
                continue;
            }
            let accepted = if printer.is_json() {
                // Non-interactive: a JSON consumer cannot answer a prompt.
                bail!("--json cannot be combined with --access approve; use --access anyone");
            } else {
                prompt_yes_no(&format!("\n{} — approve?", describe_request(&request)))?
            };
            core.respond_receiver_request(request.id.clone(), accepted, None)
                .context("failed to respond to the receiver request")?;
            printer.note(if accepted { "Approved." } else { "Refused." });
        }
        std::thread::sleep(APPROVAL_POLL_INTERVAL);
    }
}

fn prompt_yes_no(question: &str) -> Result<bool> {
    let mut stdout = std::io::stdout();
    loop {
        print!("{question} [y/n] ");
        stdout.flush()?;
        let mut answer = String::new();
        if std::io::stdin().lock().read_line(&mut answer)? == 0 {
            // stdin closed: fail closed rather than granting access.
            return Ok(false);
        }
        match answer.trim().to_ascii_lowercase().as_str() {
            "y" | "yes" => return Ok(true),
            "n" | "no" => return Ok(false),
            _ => println!("Please answer y or n."),
        }
    }
}

fn start_core(
    common: &CommonArgs,
    relay: &RelayArg,
    printer: &Arc<PrintingSink>,
) -> Result<Arc<VnidropCore>> {
    let data_dir = resolve_data_dir(common.data_dir.clone())?;
    let core = VnidropCore::initialize_with_options(
        data_dir.to_string_lossy().to_string(),
        printer.clone() as Arc<dyn vnidrop::CoreEventSink>,
        vnidrop::default_core_limits(),
        relay.to_core(),
    )
    .context("failed to initialize the VniDrop core")?;
    Ok(core)
}

/// Keeps CLI state out of the desktop app's data directory.
fn resolve_data_dir(explicit: Option<PathBuf>) -> Result<PathBuf> {
    if let Some(dir) = explicit {
        return Ok(dir);
    }
    if let Ok(dir) = std::env::var("VNIDROP_DATA_DIR") {
        return Ok(PathBuf::from(dir));
    }
    let home = std::env::var("HOME").context("HOME is not set; pass --data-dir")?;
    let base = if cfg!(target_os = "macos") {
        PathBuf::from(home).join("Library/Application Support")
    } else {
        std::env::var("XDG_DATA_HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|_| PathBuf::from(home).join(".local/share"))
    };
    Ok(base.join("vnidrop-cli"))
}

/// A `.vnd` invitation is the ticket string written as UTF-8.
fn read_invitation(value: &str) -> Result<String> {
    let path = Path::new(value);
    if path.is_file() {
        let contents = std::fs::read_to_string(path)
            .with_context(|| format!("failed to read {}", path.display()))?;
        return Ok(contents.trim().to_string());
    }
    Ok(value.trim().to_string())
}

fn share_source(path: &PathBuf) -> Result<ShareSource> {
    let metadata =
        std::fs::metadata(path).with_context(|| format!("cannot read {}", path.display()))?;
    Ok(ShareSource {
        kind: SourceKind::Path,
        value: path.to_string_lossy().to_string(),
        display_name: path
            .file_name()
            .map(|name| name.to_string_lossy().to_string()),
        is_directory: metadata.is_dir(),
    })
}

fn new_transfer_id() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|elapsed| elapsed.as_nanos() as u64)
        .unwrap_or(1)
}

fn park() -> ! {
    loop {
        std::thread::sleep(Duration::from_secs(3600));
    }
}
