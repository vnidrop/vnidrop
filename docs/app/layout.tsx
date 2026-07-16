import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import "./base.css";
import "../components/header.css";
import "./home.css";
import "./footer.css";
import "./privacy/privacy-document.css";
import "../components/hero-scene.css";
import "../components/hero-device.css";
import "../components/transfer-flow.css";
import "../components/invitation-scene.css";
import "../components/approval-scene.css";
import "../components/network-scene.css";
import "../components/platform-scene.css";
import "./responsive.css";
import "./privacy/privacy-responsive.css";
import "../components/illustration-motion.css";

export const metadata: Metadata = {
  title: {
    default: "VniDrop — Direct file transfer, on your terms",
    template: "%s · VniDrop",
  },
  description:
    "Send files and folders directly across Android, iOS, macOS, Windows, and Linux—with approval by default and no hosted transfer copy.",
  applicationName: "VniDrop",
  manifest: "/site.webmanifest",
  keywords: [
    "peer-to-peer file transfer",
    "encrypted file sharing",
    "cross-platform file transfer",
    "open source",
  ],
  openGraph: {
    type: "website",
    siteName: "VniDrop",
    title: "VniDrop — Direct file transfer, on your terms",
    description:
      "Move files from your device to theirs, with no account and no hosted transfer copy.",
  },
  twitter: {
    card: "summary",
    title: "VniDrop — Direct file transfer, on your terms",
    description:
      "Move files from your device to theirs, with no account and no hosted transfer copy.",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#fbfafc" },
    { media: "(prefers-color-scheme: dark)", color: "#17131a" },
  ],
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <a className="skip-link" href="#main-content">
          Skip to content
        </a>
        <SiteHeader />
        {children}
        <SiteFooter />
      </body>
    </html>
  );
}
