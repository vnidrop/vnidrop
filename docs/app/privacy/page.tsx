import type { Metadata } from "next";
import Link from "next/link";
import { BrandMark } from "@/components/brand";
import { Icon, type IconName } from "@/components/icons";
import { Reveal } from "@/components/reveal";

export const metadata: Metadata = {
  title: "Privacy policy",
  description:
    "How VniDrop handles transfers, local app data, optional diagnostics, bug reports, and website visits.",
};

const summaries: Array<{ icon: IconName; label: string; value: string }> = [
  { icon: "devices", label: "Transfers", value: "Device to device" },
  { icon: "globe", label: "Accounts", value: "None" },
  { icon: "shield", label: "Diagnostics", value: "Optional" },
  { icon: "folder", label: "Hosted file copy", value: "None" },
];

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
        <div className="privacy-hero-grid" aria-hidden="true" />
        <div className="page-shell privacy-hero-layout">
          <Reveal className="privacy-hero-copy">
            <Link className="privacy-back" href="/">
              <Icon name="arrow" />
              Back to VniDrop
            </Link>
            <span className="kicker">PRIVACY POLICY</span>
            <h1>Clear by design.<br />Specific by default.</h1>
            <p>
              This policy explains what moves between devices, what stays local, and what is sent
              only when you choose to share diagnostics or a bug report.
            </p>
            <div className="privacy-meta">
              <span>Effective July 16, 2026</span>
              <i />
              <span>Version 1.0</span>
            </div>
          </Reveal>
          <Reveal className="privacy-hero-art" delay={100}>
            <div className="privacy-art-orbit privacy-art-orbit-one" aria-hidden="true" />
            <div className="privacy-art-orbit privacy-art-orbit-two" aria-hidden="true" />
            <div className="privacy-mark-card" aria-hidden="true">
              <span className="privacy-mark-glow" />
              <BrandMark />
              <span className="privacy-mark-lock"><Icon name="lock" /></span>
            </div>
            <div className="privacy-data-pill privacy-data-pill-one" aria-hidden="true">
              <Icon name="file" /> CONTENT
            </div>
            <div className="privacy-data-pill privacy-data-pill-two" aria-hidden="true">
              <Icon name="shield" /> APPROVAL
            </div>
            <div className="privacy-data-pill privacy-data-pill-three" aria-hidden="true">
              <Icon name="verified" /> VERIFIED
            </div>
          </Reveal>
        </div>
      </section>

      <section className="privacy-summary" aria-label="Privacy summary">
        <div className="page-shell privacy-summary-grid">
          {summaries.map((summary, index) => (
            <Reveal key={summary.label} className="privacy-summary-card" delay={index * 60}>
              <span><Icon name={summary.icon} /></span>
              <small>{summary.label}</small>
              <strong>{summary.value}</strong>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="privacy-document-section">
        <div className="page-shell privacy-document-layout">
          <aside className="privacy-toc">
            <p>On this page</p>
            <nav aria-label="Privacy policy sections">
              {sections.map(([id, label], index) => (
                <a key={id} href={`#${id}`}>
                  <span>{String(index + 1).padStart(2, "0")}</span>
                  {label}
                </a>
              ))}
            </nav>
          </aside>

          <article className="privacy-document">
            <div className="privacy-callout">
              <Icon name="spark" />
              <div>
                <strong>The short version</strong>
                <p>
                  VniDrop has no user accounts and does not upload your transfer to a VniDrop file
                  store. Files travel over an authenticated, end-to-end encrypted connection.
                  Product diagnostics are opt-in; a bug report is sent only when you submit one.
                </p>
              </div>
            </div>

            <section id="scope" className="policy-section">
              <span className="policy-number">01</span>
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
              <span className="policy-number">02</span>
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
                <Icon name="shield" />
                <p>
                  Approval is required by default. If the sender selects “Anyone with this transfer,”
                  anyone holding the invitation may receive the files until sharing stops.
                </p>
              </div>
            </section>

            <section id="local-data" className="policy-section">
              <span className="policy-number">03</span>
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
              <span className="policy-number">04</span>
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
              <span className="policy-number">05</span>
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
              <span className="policy-number">06</span>
              <h2>Device permissions</h2>
              <div className="permission-grid">
                <div><Icon name="folder" /><strong>Files & folders</strong><p>Choose what to send and where received files are saved.</p></div>
                <div><Icon name="qr" /><strong>Camera / scanner</strong><p>Scan a QR invitation when you choose that receive method.</p></div>
                <div><Icon name="nfc" /><strong>NFC</strong><p>Read or write an invitation through a compatible NFC tag.</p></div>
                <div><Icon name="devices" /><strong>Network & notifications</strong><p>Connect peers and alert you to background receiver requests.</p></div>
              </div>
              <p>
                VniDrop requests a platform permission only for the related feature. On Android, QR
                scanning may be provided through Google Play services Code Scanner. Platform-level
                permission prompts and service-provider terms also apply.
              </p>
            </section>

            <section id="providers" className="policy-section">
              <span className="policy-number">07</span>
              <h2>Infrastructure and external services</h2>
              <div className="provider-list">
                <div>
                  <span>I</span>
                  <div><strong>Iroh / public relay operators</strong><p>Device discovery, connection establishment, and encrypted relay fallback. Relays process connection metadata but cannot decrypt transfer contents.</p></div>
                </div>
                <div>
                  <span>C</span>
                  <div><strong>Cloudflare</strong><p>The project’s diagnostics design uses Cloudflare Workers, D1, and R2. Cloudflare also processes source IPs for request delivery and abuse controls.</p></div>
                </div>
                <div>
                  <span>G</span>
                  <div><strong>Google Play services</strong><p>May provide the QR code scanner on supported Android devices when you choose to scan an invitation.</p></div>
                </div>
                <div>
                  <span>GH</span>
                  <div><strong>GitHub</strong><p>Hosts the source repository, issue tracker, and external pages linked from this site. GitHub’s own privacy terms apply after you follow those links.</p></div>
                </div>
              </div>
              <p className="provider-links">
                Provider policies: {" "}
                <a href="https://services.iroh.computer/legal/privacy" target="_blank" rel="noreferrer">Iroh</a>, {" "}
                <a href="https://www.cloudflare.com/policies/privacy/" target="_blank" rel="noreferrer">Cloudflare</a>, {" "}
                <a href="https://policies.google.com/privacy" target="_blank" rel="noreferrer">Google</a>, and {" "}
                <a href="https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement" target="_blank" rel="noreferrer">GitHub</a>.
              </p>
            </section>

            <section id="retention" className="policy-section">
              <span className="policy-number">08</span>
              <h2>Retention and deletion</h2>
              <div className="retention-table" role="table" aria-label="Data retention periods">
                <div className="retention-row retention-head" role="row">
                  <span role="columnheader">Data</span><span role="columnheader">Typical retention</span>
                </div>
                <div className="retention-row" role="row">
                  <span role="cell">Transfer history and settings</span><span role="cell">Until you delete them, clear app data, or uninstall</span>
                </div>
                <div className="retention-row" role="row">
                  <span role="cell">Pending local crash reports</span><span role="cell">Up to 30 days and 20 reports; deleted when diagnostics is disabled</span>
                </div>
                <div className="retention-row" role="row">
                  <span role="cell">Server diagnostics and bug reports</span><span role="cell">The current project configuration is 90 days, with scheduled deletion</span>
                </div>
                <div className="retention-row" role="row">
                  <span role="cell">Downloaded files</span><span role="cell">Until you delete them through your operating system</span>
                </div>
              </div>
              <p>
                Operational backups, provider logs, and deletion backlogs may persist briefly beyond
                the stated period where necessary for security, integrity, or legal obligations. If
                the production diagnostics retention configuration changes, this policy should be
                updated to match it.
              </p>
            </section>

            <section id="choices" className="policy-section">
              <span className="policy-number">09</span>
              <h2>Your choices and rights</h2>
              <ul>
                <li>Enable or disable “Share diagnostics” in VniDrop settings.</li>
                <li>Submit a bug report only when you choose, omit contact information, and exclude logs.</li>
                <li>Approve or refuse each receiver, cancel a transfer, or stop sharing.</li>
                <li>Delete individual transfer history or clear completed, failed, and cancelled receive history.</li>
                <li>Delete downloaded files using your operating system, or clear all app data by uninstalling or resetting the app.</li>
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
              <span className="policy-number">10</span>
              <h2>Security</h2>
              <p>
                VniDrop uses authenticated end-to-end encrypted connections, content verification,
                deny-by-default share access, bounded diagnostics payloads, redaction, and safe file
                publishing that avoids silently replacing an existing file. No system can guarantee
                absolute security. Keep invitations private, verify receiver names, keep your device
                updated, and stop sharing when a transfer is finished.
              </p>
              <p>
                Please report a suspected vulnerability through the private process in the {" "}
                <a href="https://github.com/vnidrop/vnidrop/blob/master/SECURITY.md" target="_blank" rel="noreferrer">VniDrop security policy</a>, not in a public issue.
              </p>
            </section>

            <section id="changes" className="policy-section">
              <span className="policy-number">11</span>
              <h2>Changes to this policy</h2>
              <p>
                VniDrop is in early development. Features and data practices may change. When this
                policy changes, we will update the effective date and version at the top of the page
                and publish the revised text with the project. Material changes should be called out
                in release notes or the application where practical.
              </p>
            </section>

            <section id="contact" className="policy-section policy-contact">
              <span className="policy-number">12</span>
              <h2>Contact</h2>
              <p>
                VniDrop is currently maintained as an open-source project and does not yet publish a
                dedicated privacy email or postal address. For a privacy question or request, open a
                request in the project issue tracker. Do not put an invitation, file content,
                credentials, or other sensitive information in a public issue.
              </p>
              <a
                className="button button-primary"
                href="https://github.com/vnidrop/vnidrop/issues/new"
                target="_blank"
                rel="noreferrer"
              >
                Contact the maintainers
                <Icon name="arrow" />
              </a>
            </section>
          </article>
        </div>
      </section>
    </main>
  );
}
