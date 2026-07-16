"use client";

import Link from "next/link";
import { useState } from "react";
import { Brand } from "@/components/brand";
import { Icon } from "@/components/icons";

const navigation = [
  { href: "/#how-it-works", label: "How it works" },
  { href: "/#privacy", label: "Privacy" },
  { href: "/#platforms", label: "Platforms" },
];

export function SiteHeader() {
  const [open, setOpen] = useState(false);

  return (
    <header className="site-header">
      <div className="header-inner">
        <Brand />
        <nav className="desktop-nav" aria-label="Main navigation">
          {navigation.map((item) => (
            <Link key={item.href} href={item.href}>
              {item.label}
            </Link>
          ))}
        </nav>
        <a
          className="button button-small button-dark header-github"
          href="https://github.com/vnidrop/vnidrop"
          target="_blank"
          rel="noreferrer"
        >
          <Icon name="github" />
          GitHub
        </a>
        <button
          className="menu-button"
          type="button"
          aria-expanded={open}
          aria-controls="mobile-navigation"
          aria-label={open ? "Close navigation" : "Open navigation"}
          onClick={() => setOpen((value) => !value)}
        >
          <Icon name={open ? "close" : "menu"} />
        </button>
      </div>
      <nav
        id="mobile-navigation"
        className={`mobile-nav${open ? " is-open" : ""}`}
        aria-label="Mobile navigation"
      >
        {navigation.map((item) => (
          <Link key={item.href} href={item.href} onClick={() => setOpen(false)}>
            {item.label}
            <Icon name="arrow" />
          </Link>
        ))}
        <a
          href="https://github.com/vnidrop/vnidrop"
          target="_blank"
          rel="noreferrer"
          onClick={() => setOpen(false)}
        >
          Explore on GitHub
          <Icon name="github" />
        </a>
      </nav>
    </header>
  );
}
