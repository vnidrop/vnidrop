import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import "./base.css";
import "../components/header.css";
import "./home.css";
import "./footer.css";
import "./privacy/privacy-document.css";
import "./responsive.css";
import "./privacy/privacy-responsive.css";

const configuredSiteUrl =
  process.env.NEXT_PUBLIC_SITE_URL ??
  process.env.VERCEL_PROJECT_PRODUCTION_URL ??
  process.env.VERCEL_URL ??
  "http://localhost:3000";

const metadataBase = new URL(
  configuredSiteUrl.startsWith("http") ? configuredSiteUrl : `https://${configuredSiteUrl}`,
);

export const metadata: Metadata = {
  metadataBase,
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
    images: [
      {
        url: "/og.png",
        width: 1200,
        height: 630,
        alt: "VniDrop — Your files, a straight line between devices.",
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    title: "VniDrop — Direct file transfer, on your terms",
    description:
      "Move files from your device to theirs, with no account and no hosted transfer copy.",
    images: [
      {
        url: "/og.png",
        alt: "VniDrop — Your files, a straight line between devices.",
      },
    ],
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
