import Image from "next/image";
import Link from "next/link";
import { Icon, type IconName } from "@/components/icons";
import { PlatformIcon, supportedPlatforms } from "@/components/platform-icon";
import { Reveal } from "@/components/reveal";

const steps: Array<{
  icon: IconName;
  number: string;
  title: string;
  text: string;
}> = [
  {
    icon: "folder",
    number: "01",
    title: "Choose what to send",
    text: "Pick files, a batch, or a complete folder. VniDrop preserves the folder structure.",
  },
  {
    icon: "qr",
    number: "02",
    title: "Share an invitation",
    text: "Introduce the devices with a QR code, NFC tap, or portable .vnd invitation.",
  },
  {
    icon: "shield",
    number: "03",
    title: "Approve and transfer",
    text: "The receiver asks first. Once approved, the files move over an authenticated encrypted connection.",
  },
];

const trustItems: Array<{
  icon: IconName;
  title: string;
  text: string;
}> = [
  {
    icon: "devices",
    title: "Direct when possible",
    text: "Devices connect directly when they can. An encrypted relay forwards traffic when they cannot.",
  },
  {
    icon: "lock",
    title: "No hosted copy",
    text: "A relay is a route, not storage. Your transfer is never turned into a cloud download.",
  },
  {
    icon: "verified",
    title: "Verified on arrival",
    text: "Content addressing checks that the received bytes match exactly what you sent.",
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
                Send files and folders across your devices—direct when possible, private by design,
                and always under your control.
              </p>
              <div className="hero-actions">
                <a className="button button-primary" href="#how-it-works">
                  See how it works
                  <Icon name="arrow" />
                </a>
              </div>
              <div className="hero-platforms" aria-label="Supported platforms">
                <span>Available across</span>
                <ul>
                  {supportedPlatforms.map((platform) => (
                    <li key={platform} aria-label={platform} title={platform}>
                      <PlatformIcon className="platform-glyph" platform={platform} />
                      <span className="sr-only">{platform}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </Reveal>
          </div>
          <Reveal className="hero-art-reveal" delay={120}>
            <figure className="hero-app-preview">
              <Image
                className="hero-app-preview-image"
                src="/App.png"
                width={2338}
                height={1873}
                sizes="(max-width: 920px) calc(100vw - 32px), 56vw"
                alt="VniDrop running on desktop and iPhone, showing transfer review, receiver permissions, and invitation options."
                priority
                unoptimized
              />
            </figure>
          </Reveal>
        </div>
      </section>

      <section id="how-it-works" className="simple-section simple-flow-section">
        <div className="page-shell">
          <Reveal className="simple-heading">
            <span className="kicker">HOW IT WORKS</span>
            <h2>Three steps. No account.</h2>
            <p>
              Choose the files, introduce the devices, and approve the handoff. VniDrop handles the
              secure route.
            </p>
          </Reveal>
          <div className="simple-steps">
            {steps.map((step) => (
              <article key={step.number} className="simple-step">
                <div className="simple-step-top">
                  <span>{step.number}</span>
                  <Icon name={step.icon} />
                </div>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section id="privacy" className="trust-summary-section">
        <div className="page-shell trust-summary">
          <Reveal className="trust-summary-copy">
            <span className="kicker kicker-light">PRIVACY BY DESIGN</span>
            <h2>What VniDrop does—and doesn’t do.</h2>
            <p>
              The transfer happens between devices. These are the details that matter when you
              decide what to share and who can receive it.
            </p>
            <Link className="simple-text-link" href="/privacy/">
              Read the privacy policy
              <Icon name="arrow" />
            </Link>
          </Reveal>
          <div className="trust-summary-list">
            {trustItems.map((item) => (
              <article key={item.title} className="trust-summary-item">
                <span><Icon name={item.icon} /></span>
                <div>
                  <h3>{item.title}</h3>
                  <p>{item.text}</p>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
