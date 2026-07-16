import { BrandMark } from "@/components/brand";

const platforms = ["Android", "iOS", "macOS", "Windows", "Linux"];

export function PlatformOrbit() {
  return (
    <div className="platform-orbit" aria-label="VniDrop supports Android, iOS, macOS, Windows, and Linux">
      <div className="platform-ring platform-ring-outer" aria-hidden="true" />
      <div className="platform-ring platform-ring-inner" aria-hidden="true" />
      <div className="platform-center" aria-hidden="true">
        <span className="platform-center-glow" />
        <BrandMark />
      </div>
      {platforms.map((platform, index) => (
        <span
          key={platform}
          className={`platform-chip platform-chip-${index + 1}`}
        >
          <i />
          {platform}
        </span>
      ))}
    </div>
  );
}
