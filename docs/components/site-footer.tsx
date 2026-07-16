import Link from "next/link";
import { BrandMark } from "@/components/brand";
import { Icon } from "@/components/icons";

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="footer-main page-shell">
        <div className="footer-brand">
          <span className="footer-mark">
            <BrandMark />
          </span>
          <div>
            <p className="footer-name">VniDrop</p>
            <p>Send files directly. Stay in control.</p>
          </div>
        </div>
        <div className="footer-links">
          <div>
            <p className="footer-label">Explore</p>
            <Link href="/#how-it-works">How it works</Link>
            <Link href="/#privacy">Privacy by design</Link>
            <Link href="/#platforms">Platforms</Link>
          </div>
          <div>
            <p className="footer-label">Project</p>
            <a href="https://github.com/vnidrop/vnidrop" target="_blank" rel="noreferrer">
              GitHub <Icon name="github" />
            </a>
            <a
              href="https://github.com/vnidrop/vnidrop/blob/master/LICENSE"
              target="_blank"
              rel="noreferrer"
            >
              Apache 2.0 <Icon name="arrow" />
            </a>
            <Link href="/privacy/">Privacy policy</Link>
          </div>
        </div>
      </div>
      <div className="footer-bottom page-shell">
        <p>© 2026 VniDrop contributors.</p>
        <p>Open source · Early development</p>
      </div>
    </footer>
  );
}
