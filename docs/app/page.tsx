import Link from "next/link";
import { ApprovalDemo } from "@/components/approval-demo";
import { HeroIllustration } from "@/components/hero-illustration";
import { Icon, type IconName } from "@/components/icons";
import { InvitationShowcase } from "@/components/invitation-showcase";
import { NetworkIllustration } from "@/components/network-illustration";
import { PlatformOrbit } from "@/components/platform-orbit";
import { Reveal } from "@/components/reveal";
import { TransferFlow } from "@/components/transfer-flow";

const trustItems = [
  { value: "No account", detail: "Open the app and start sharing" },
  { value: "No hosted copy", detail: "Your transfer is not parked in our cloud" },
  { value: "No silent overwrite", detail: "Existing files stay untouched" },
];

const privacyPoints: Array<{
  icon: IconName;
  number: string;
  title: string;
  text: string;
}> = [
  {
    icon: "lock",
    number: "01",
    title: "Encrypted in transit",
    text: "Each connection is authenticated and end-to-end encrypted, including through a relay.",
  },
  {
    icon: "shield",
    number: "02",
    title: "Approval by default",
    text: "An invitation helps devices meet. It is not automatic permission to download.",
  },
  {
    icon: "verified",
    number: "03",
    title: "Verified on arrival",
    text: "Content addressing checks that incoming bytes match exactly what was offered.",
  },
  {
    icon: "stop",
    number: "04",
    title: "Revocable access",
    text: "Stop a share at any time and the invitation can no longer be used.",
  },
];

export default function HomePage() {
  return (
    <main id="main-content">
      <section className="hero-section">
        <div className="hero-ambient hero-ambient-one" aria-hidden="true" />
        <div className="hero-ambient hero-ambient-two" aria-hidden="true" />
        <div className="page-shell hero-layout">
          <div className="hero-copy">
            <Reveal>
              <div className="eyebrow-badge">
                <span className="eyebrow-dot" />
                OPEN SOURCE · EARLY DEVELOPMENT
              </div>
              <h1>
                Your files.
                <br />
                <span>A straight line</span>
                <br />
                between devices.
              </h1>
              <p className="hero-lead">
                Send files and folders directly when possible, with an end-to-end encrypted relay
                when needed. No account. No hosted transfer copy. You decide who receives them.
              </p>
              <div className="hero-actions">
                <a className="button button-primary" href="#how-it-works">
                  See how it works
                  <Icon name="arrow" />
                </a>
                <a
                  className="button button-ghost"
                  href="https://github.com/vnidrop/vnidrop"
                  target="_blank"
                  rel="noreferrer"
                >
                  <Icon name="github" />
                  Explore the project
                </a>
              </div>
              <div className="hero-platforms" aria-label="Supported platforms">
                <span>Available across</span>
                <ul>
                  <li>Android</li>
                  <li>iOS</li>
                  <li>macOS</li>
                  <li>Windows</li>
                  <li>Linux</li>
                </ul>
              </div>
            </Reveal>
          </div>
          <Reveal className="hero-art-reveal" delay={120}>
            <HeroIllustration />
          </Reveal>
        </div>
      </section>

      <section className="trust-section" aria-label="VniDrop principles">
        <div className="page-shell trust-grid">
          {trustItems.map((item, index) => (
            <Reveal key={item.value} className="trust-item" delay={index * 70}>
              <span className="trust-number">0{index + 1}</span>
              <div>
                <strong>{item.value}</strong>
                <p>{item.detail}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      <div className="word-stream" aria-hidden="true">
        <div>
          <span>FILES</span><i>◆</i><span>FOLDERS</span><i>◆</i><span>PHOTOS</span><i>◆</i>
          <span>PROJECTS</span><i>◆</i><span>FILES</span><i>◆</i><span>FOLDERS</span><i>◆</i>
          <span>PHOTOS</span><i>◆</i><span>PROJECTS</span><i>◆</i>
        </div>
      </div>

      <section id="how-it-works" className="section flow-section">
        <div className="page-shell">
          <Reveal className="section-heading section-heading-centered">
            <span className="kicker">ONE DROP, FIVE MOMENTS</span>
            <h2>A handoff that stays in your hands.</h2>
            <p>
              VniDrop keeps the transfer understandable from first selection to final verified file.
            </p>
          </Reveal>
          <Reveal delay={100}>
            <TransferFlow />
          </Reveal>
        </div>
      </section>

      <section id="invitations" className="section invitation-section">
        <div className="page-shell">
          <Reveal className="section-heading section-heading-split">
            <div>
              <span className="kicker">MAKE THE INTRODUCTION</span>
              <h2>One transfer. Three ways to meet.</h2>
            </div>
            <p>
              The invitation is small enough to scan, tap, or send. Your files stay where they are
              until a receiver connects and you allow the transfer.
            </p>
          </Reveal>
          <Reveal delay={100}>
            <InvitationShowcase />
          </Reveal>
        </div>
      </section>

      <section className="section approval-section">
        <div className="page-shell approval-layout">
          <Reveal className="approval-copy">
            <span className="kicker">CONTROL IS BUILT IN</span>
            <h2>A link is not permission.</h2>
            <p className="section-lead">
              Every new receiver knocks by default. Approve people you recognize, refuse the rest,
              and follow progress for each device.
            </p>
            <ul className="check-list">
              <li><Icon name="check" /> See the receiver before anything moves</li>
              <li><Icon name="check" /> Cancel a transfer already in progress</li>
              <li><Icon name="check" /> Stop sharing to revoke future access</li>
            </ul>
            <p className="approval-note">
              Need an open handoff? An optional “Anyone with this transfer” mode is available for
              non-sensitive files.
            </p>
          </Reveal>
          <Reveal className="approval-demo-wrap" delay={120}>
            <ApprovalDemo />
            <span className="demo-hint"><Icon name="spark" /> Try the controls</span>
          </Reveal>
        </div>
      </section>

      <section id="privacy" className="privacy-principles-section">
        <div className="page-shell">
          <Reveal className="privacy-intro">
            <span className="kicker kicker-light">PRIVACY BY DESIGN</span>
            <h2>The file never becomes our file.</h2>
            <p>
              VniDrop coordinates a secure handoff. It does not turn your transfer into a hosted
              download sitting on someone else’s storage.
            </p>
          </Reveal>
          <div className="privacy-point-grid">
            {privacyPoints.map((point, index) => (
              <Reveal key={point.number} className="privacy-point" delay={index * 70}>
                <div className="privacy-point-top">
                  <span><Icon name={point.icon} /></span>
                  <small>{point.number}</small>
                </div>
                <h3>{point.title}</h3>
                <p>{point.text}</p>
              </Reveal>
            ))}
          </div>
          <Reveal className="privacy-policy-link" delay={120}>
            <p>Diagnostics are optional and exclude invitations, file paths, and transfer contents.</p>
            <Link href="/privacy/">
              Read the full privacy policy
              <Icon name="arrow" />
            </Link>
          </Reveal>
        </div>
      </section>

      <section className="network-section">
        <div className="page-shell network-layout">
          <Reveal className="network-copy">
            <span className="kicker kicker-light">DIRECT WHEN POSSIBLE</span>
            <h2>The shortest safe path, chosen automatically.</h2>
            <p>
              VniDrop tries to connect devices directly—even across home routers and mobile
              networks. If that path is not available, a relay forwards the same encrypted
              connection without becoming a file store.
            </p>
            <blockquote>
              <span>“</span>
              The relay can move the envelope. It cannot open it.
            </blockquote>
          </Reveal>
          <Reveal delay={120}>
            <NetworkIllustration />
          </Reveal>
        </div>
      </section>

      <section id="platforms" className="section platforms-section">
        <div className="page-shell platforms-layout">
          <Reveal className="platforms-copy">
            <span className="kicker">ONE FAMILIAR HANDOFF</span>
            <h2>At home on every screen.</h2>
            <p className="section-lead">
              Android, iOS, macOS, Windows, and Linux share one clear transfer experience while each
              platform keeps control of its own file picker and save destination.
            </p>
            <div className="platform-feature-list">
              <span><Icon name="devices" /> Cross-platform by design</span>
              <span><Icon name="folder" /> Native file destinations</span>
              <span><Icon name="code" /> Open-source core</span>
            </div>
          </Reveal>
          <Reveal className="orbit-wrap" delay={100}>
            <PlatformOrbit />
          </Reveal>
        </div>
      </section>

      <section className="final-cta-section">
        <div className="page-shell">
          <Reveal className="final-cta">
            <div className="final-cta-orb final-cta-orb-one" aria-hidden="true" />
            <div className="final-cta-orb final-cta-orb-two" aria-hidden="true" />
            <span className="kicker kicker-light">OPEN SOURCE · APACHE 2.0</span>
            <h2>Drop the cloud holding pen. Keep the control.</h2>
            <p>
              VniDrop is in early development. Explore the code, build the app, and help shape a
              more direct way to share.
            </p>
            <div className="final-cta-actions">
              <a
                className="button button-light"
                href="https://github.com/vnidrop/vnidrop"
                target="_blank"
                rel="noreferrer"
              >
                <Icon name="github" />
                View the source
              </a>
              <Link className="button button-dark-ghost" href="/privacy/">
                Privacy policy
                <Icon name="arrow" />
              </Link>
            </div>
          </Reveal>
        </div>
      </section>
    </main>
  );
}
