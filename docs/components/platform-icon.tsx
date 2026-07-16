import type { SVGProps } from "react";

export const supportedPlatforms = ["Android", "iOS", "macOS", "Windows", "Linux"] as const;

export type PlatformName = (typeof supportedPlatforms)[number];

type PlatformIconProps = Omit<SVGProps<SVGSVGElement>, "children"> & {
  platform: PlatformName;
};

function AndroidIcon() {
  return (
    <>
      <path
        d="M7.25 8.15h9.5a1.5 1.5 0 0 1 1.5 1.5v6.1a1.5 1.5 0 0 1-1.5 1.5h-9.5a1.5 1.5 0 0 1-1.5-1.5v-6.1a1.5 1.5 0 0 1 1.5-1.5Z"
        fill="currentColor"
      />
      <path d="M8.15 8.1a3.95 3.95 0 0 1 7.7 0" fill="currentColor" />
      <path d="m8.35 4.45-1.2-1.7M15.65 4.45l1.2-1.7" stroke="currentColor" strokeWidth="1.25" strokeLinecap="round" />
      <path d="M4.2 10.05v5.15M19.8 10.05v5.15M8.5 17v3.05M15.5 17v3.05" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" />
      <circle cx="9.5" cy="6.65" r="0.62" fill="white" />
      <circle cx="14.5" cy="6.65" r="0.62" fill="white" />
    </>
  );
}

function AppleIcon() {
  return (
    <path
      d="M17.05 20.28c-.98.95-2.05.8-3.08.35-1.09-.46-2.09-.48-3.24 0-1.44.62-2.2.44-3.06-.35C2.79 15.25 3.51 7.59 9.05 7.31c1.32.07 2.24.76 3.01.82 1.15-.23 2.25-.89 3.44-.8 1.43.12 2.51.68 3.22 1.7-2.96 1.78-2.26 5.68.46 6.77-.54 1.42-1.24 2.83-2.13 4.48ZM12.03 7.25c-.15-2.13 1.59-3.89 3.58-4.06.27 2.46-2.23 4.3-3.58 4.06Z"
      fill="currentColor"
    />
  );
}

function MacIcon() {
  return (
    <>
      <rect x="2.5" y="2.5" width="19" height="19" rx="5.5" fill="currentColor" />
      <path d="M12 2.5v19" stroke="white" strokeOpacity="0.72" />
      <path d="M8 9.15h.01M16 9.15h.01" stroke="white" strokeWidth="1.9" strokeLinecap="round" />
      <path d="M7.1 15.2c2.7 1.45 7.1 1.45 9.8 0" stroke="white" strokeWidth="1.35" strokeLinecap="round" />
      <path d="m13.55 6.25-2.45 8.3" stroke="white" strokeWidth="1.1" strokeLinecap="round" strokeOpacity="0.82" />
    </>
  );
}

function WindowsIcon() {
  return (
    <path
      d="M3 5.15 11 4v7H3V5.15Zm9-1.3L21 2.5V11h-9V3.85ZM3 12h8v7l-8-1.15V12Zm9 0h9v8.5l-9-1.35V12Z"
      fill="currentColor"
    />
  );
}

function LinuxIcon() {
  return (
    <>
      <ellipse cx="12" cy="12.6" rx="5.25" ry="7.55" fill="#242027" />
      <ellipse cx="12" cy="14.55" rx="3.45" ry="4.7" fill="#f8f7f9" />
      <ellipse cx="9.95" cy="8.45" rx="1.35" ry="1.8" fill="white" />
      <ellipse cx="14.05" cy="8.45" rx="1.35" ry="1.8" fill="white" />
      <circle cx="10.35" cy="8.65" r="0.55" fill="#242027" />
      <circle cx="13.65" cy="8.65" r="0.55" fill="#242027" />
      <path d="m9.8 10.15 2.2-1 2.2 1-2.2 2.1-2.2-2.1Z" fill="#f5b82e" />
      <path d="M6.15 18.45c1.2-1 2.45-1.3 3.75-.75-.7 1.6-2.35 2.45-4.9 2.2.2-.6.58-1.08 1.15-1.45ZM17.85 18.45c-1.2-1-2.45-1.3-3.75-.75.7 1.6 2.35 2.45 4.9 2.2-.2-.6-.58-1.08-1.15-1.45Z" fill="#f5b82e" />
    </>
  );
}

export function PlatformIcon({ platform, className, ...props }: PlatformIconProps) {
  return (
    <svg
      className={className}
      data-platform={platform.toLowerCase()}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      {platform === "Android" && <AndroidIcon />}
      {platform === "iOS" && <AppleIcon />}
      {platform === "macOS" && <MacIcon />}
      {platform === "Windows" && <WindowsIcon />}
      {platform === "Linux" && <LinuxIcon />}
    </svg>
  );
}
