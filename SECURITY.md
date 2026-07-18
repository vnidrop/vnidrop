# VniDrop Security Policy

VniDrop transfers files directly between devices and treats invitations as
capabilities. Security reports are taken seriously, especially when they affect
transfer authorization, file integrity, peer privacy, or the handling of local
files.

## Supported Versions

VniDrop is currently pre-release software and does not yet have tagged stable
releases. Security fixes are developed for the latest commit on `master`.

| Version | Supported |
|---------|-----------|
| Latest `master` | Yes |
| Older commits and unofficial builds | No |

Before reporting an issue, check whether it is reproducible on the latest
`master` when it is safe to do so. This table will be updated when versioned
releases are published.

## Reporting a Vulnerability

Do not disclose a suspected vulnerability, proof of concept, invitation ticket,
private file, or sensitive log in a public issue or discussion.

To make a private report:

1. Contact a VniDrop maintainer privately using the contact information on their
   GitHub profile.
2. If no private contact method is available, open a public issue titled
   **Security contact request**. Include no vulnerability details. A maintainer
   will arrange a private channel for the report.

Code of Conduct incidents should instead follow the private reporting process in
[`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).

### What to Include

Provide as much of the following information as you safely can:

- A concise description of the vulnerability and its potential impact
- The affected commit, platform, and application version
- Reproduction steps or a minimal proof of concept using test data
- Required access, permissions, or user interaction
- Relevant logs or screenshots with tickets, paths, endpoint identifiers,
  credentials, and personal data redacted
- Any suggested mitigation or fix
- Whether the issue has been disclosed to anyone else

Do not attach real user files or reusable invitation tickets. Generate isolated
test fixtures where possible.

## Security-Sensitive Areas

Reports are especially useful when they involve:

- Bypassing transfer approval, access policy, or provider authorization
- Forging, leaking, replaying, or incorrectly accepting invitation tickets
- Serving blobs that are not registered for an active share
- Path traversal, symlink attacks, file overwrite, or unsafe temporary-file
  publication
- Unsafe handling of Android file descriptors, SAF permissions, or iOS
  security-scoped resources
- Remote code execution, memory-safety failures, or denial of service caused by
  untrusted peer input
- Exposure of file contents, local paths, tickets, endpoint identifiers,
  credentials, or other sensitive values through diagnostics or logs
- Authentication, authorization, or data-isolation failures in the diagnostics
  service

The transfer protocol and file-publication invariants are documented in
[`crates/vnidrop/CORE_FLOW.md`](crates/vnidrop/CORE_FLOW.md).

## Coordinated Disclosure

After receiving a report, maintainers will aim to:

1. Confirm receipt and establish a private communication channel.
2. Reproduce and assess the issue, including affected platforms and versions.
3. Develop and verify a fix without weakening existing security boundaries.
4. Coordinate the release and public disclosure with the reporter.
5. Credit the reporter if they want public acknowledgement.

Response and remediation times depend on severity and complexity. Please allow
maintainers a reasonable opportunity to investigate and release a fix before
publishing technical details.

## Research Guidelines

When investigating VniDrop:

- Use devices, accounts, files, and peers that you own or have permission to
  test.
- Minimize access to personal data and stop testing if you encounter data that
  does not belong to you.
- Avoid privacy violations, service disruption, data destruction, and testing
  that affects other users.
- Keep vulnerability details confidential until disclosure is coordinated.
- Follow applicable laws and the project [Code of Conduct](CODE_OF_CONDUCT.md).

Thank you for helping keep VniDrop and its users safe.
