"use client";

import { useState } from "react";
import { Icon, type IconName } from "@/components/icons";

type Method = "qr" | "nfc" | "file";

const methods: Array<{
  id: Method;
  icon: IconName;
  label: string;
  eyebrow: string;
  title: string;
  text: string;
}> = [
  {
    id: "qr",
    icon: "qr",
    label: "QR code",
    eyebrow: "IN THE SAME ROOM",
    title: "Point. Scan. Connected.",
    text: "Show the invitation on one screen and scan it from the other. No contact exchange required.",
  },
  {
    id: "nfc",
    icon: "nfc",
    label: "NFC",
    eyebrow: "THE QUICKEST TAP",
    title: "Bring devices together.",
    text: "Write or open an invitation with an NFC tag for a physical, close-range handoff.",
  },
  {
    id: "file",
    icon: "file",
    label: ".vnd file",
    eyebrow: "SEND IT ANYWHERE",
    title: "An invitation that travels.",
    text: "Save the small invitation as a .vnd file, then pass it through the channel you already use.",
  },
];

function QrArt() {
  const filled = new Set([
    0, 1, 2, 3, 4, 6, 8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 29, 30, 31, 32,
    36, 38, 40, 42, 44, 48, 49, 50, 51, 52, 54, 56, 58, 60, 62, 64, 66, 67, 69,
    72, 73, 74, 76, 78, 79, 80,
  ]);
  return (
    <div className="invite-qr-art">
      <div className="invite-qr-grid">
        {Array.from({ length: 81 }, (_, index) => (
          <span key={index} className={filled.has(index) ? "is-filled" : ""} />
        ))}
      </div>
      <span className="qr-scan-line" />
      <span className="qr-corner qr-corner-one" />
      <span className="qr-corner qr-corner-two" />
      <span className="qr-corner qr-corner-three" />
      <span className="qr-corner qr-corner-four" />
    </div>
  );
}

function NfcArt() {
  return (
    <div className="invite-nfc-art">
      <div className="nfc-device nfc-device-left">
        <span className="nfc-screen-dot" />
      </div>
      <div className="nfc-waves">
        <span />
        <span />
        <span />
        <i />
      </div>
      <div className="nfc-tag">
        <Icon name="nfc" />
        <small>VniDrop</small>
      </div>
    </div>
  );
}

function FileArt() {
  return (
    <div className="invite-file-art">
      <span className="file-art-orbit file-art-orbit-one" />
      <span className="file-art-orbit file-art-orbit-two" />
      <div className="vnd-file">
        <span className="vnd-fold" />
        <Icon name="file" />
        <strong>Weekend trip</strong>
        <small>VniDrop invitation</small>
        <code>.vnd</code>
      </div>
      <div className="file-tray">
        <span />
      </div>
    </div>
  );
}

export function InvitationShowcase() {
  const [active, setActive] = useState<Method>("qr");
  const current = methods.find((method) => method.id === active) ?? methods[0];

  return (
    <div className="invite-showcase">
      <div className="invite-tabs" role="tablist" aria-label="Invitation methods">
        {methods.map((method) => (
          <button
            key={method.id}
            id={`invite-tab-${method.id}`}
            type="button"
            role="tab"
            aria-selected={active === method.id}
            aria-controls="invite-panel"
            className={active === method.id ? "is-active" : ""}
            onClick={() => setActive(method.id)}
          >
            <Icon name={method.icon} />
            {method.label}
          </button>
        ))}
      </div>
      <div
        id="invite-panel"
        className="invite-panel"
        role="tabpanel"
        aria-labelledby={`invite-tab-${active}`}
        key={active}
      >
        <div className="invite-copy">
          <span className="mono-label">{current.eyebrow}</span>
          <h3>{current.title}</h3>
          <p>{current.text}</p>
          <div className="invite-detail">
            <Icon name="lock" />
            <span>
              <strong>The invitation is a private access link.</strong>
              <small>Only share it with people you intend to receive the transfer.</small>
            </span>
          </div>
        </div>
        <div className="invite-art" aria-hidden="true">
          {active === "qr" && <QrArt />}
          {active === "nfc" && <NfcArt />}
          {active === "file" && <FileArt />}
        </div>
      </div>
    </div>
  );
}
