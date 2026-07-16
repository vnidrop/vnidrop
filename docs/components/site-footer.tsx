import Link from "next/link";
import { BrandMark } from "@/components/brand";
import { Icon } from "@/components/icons";

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="footer-inner page-shell">
        <div className="footer-identity">
          <BrandMark />
          <span>VniDrop</span>
        </div>
        <nav className="footer-links" aria-label="Footer navigation">
          <a href="https://github.com/vnidrop/vnidrop" target="_blank" rel="noreferrer">
            <Icon name="github" /> GitHub
          </a>
          <Link href="/privacy/">Privacy</Link>
          <a
            href="https://github.com/vnidrop/vnidrop/blob/master/LICENSE"
            target="_blank"
            rel="noreferrer"
          >
            Apache 2.0
          </a>
        </nav>
      </div>
      <div className="footer-bottom page-shell">
        <p>© 2026 VniDrop contributors.</p>
        <p>Open source · Early development</p>
      </div>
    </footer>
  );
}
