use std::path::PathBuf;

use anyhow::{bail, Result};
use clap::{Args, Parser, Subcommand, ValueEnum};

#[derive(Debug, Parser)]
#[command(
    name = "vnidrop",
    about = "Send and receive files with the VniDrop transfer core",
    version
)]
pub struct Cli {
    #[command(subcommand)]
    pub command: Command,
}

#[derive(Debug, Subcommand)]
pub enum Command {
    /// Share files and print an invitation ticket.
    Send(SendArgs),
    /// Receive files from a ticket or a `.vnd` invitation file.
    Receive(ReceiveArgs),
}

#[derive(Debug, Args)]
pub struct CommonArgs {
    /// Relay mode: `default`, `none`, `staging`, or one or more relay URLs.
    #[arg(long = "relay", value_name = "MODE|URL", global = true)]
    pub relay: Vec<String>,

    /// Sender or receiver display name.
    #[arg(long, value_name = "STRING", global = true)]
    pub name: Option<String>,

    /// Machine-readable JSON Lines output.
    #[arg(long, global = true)]
    pub json: bool,

    /// Print the core event log.
    #[arg(short, long, global = true)]
    pub verbose: bool,

    /// Core data directory (identity, history, blob store).
    #[arg(long, value_name = "PATH", global = true)]
    pub data_dir: Option<PathBuf>,
}

#[derive(Debug, Args)]
pub struct SendArgs {
    /// Files or directories to share.
    #[arg(required = true, value_name = "PATH")]
    pub paths: Vec<PathBuf>,

    /// Who may download: `approve` prompts per receiver, `anyone` does not.
    #[arg(long, value_enum, default_value_t = AccessArg::Approve)]
    pub access: AccessArg,

    /// Also write the invitation to this `.vnd` file.
    #[arg(long, value_name = "PATH")]
    pub out: Option<PathBuf>,

    #[command(flatten)]
    pub common: CommonArgs,
}

#[derive(Debug, Args)]
pub struct ReceiveArgs {
    /// A `vnd1:` ticket, or a path to a `.vnd` invitation file.
    #[arg(value_name = "TICKET|FILE")]
    pub invitation: String,

    /// Directory to write received files into (default: current directory).
    #[arg(value_name = "OUTPUT_DIR")]
    pub output_dir: Option<PathBuf>,

    #[command(flatten)]
    pub common: CommonArgs,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, ValueEnum)]
pub enum AccessArg {
    /// Approve or refuse each receiver interactively.
    Approve,
    /// Anyone holding the invitation may download.
    Anyone,
}

/// Relay transport selection, mirroring `iroh::RelayMode` minus the relay map
/// construction (which belongs to the core, not the CLI).
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RelayArg {
    Default,
    Staging,
    Disabled,
    Custom(Vec<String>),
}

/// n0's staging relays, from `iroh::defaults::staging`. The core has no staging
/// variant because staging is just a pair of ordinary relay URLs.
const STAGING_RELAY_URLS: [&str; 2] = [
    "https://use1-1.staging-relay.n0.iroh.link.",
    "https://euc1-1.staging-relay.n0.iroh.link.",
];

impl RelayArg {
    pub fn to_core(&self) -> vnidrop::RelayMode {
        match self {
            Self::Default => vnidrop::RelayMode::Default,
            Self::Disabled => vnidrop::RelayMode::Disabled,
            Self::Staging => vnidrop::RelayMode::Custom {
                urls: STAGING_RELAY_URLS
                    .iter()
                    .map(|url| url.to_string())
                    .collect(),
            },
            Self::Custom(urls) => vnidrop::RelayMode::Custom { urls: urls.clone() },
        }
    }
}

/// Resolves repeated `--relay` values into a single mode.
///
/// Anything that is not a mode keyword is treated as a relay URL. URL validity
/// is the core's decision (`vnidrop::validate_relay_mode`), so the CLI and the
/// apps reject exactly the same input with the same message.
pub fn parse_relay(values: &[String]) -> Result<RelayArg> {
    let mut urls = Vec::new();
    let mut keywords = Vec::new();
    for value in values {
        let value = value.trim();
        match value.to_ascii_lowercase().as_str() {
            "default" => keywords.push(RelayArg::Default),
            "none" | "disabled" => keywords.push(RelayArg::Disabled),
            "staging" => keywords.push(RelayArg::Staging),
            _ => urls.push(value.to_string()),
        }
    }

    let mode = match (keywords.len(), urls.len()) {
        (0, 0) => RelayArg::Default,
        (0, _) => RelayArg::Custom(urls),
        (1, 0) => keywords.remove(0),
        (_, 0) => bail!("--relay accepts only one of default, none, or staging"),
        _ => bail!("--relay cannot mix a relay mode with relay URLs"),
    };
    vnidrop::validate_relay_mode(mode.to_core())?;
    Ok(mode)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn urls(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    #[test]
    fn no_flag_defaults_to_n0_relays() {
        assert_eq!(parse_relay(&[]).unwrap(), RelayArg::Default);
    }

    #[test]
    fn keywords_are_case_insensitive() {
        assert_eq!(parse_relay(&urls(&["NONE"])).unwrap(), RelayArg::Disabled);
        assert_eq!(parse_relay(&urls(&["Staging"])).unwrap(), RelayArg::Staging);
        assert_eq!(parse_relay(&urls(&["default"])).unwrap(), RelayArg::Default);
    }

    #[test]
    fn disabled_accepts_both_spellings() {
        assert_eq!(
            parse_relay(&urls(&["disabled"])).unwrap(),
            RelayArg::Disabled
        );
    }

    #[test]
    fn collects_multiple_custom_urls() {
        let parsed = parse_relay(&urls(&[
            "https://relay-1.example.org",
            "https://relay-2.example.org:8443/path",
        ]))
        .unwrap();
        assert_eq!(
            parsed,
            RelayArg::Custom(urls(&[
                "https://relay-1.example.org",
                "https://relay-2.example.org:8443/path",
            ]))
        );
    }

    #[test]
    fn staging_resolves_to_the_n0_staging_relays() {
        let vnidrop::RelayMode::Custom { urls } = RelayArg::Staging.to_core() else {
            panic!("staging should map to custom relay URLs");
        };
        assert_eq!(urls.len(), 2);
        // Guards against pointing testers at the production relays.
        assert!(
            urls.iter().all(|url| url.contains("staging-relay")),
            "{urls:?}"
        );
    }

    /// URL rules live in the core; this only proves the CLI defers to them.
    #[test]
    fn invalid_urls_are_rejected_by_the_core_validator() {
        let error = parse_relay(&urls(&["nonsense://relay.example.org"])).unwrap_err();
        assert!(error.to_string().contains("unsupported scheme"), "{error}");
    }

    #[test]
    fn rejects_mixing_mode_and_url() {
        let error = parse_relay(&urls(&["none", "https://relay.example.org"])).unwrap_err();
        assert!(error.to_string().contains("cannot mix"), "{error}");
    }

    #[test]
    fn rejects_conflicting_modes() {
        let error = parse_relay(&urls(&["none", "default"])).unwrap_err();
        assert!(error.to_string().contains("only one of"), "{error}");
    }
}
