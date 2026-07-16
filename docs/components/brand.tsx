import Link from "next/link";
import Image from "next/image";

type BrandAssetProps = {
  className?: string;
  title?: string;
};

export function BrandMark({ className, title }: BrandAssetProps) {
  return (
    <Image
      className={className}
      src="/brand-mark.svg"
      width={1024}
      height={1024}
      alt={title ?? ""}
      aria-hidden={title ? undefined : true}
      unoptimized
    />
  );
}

export function AppIcon({ className, title }: BrandAssetProps) {
  return (
    <Image
      className={className}
      src="/icon.svg"
      width={1024}
      height={1024}
      alt={title ?? ""}
      aria-hidden={title ? undefined : true}
      unoptimized
    />
  );
}

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <Link className="brand" href="/" aria-label="VniDrop home">
      <BrandMark className="brand-mark-image" />
      {!compact && <span className="brand-name">VniDrop</span>}
    </Link>
  );
}
