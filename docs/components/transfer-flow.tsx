import { Icon, type IconName } from "@/components/icons";

const steps: Array<{
  number: string;
  icon: IconName;
  title: string;
  text: string;
}> = [
  {
    number: "01",
    icon: "folder",
    title: "Pick anything",
    text: "Choose one file, a whole batch, or a complete folder. Its structure stays intact.",
  },
  {
    number: "02",
    icon: "qr",
    title: "Pass a tiny invitation",
    text: "Share a QR code, tap an NFC tag, or send a portable .vnd invitation file.",
  },
  {
    number: "03",
    icon: "shield",
    title: "Let them knock",
    text: "Every new receiver asks first by default. You choose approve or refuse.",
  },
  {
    number: "04",
    icon: "verified",
    title: "Stream and verify",
    text: "Files move over an authenticated encrypted connection and are verified as they arrive.",
  },
  {
    number: "05",
    icon: "stop",
    title: "Close the door",
    text: "Follow each receiver, cancel in progress, or stop sharing to revoke the invitation.",
  },
];

export function TransferFlow() {
  return (
    <div className="flow-layout">
      <div className="flow-visual" aria-hidden="true">
        <div className="flow-visual-glow" />
        <div className="flow-device flow-device-sender">
          <span className="flow-device-camera" />
          <div className="flow-device-content">
            <span className="flow-mini-label">SELECTED</span>
            <div className="flow-file-stack">
              <span><Icon name="file" /></span>
              <span><Icon name="file" /></span>
              <span><Icon name="file" /></span>
            </div>
            <strong>Project files</strong>
            <small>18 items · 248 MB</small>
          </div>
        </div>
        <svg className="flow-track" viewBox="0 0 480 600">
          <defs>
            <linearGradient id="flow-gradient" x1="90" y1="80" x2="401" y2="520" gradientUnits="userSpaceOnUse">
              <stop stopColor="#C778FF" />
              <stop offset="0.5" stopColor="#A855F7" />
              <stop offset="1" stopColor="#7628EA" />
            </linearGradient>
            <filter id="flow-blur" x="-50%" y="-50%" width="200%" height="200%">
              <feGaussianBlur stdDeviation="18" />
            </filter>
          </defs>
          <path className="flow-track-shadow" d="M132 141c216 14 240 88 153 159-89 73-43 157 81 160" />
          <path className="flow-track-base" d="M132 141c216 14 240 88 153 159-89 73-43 157 81 160" />
          <path className="flow-track-energy" d="M132 141c216 14 240 88 153 159-89 73-43 157 81 160" />
          <circle className="flow-moving-drop" cx="0" cy="0" r="11" fill="url(#flow-gradient)" />
          <circle className="flow-moving-halo" cx="0" cy="0" r="24" fill="#A855F7" opacity="0.18" filter="url(#flow-blur)" />
          <g className="flow-gate">
            <rect x="197" y="274" width="88" height="50" rx="17" fill="#FFFCFF" stroke="#D8B4FE" />
            <path d="m241 286 12 5v8.5c0 7.2-5.1 12.1-12 14.2-6.9-2.1-12-7-12-14.2V291Z" fill="url(#flow-gradient)" />
            <path d="m235.5 299 3.5 3.5 7.5-8" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" />
          </g>
        </svg>
        <div className="flow-device flow-device-receiver">
          <span className="flow-device-camera" />
          <div className="flow-device-content flow-received-content">
            <span className="flow-complete-ring"><Icon name="check" /></span>
            <strong>All received</strong>
            <small>18 items verified</small>
          </div>
        </div>
        <span className="flow-annotation flow-annotation-one">E2E</span>
        <span className="flow-annotation flow-annotation-two">VERIFIED</span>
      </div>
      <ol className="flow-steps">
        {steps.map((step) => (
          <li key={step.number} className="flow-step">
            <div className="flow-step-icon">
              <Icon name={step.icon} />
            </div>
            <div>
              <span className="flow-step-number">{step.number}</span>
              <h3>{step.title}</h3>
              <p>{step.text}</p>
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}
