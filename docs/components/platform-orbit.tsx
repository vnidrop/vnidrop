import { BrandMark } from "@/components/brand";
import { PlatformIcon, supportedPlatforms } from "@/components/platform-icon";

export function PlatformOrbit() {
  return (
    <div
      className="platform-orbit"
      role="img"
      aria-label="VniDrop supports Android, iOS, macOS, Windows, and Linux"
    >
      <div className="platform-ring platform-ring-outer" aria-hidden="true" />
      <div className="platform-ring platform-ring-inner" aria-hidden="true" />
      <div className="platform-center" aria-hidden="true">
        <span className="platform-center-glow" />
        <BrandMark />
      </div>
      {supportedPlatforms.map((platform, index) => (
        <span
          key={platform}
          className={`platform-chip platform-chip-${index + 1}`}
          title={platform}
        >
          <PlatformIcon className="platform-glyph" platform={platform} />
          <span className="sr-only">{platform}</span>
        </span>
      ))}
    </div>
  );
}
