import Link from "next/link";

type BrandMarkProps = {
  className?: string;
  title?: string;
};

export function BrandMark({ className, title }: BrandMarkProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 64 64"
      fill="none"
      role={title ? "img" : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      <path
        d="M11 8h10.6a4.8 4.8 0 0 1 4.8 4.8v25.4c0 8.7 6.1 15.2 14.5 15.2 8.3 0 14.1-6.5 14.1-15.2V12.8A4.8 4.8 0 0 1 59.8 8H62v30.4C62 52.5 53.3 61 40.9 61 28.3 61 19 52.5 19 38.4V23h-3.5v-6.4H11V8Z"
        fill="url(#brand-mark-gradient)"
      />
      <path
        d="M40.8 25.9c-3.1 4.1-8.2 10.8-8.2 15.5 0 5.1 3.6 8.9 8.2 8.9 4.8 0 8.3-3.8 8.3-8.9 0-4.7-5.2-11.4-8.3-15.5Z"
        fill="url(#brand-drop-gradient)"
      />
      <path d="M54.9 8h7v7l-7-7Z" fill="#E8CDFF" />
      <defs>
        <linearGradient
          id="brand-mark-gradient"
          x1="11"
          y1="8"
          x2="64"
          y2="55"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#B45CFA" />
          <stop offset="0.5" stopColor="#9D4DF4" />
          <stop offset="1" stopColor="#7828ED" />
        </linearGradient>
        <linearGradient
          id="brand-drop-gradient"
          x1="32"
          y1="26"
          x2="51"
          y2="47"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#C06CFF" />
          <stop offset="1" stopColor="#7828ED" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <Link className="brand" href="/" aria-label="VniDrop home">
      <span className="brand-mark-shell">
        <BrandMark />
      </span>
      {!compact && <span className="brand-name">VniDrop</span>}
    </Link>
  );
}
