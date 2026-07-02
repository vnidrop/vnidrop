use std::{
    io::{self, Read, Write},
    path::{Component, Path, PathBuf},
};

use anyhow::{Context, Result};
use bytes::Bytes;
use iroh_blobs::{api::TempTag, Hash};

use crate::{
    api::{ShareSource, SourceKind},
    util::non_empty,
};

const STREAM_BUFFER_LEN: usize = 1024 * 1024;

#[derive(Debug)]
pub(crate) struct TransferImport {
    pub(crate) tag: TempTag,
    pub(crate) root_hash: Hash,
    pub(crate) total_size: u64,
    pub(crate) file_count: u64,
    pub(crate) default_name: String,
}

#[derive(Debug)]
pub(crate) struct ImportSourceFile {
    pub(crate) path: PathBuf,
    pub(crate) collection_name: String,
}

pub(crate) fn collect_import_files(sources: Vec<ShareSource>) -> Result<Vec<ImportSourceFile>> {
    let mut files = Vec::new();
    for source in sources {
        match source.kind {
            SourceKind::Path | SourceKind::IosSecurityScopedUrl => {
                let path = source_path(&source)?;
                let display_name = source
                    .display_name
                    .clone()
                    .and_then(non_empty)
                    .or_else(|| {
                        path.file_name()
                            .and_then(|name| name.to_str())
                            .map(ToOwned::to_owned)
                    })
                    .unwrap_or_else(|| "transfer".to_string());
                if source.is_directory || path.is_dir() {
                    collect_dir_files(&path, &display_name, &mut files)?;
                } else {
                    files.push(ImportSourceFile {
                        path,
                        collection_name: validated_relative_string(&display_name)?,
                    });
                }
            }
            SourceKind::AndroidContentUri => {
                anyhow::bail!(
                    "Android content URI streaming needs platform file descriptor glue before it can be imported without copying"
                );
            }
        }
    }
    if files.is_empty() {
        anyhow::bail!("no files found in selected sources");
    }
    Ok(files)
}

fn source_path(source: &ShareSource) -> Result<PathBuf> {
    if matches!(source.kind, SourceKind::IosSecurityScopedUrl)
        && source.value.starts_with("file://")
    {
        let without_scheme = source.value.trim_start_matches("file://");
        return Ok(PathBuf::from(percent_decode_file_url_path(without_scheme)?));
    }
    Ok(PathBuf::from(&source.value))
}

fn collect_dir_files(
    root: &Path,
    display_name: &str,
    files: &mut Vec<ImportSourceFile>,
) -> Result<()> {
    for entry in walkdir::WalkDir::new(root).follow_links(false) {
        let entry = entry?;
        if !entry.file_type().is_file() {
            continue;
        }
        let relative = entry
            .path()
            .strip_prefix(root)
            .context("failed to compute relative path")?;
        let collection_name = path_to_string(Path::new(display_name).join(relative), true)?;
        files.push(ImportSourceFile {
            path: entry.path().to_path_buf(),
            collection_name,
        });
    }
    Ok(())
}

pub(crate) fn default_collection_name(files: &[ImportSourceFile]) -> String {
    files
        .first()
        .and_then(|file| file.collection_name.split('/').next())
        .filter(|name| !name.is_empty())
        .unwrap_or("transfer")
        .to_string()
}

pub(crate) fn safe_output_path(output_dir: &Path, relative_path: &str) -> Result<PathBuf> {
    let relative = Path::new(relative_path);
    path_to_string(relative, true)?;
    Ok(output_dir.join(relative))
}

pub(crate) fn read_stream_from_blocking_reader<R>(
    mut reader: R,
) -> impl futures::Stream<Item = io::Result<Bytes>> + Send + Sync + 'static
where
    R: Read + Send + 'static,
{
    let (tx, rx) = async_channel::bounded(2);
    std::thread::spawn(move || {
        let mut buffer = vec![0; STREAM_BUFFER_LEN];
        loop {
            match reader.read(&mut buffer) {
                Ok(0) => break,
                Ok(read) => {
                    if tx
                        .send_blocking(Ok(Bytes::copy_from_slice(&buffer[..read])))
                        .is_err()
                    {
                        break;
                    }
                }
                Err(error) => {
                    let _ = tx.send_blocking(Err(error));
                    break;
                }
            }
        }
    });
    rx
}

pub(crate) fn write_stream_to_blocking_writer<W>(
    mut writer: W,
    rx: async_channel::Receiver<io::Result<Option<Bytes>>>,
) -> io::Result<()>
where
    W: Write,
{
    while let Ok(item) = rx.recv_blocking() {
        match item? {
            Some(bytes) => writer.write_all(&bytes)?,
            None => break,
        }
    }
    writer.flush()
}

pub(crate) async fn wait_for_writer(
    task: std::thread::JoinHandle<io::Result<()>>,
) -> Result<io::Result<()>> {
    tokio::task::spawn_blocking(move || {
        task.join()
            .map_err(|_| anyhow::anyhow!("export writer thread panicked"))
    })
    .await?
}

pub(crate) fn validated_relative_string(name: &str) -> Result<String> {
    path_to_string(Path::new(name), true)
}

pub(crate) fn path_to_string(path: impl AsRef<Path>, must_be_relative: bool) -> Result<String> {
    let mut path_str = String::new();
    let parts = path
        .as_ref()
        .components()
        .filter_map(|component| match component {
            Component::Normal(x) => {
                let Some(component) = x.to_str() else {
                    return Some(Err(anyhow::anyhow!("invalid character in path")));
                };
                if !component.contains('/') && !component.contains('\\') {
                    Some(Ok(component))
                } else {
                    Some(Err(anyhow::anyhow!("invalid path component {component:?}")))
                }
            }
            Component::RootDir => {
                if must_be_relative {
                    Some(Err(anyhow::anyhow!("invalid root path component")))
                } else {
                    path_str.push('/');
                    None
                }
            }
            other => Some(Err(anyhow::anyhow!("invalid path component {other:?}"))),
        })
        .collect::<Result<Vec<_>>>()?;
    path_str.push_str(&parts.join("/"));
    Ok(path_str)
}

pub(crate) fn percent_decode_file_url_path(value: &str) -> Result<String> {
    let bytes = value.as_bytes();
    let mut output = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' {
            if i + 2 >= bytes.len() {
                anyhow::bail!("invalid percent escape in file URL");
            }
            let hex = std::str::from_utf8(&bytes[i + 1..i + 3])?;
            output.push(u8::from_str_radix(hex, 16).context("invalid percent escape in file URL")?);
            i += 3;
        } else {
            output.push(bytes[i]);
            i += 1;
        }
    }
    Ok(String::from_utf8(output)?)
}
