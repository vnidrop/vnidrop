import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy policy",
  description:
    "How VniDrop handles transfers, local app data, optional diagnostics, bug reports, and website visits.",
};

const sections = [
  ["scope", "Scope"],
  ["transfers", "Transfers"],
  ["local-data", "Local data"],
  ["diagnostics", "Diagnostics"],
  ["website", "Website"],
  ["permissions", "Permissions"],
  ["providers", "Service providers"],
  ["retention", "Retention"],
  ["choices", "Your choices"],
  ["security", "Security"],
  ["changes", "Changes"],
  ["contact", "Contact"],
];

export default function PrivacyPage() {
  return (
    <main id="main-content" className="privacy-page">
      <section className="privacy-hero">
        <div className="page-shell privacy-hero-inner">
          <h1>Privacy Policy</h1>
          <p>
            This policy explains what moves between devices, what stays local, and what is sent
            only when you choose to share diagnostics or a bug report.
          </p>
          <p className="privacy-meta">Effective July 16, 2026 · Version 1.0</p>
        </div>
      </section>

      <section className="privacy-document-section">
        <div className="page-shell privacy-document-layout">
          <aside className="privacy-toc">
            <p>On this page</p>
            <nav aria-label="Privacy policy sections">
              <ol>
                {sections.map(([id, label]) => (
                  <li key={id}>
                    <a href={`#${id}`}>{label}</a>
                  </li>
                ))}
              </ol>
            </nav>
          </aside>

          <article className="privacy-document">
            <div className="privacy-callout">
              <strong>The short version</strong>
              <p>
                VniDrop has no user accounts and does not upload your transfer to a VniDrop file
                store. Files travel over an authenticated, end-to-end encrypted connection.
                Product diagnostics are opt-in; a bug report is sent only when you submit one.
              </p>
            </div>

            <section id="scope" className="policy-section">
              <h2>Scope and who “VniDrop” means</h2>
              <p>
                This policy covers the official VniDrop website, the VniDrop applications for
                Android, iOS, macOS, Windows, and Linux, and the diagnostics service configured by
                the official project. In this policy, “VniDrop,” “we,” and “us” refer to the
                maintainers of the official VniDrop project and the official builds they distribute.
              </p>
              <p>
                VniDrop is open-source software. A build distributed or operated by someone else
                may use different networking infrastructure, diagnostics settings, or website
                hosting. That distributor is responsible for explaining its own practices.
              </p>
            </section>

            <section id="transfers" className="policy-section">
              <h2>What happens during a transfer</h2>
              <h3>File contents</h3>
              <p>
                The sender chooses files or folders on their device. VniDrop streams those bytes to
                an approved receiver and does not first upload them to a VniDrop-hosted storage
                bucket. The receiver saves the files to a destination they choose. Relayed traffic
                remains end-to-end encrypted.
              </p>
              <h3>Invitations and transfer metadata</h3>
              <p>
                A QR code, NFC tag, or <code>.vnd</code> file contains a transfer invitation. The
                invitation includes connection and content identifiers plus transfer metadata such
                as the transfer name, optional sender name, creation time, file count, and total
                size. It is a capability: anyone who receives it may be able to request the transfer
                while the share is active. Treat it like a private access link.
              </p>
              <h3>What peers and relays can see</h3>
              <p>
                A receive request can disclose the receiver’s chosen display or device name,
                application version, and a technical endpoint identifier to the sender. A direct
                connection exposes the peers’ IP addresses to one another. When a public relay is
                used, its operator can observe connection metadata such as source and destination IP
                addresses, connection time, and the amount of relayed data, but cannot read the
                encrypted transfer contents.
              </p>
              <div className="policy-note">
                <p>
                  Approval is required by default. If the sender selects “Anyone with this transfer,”
                  anyone holding the invitation may receive the files until sharing stops.
                </p>
              </div>
            </section>

            <section id="local-data" className="policy-section">
              <h2>Information kept on your device</h2>
              <p>VniDrop stores the information needed to operate the app locally, including:</p>
              <ul>
                <li>device identity and networking keys used to establish secure connections;</li>
                <li>active shares, transfer history, receiver requests, progress, and status;</li>
                <li>app preferences, including access and diagnostics choices;</li>
                <li>download destinations and locally managed transfer data; and</li>
                <li>an anonymous installation identifier used only for diagnostics correlation.</li>
              </ul>
              <p>
                This information remains until you remove the relevant history, stop or delete a
                share, clear the app’s data, or uninstall the app, subject to operating-system file
                behavior. Removing VniDrop history does not delete a file you already downloaded;
                delete that file through your operating system if you no longer want it.
              </p>
            </section>

            <section id="diagnostics" className="policy-section">
              <h2>Optional diagnostics and bug reports</h2>
              <h3>Automatic product diagnostics</h3>
              <p>
                When an official build includes diagnostics, automatic usage events and crash
                reports are disabled until you enable “Share diagnostics.” If enabled, VniDrop may
                send an anonymous installation ID, app version, platform, sparse event names and
                properties, crash type and message, a redacted stack trace, timestamps, and recent
                in-app breadcrumbs. You can turn this off at any time; doing so also removes pending
                local crash reports.
              </p>
              <h3>User-submitted bug reports</h3>
              <p>
                A bug report is separate from the diagnostics toggle and is sent only when you press
                submit. It can contain what you say happened, what you expected, reproduction steps,
                an optional contact email, app and platform versions, an anonymous installation ID,
                device name and model, operating system, network and battery information, recent
                breadcrumbs, and optional recent logs. You can exclude logs before submitting.
              </p>
              <h3>Data deliberately excluded</h3>
              <p>
                Automatic diagnostics are designed to exclude transfer contents, invitations, and
                file paths. Before diagnostic text or optional logs are sent, VniDrop applies rules
                intended to redact invitation tokens, endpoint identifiers, absolute paths, file and
                content URIs, and platform document identifiers. No redaction system is perfect, so
                review anything you type into a bug report and avoid including secrets.
              </p>
            </section>

            <section id="website" className="policy-section">
              <h2>The VniDrop website</h2>
              <p>
                This website is a static product site. It does not provide an account, contact form,
                advertising, behavioral analytics, marketing pixels, or non-essential cookies. It
                does not ask the browser for access to your files, camera, contacts, location, or
                nearby devices.
              </p>
              <p>
                The hosting and security infrastructure may process routine request information—such
                as IP address, time, requested page, referrer, and browser user agent—to deliver the
                site, maintain reliability, and prevent abuse. The live hosting provider must be
                identified in this policy before public deployment if it differs from the providers
                described below.
              </p>
            </section>

            <section id="permissions" className="policy-section">
              <h2>Device permissions</h2>
              <dl className="permission-list">
                <div>
                  <dt>Files &amp; folders</dt>
                  <dd>Choose what to send and where received files are saved.</dd>
                </div>
                <div>
                  <dt>Camera / scanner</dt>
                  <dd>Scan a QR invitation when you choose that receive method.</dd>
                </div>
                <div>
                  <dt>NFC</dt>
                  <dd>Read or write an invitation through a compatible NFC tag.</dd>
                </div>
                <div>
                  <dt>Network &amp; notifications</dt>
                  <dd>Connect peers and alert you to background receiver requests.</dd>
                </div>
              </dl>
              <p>
                VniDrop requests a platform permission only for the related feature. On Android, QR
                scanning may be provided through Google Play services Code Scanner. Platform-level
                permission prompts and service-provider terms also apply.
              </p>
            </section>

            <section id="providers" className="policy-section">
              <h2>Infrastructure and external services</h2>
              <dl className="provider-list">
                <div>
                  <dt>Iroh / public relay operators</dt>
                  <dd>
                    Device discovery, connection establishment, and encrypted relay fallback.
                    Relays process connection metadata but cannot decrypt transfer contents.
                  </dd>
                </div>
                <div>
                  <dt>Cloudflare</dt>
                  <dd>
                    The project’s diagnostics design uses Cloudflare Workers, D1, and R2.
                    Cloudflare also processes source IPs for request delivery and abuse controls.
                  </dd>
                </div>
                <div>
                  <dt>Google Play services</dt>
                  <dd>
                    May provide the QR code scanner on supported Android devices when you choose to
                    scan an invitation.
                  </dd>
                </div>
                <div>
                  <dt>GitHub</dt>
                  <dd>
                    Hosts the source repository, issue tracker, and external pages linked from this
                    site. GitHub’s own privacy terms apply after you follow those links.
                  </dd>
                </div>
              </dl>
              <p className="provider-links">
                Provider policies:{" "}
                <a
                  href="https://services.iroh.computer/legal/privacy"
                  target="_blank"
                  rel="noreferrer"
                >
                  Iroh
                </a>
                ,{" "}
                <a
                  href="https://www.cloudflare.com/policies/privacy/"
                  target="_blank"
                  rel="noreferrer"
                >
                  Cloudflare
                </a>
                ,{" "}
                <a href="https://policies.google.com/privacy" target="_blank" rel="noreferrer">
                  Google
                </a>
                , and{" "}
                <a
                  href="https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement"
                  target="_blank"
                  rel="noreferrer"
                >
                  GitHub
                </a>
                .
              </p>
            </section>

            <section id="retention" className="policy-section">
              <h2>Retention and deletion</h2>
              <div className="retention-table-wrap">
                <table className="retention-table">
                  <caption className="sr-only">Data retention periods</caption>
                  <thead>
                    <tr>
                      <th scope="col">Data</th>
                      <th scope="col">Typical retention</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <th scope="row">Transfer history and settings</th>
                      <td>Until you delete them, clear app data, or uninstall</td>
                    </tr>
                    <tr>
                      <th scope="row">Pending local crash reports</th>
                      <td>Up to 30 days and 20 reports; deleted when diagnostics is disabled</td>
                    </tr>
                    <tr>
                      <th scope="row">Server diagnostics and bug reports</th>
                      <td>The current project configuration is 90 days, with scheduled deletion</td>
                    </tr>
                    <tr>
                      <th scope="row">Downloaded files</th>
                      <td>Until you delete them through your operating system</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p>
                Operational backups, provider logs, and deletion backlogs may persist briefly beyond
                the stated period where necessary for security, integrity, or legal obligations. If
                the production diagnostics retention configuration changes, this policy should be
                updated to match it.
              </p>
            </section>

            <section id="choices" className="policy-section">
              <h2>Your choices and rights</h2>
              <ul>
                <li>Enable or disable “Share diagnostics” in VniDrop settings.</li>
                <li>
                  Submit a bug report only when you choose, omit contact information, and exclude
                  logs.
                </li>
                <li>Approve or refuse each receiver, cancel a transfer, or stop sharing.</li>
                <li>
                  Delete individual transfer history or clear completed, failed, and cancelled
                  receive history.
                </li>
                <li>
                  Delete downloaded files using your operating system, or clear all app data by
                  uninstalling or resetting the app.
                </li>
              </ul>
              <p>
                Depending on where you live, privacy law may provide rights to access, correct,
                delete, restrict, or object to processing of personal information. Because VniDrop
                has no account and automatic diagnostics use an anonymous installation ID, we may
                not be able to connect a server record to you without additional information. Use
                the contact method below and provide only what is needed to locate your submission.
              </p>
            </section>

            <section id="security" className="policy-section">
              <h2>Security</h2>
              <p>
                VniDrop uses authenticated end-to-end encrypted connections, content verification,
                deny-by-default share access, bounded diagnostics payloads, redaction, and safe file
                publishing that avoids silently replacing an existing file. No system can guarantee
                absolute security. Keep invitations private, verify receiver names, keep your device
                updated, and stop sharing when a transfer is finished.
              </p>
              <p>
                Please report a suspected vulnerability through the private process in the{" "}
                <a
                  href="https://github.com/vnidrop/vnidrop/blob/master/SECURITY.md"
                  target="_blank"
                  rel="noreferrer"
                >
                  VniDrop security policy
                </a>
                , not in a public issue.
              </p>
            </section>

            <section id="changes" className="policy-section">
              <h2>Changes to this policy</h2>
              <p>
                VniDrop is in early development. Features and data practices may change. When this
                policy changes, we will update the effective date and version at the top of the page
                and publish the revised text with the project. Material changes should be called out
                in release notes or the application where practical.
              </p>
            </section>

            <section id="contact" className="policy-section policy-contact">
              <h2>Contact</h2>
              <p>
                VniDrop is currently maintained as an open-source project and does not yet publish a
                dedicated privacy email or postal address. For a privacy question or request, open a
                request in the project issue tracker. Do not put an invitation, file content,
                credentials, or other sensitive information in a public issue.
              </p>
              <a
                className="privacy-contact-link"
                href="https://github.com/vnidrop/vnidrop/issues/new"
                target="_blank"
                rel="noreferrer"
              >
                Contact the maintainers
              </a>
            </section>
          </article>
        </div>
      </section>
    </main>
  );
}
