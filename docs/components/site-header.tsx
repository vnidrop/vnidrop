import Link from "next/link";
import { Brand } from "@/components/brand";
import { Icon } from "@/components/icons";

export function SiteHeader() {
  return (
    <header className="site-header">
      <div className="header-inner page-shell">
        <Brand />
        <Link
          className="header-github-button"
          href="https://github.com/vnidrop/vnidrop"
          target="_blank"
          rel="noreferrer"
        >
          <Icon name="github" />
          View on GitHub
        </Link>
      </div>
    </header>
  );
}
