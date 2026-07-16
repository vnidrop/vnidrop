import { Icon } from "@/components/icons";

export function NetworkIllustration() {
  return (
    <div className="network-visual" aria-hidden="true">
      <div className="network-grid" />
      <div className="network-device network-device-left">
        <span className="network-device-top" />
        <Icon name="file" />
        <small>SENDER</small>
      </div>
      <div className="network-device network-device-right">
        <span className="network-device-top" />
        <Icon name="check" />
        <small>RECEIVER</small>
      </div>
      <svg className="network-routes" viewBox="0 0 760 430">
        <defs>
          <linearGradient id="network-route-gradient" x1="115" y1="210" x2="650" y2="210" gradientUnits="userSpaceOnUse">
            <stop stopColor="#D08BFF" />
            <stop offset="0.5" stopColor="#A855F7" />
            <stop offset="1" stopColor="#7C2AEF" />
          </linearGradient>
          <filter id="network-route-glow" x="-30%" y="-100%" width="160%" height="300%">
            <feGaussianBlur stdDeviation="8" />
          </filter>
        </defs>
        <path className="network-direct-glow" d="M134 227C279 92 491 92 626 227" />
        <path className="network-direct" d="M134 227C279 92 491 92 626 227" />
        <path className="network-direct-pulse" d="M134 227C279 92 491 92 626 227" />
        <path className="network-relay-base" d="M134 247c126 87 371 87 492 0" />
        <path className="network-relay-line" d="M134 247c126 87 371 87 492 0" />
      </svg>
      <div className="network-direct-label">
        <span className="live-dot" />
        DIRECT WHEN POSSIBLE
      </div>
      <div className="network-relay-node">
        <span className="relay-ring relay-ring-one" />
        <span className="relay-ring relay-ring-two" />
        <div className="relay-core">
          <Icon name="lock" />
        </div>
        <strong>Encrypted relay</strong>
        <small>Forwards packets · stores no transfer copy</small>
      </div>
      <div className="network-packet network-packet-one"><Icon name="file" /></div>
      <div className="network-packet network-packet-two"><Icon name="file" /></div>
    </div>
  );
}
