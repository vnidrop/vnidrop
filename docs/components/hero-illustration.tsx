"use client";

import type { CSSProperties, PointerEvent } from "react";
import { useRef } from "react";
import { Icon } from "@/components/icons";

type StageStyle = CSSProperties & {
  "--pointer-x": string;
  "--pointer-y": string;
};

export function HeroIllustration() {
  const stageRef = useRef<HTMLDivElement>(null);

  const updateParallax = (event: PointerEvent<HTMLDivElement>) => {
    const stage = stageRef.current;
    if (!stage || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const bounds = stage.getBoundingClientRect();
    const x = ((event.clientX - bounds.left) / bounds.width - 0.5) * 2;
    const y = ((event.clientY - bounds.top) / bounds.height - 0.5) * 2;
    stage.style.setProperty("--pointer-x", x.toFixed(3));
    stage.style.setProperty("--pointer-y", y.toFixed(3));
  };

  const resetParallax = () => {
    stageRef.current?.style.setProperty("--pointer-x", "0");
    stageRef.current?.style.setProperty("--pointer-y", "0");
  };

  return (
    <figure className="hero-visual">
      <div
        ref={stageRef}
        className="hero-stage"
        style={{ "--pointer-x": "0", "--pointer-y": "0" } as StageStyle}
        onPointerMove={updateParallax}
        onPointerLeave={resetParallax}
        aria-hidden="true"
      >
        <div className="hero-grid" />
        <div className="hero-glow hero-glow-one" />
        <div className="hero-glow hero-glow-two" />
        <div className="hero-status hero-status-secure">
          <span className="status-icon">
            <Icon name="lock" />
          </span>
          <span>
            <strong>End-to-end encrypted</strong>
            <small>Authenticated connection</small>
          </span>
        </div>
        <div className="hero-status hero-status-route">
          <span className="status-pulse" />
          Direct route found
        </div>
        <svg className="hero-scene" viewBox="0 0 760 620">
          <defs>
            <linearGradient id="hero-purple" x1="240" y1="90" x2="620" y2="520">
              <stop stopColor="#C16BFF" />
              <stop offset="0.48" stopColor="#A855F7" />
              <stop offset="1" stopColor="#6F27E9" />
            </linearGradient>
            <linearGradient id="hero-screen" x1="88" y1="118" x2="342" y2="345">
              <stop stopColor="#25202C" />
              <stop offset="1" stopColor="#151319" />
            </linearGradient>
            <linearGradient id="hero-phone" x1="540" y1="160" x2="692" y2="465">
              <stop stopColor="#2A2530" />
              <stop offset="1" stopColor="#141217" />
            </linearGradient>
            <radialGradient id="hero-orb">
              <stop stopColor="#E3BDFF" stopOpacity="0.9" />
              <stop offset="0.45" stopColor="#A855F7" stopOpacity="0.45" />
              <stop offset="1" stopColor="#A855F7" stopOpacity="0" />
            </radialGradient>
            <filter id="hero-shadow" x="-30%" y="-30%" width="160%" height="180%">
              <feDropShadow dx="0" dy="20" stdDeviation="18" floodColor="#3F2454" floodOpacity="0.16" />
            </filter>
            <filter id="hero-glow-filter" x="-100%" y="-100%" width="300%" height="300%">
              <feGaussianBlur stdDeviation="12" />
            </filter>
            <clipPath id="laptop-screen-clip">
              <rect x="82" y="132" width="264" height="176" rx="12" />
            </clipPath>
            <clipPath id="phone-screen-clip">
              <rect x="548" y="171" width="136" height="279" rx="25" />
            </clipPath>
          </defs>

          <g className="scene-orbits">
            <ellipse cx="385" cy="325" rx="315" ry="216" fill="none" stroke="#B969F8" strokeOpacity="0.14" />
            <ellipse cx="385" cy="325" rx="245" ry="169" fill="none" stroke="#B969F8" strokeOpacity="0.12" strokeDasharray="8 14" />
            <circle cx="670" cy="215" r="5" fill="#A855F7" />
            <circle cx="117" cy="455" r="4" fill="#C98AFD" />
            <path d="m704 345 4 10 10 4-10 4-4 10-4-10-10-4 10-4Z" fill="#D9AEFF" />
          </g>

          <g className="scene-laptop" filter="url(#hero-shadow)">
            <rect x="69" y="117" width="290" height="207" rx="19" fill="#E8E4EB" stroke="#D6CFDA" strokeWidth="2" />
            <rect x="80" y="129" width="268" height="183" rx="13" fill="url(#hero-screen)" />
            <g clipPath="url(#laptop-screen-clip)">
              <circle cx="208" cy="235" r="108" fill="#A855F7" opacity="0.07" />
              <path d="M80 278c62-45 99-24 141-60 39-34 70-39 127-31v125H80Z" fill="#A855F7" opacity="0.09" />
              <rect x="101" y="151" width="101" height="13" rx="6.5" fill="#3E3745" />
              <rect x="101" y="175" width="67" height="7" rx="3.5" fill="#5E5366" />
              <g className="laptop-files">
                <rect x="118" y="205" width="76" height="79" rx="10" fill="#F8F2FC" stroke="#C98AFD" />
                <path d="M173 205h21v21Z" fill="#E3C3FA" />
                <rect x="129" y="219" width="24" height="24" rx="6" fill="#E8D0FB" />
                <path d="m135 238 7-8 5 5 4-4 6 7Z" fill="#A855F7" opacity="0.75" />
                <rect x="129" y="253" width="46" height="6" rx="3" fill="#C3B6CB" />
                <rect x="129" y="265" width="34" height="5" rx="2.5" fill="#DDD4E2" />
                <rect x="181" y="223" width="82" height="68" rx="10" fill="#2F2935" stroke="#574D5E" />
                <path d="M242 223h21v21Z" fill="#4F4557" />
                <circle cx="205" cy="245" r="9" fill="#A855F7" opacity="0.75" />
                <rect x="195" y="264" width="49" height="6" rx="3" fill="#63586A" />
                <rect x="195" y="276" width="34" height="5" rx="2.5" fill="#4D4453" />
              </g>
            </g>
            <path d="M46 324h336l-20 23a20 20 0 0 1-15 6H81a20 20 0 0 1-15-7Z" fill="#D9D3DD" stroke="#CAC1CE" strokeWidth="2" />
            <path d="M183 324h62l-7 12h-48Z" fill="#C1B8C6" />
          </g>

          <g className="transfer-conduit">
            <path
              className="conduit-glow"
              d="M284 358c23 134 225 151 279 35"
              fill="none"
              stroke="#B65DF9"
              strokeOpacity="0.2"
              strokeWidth="34"
              strokeLinecap="round"
              filter="url(#hero-glow-filter)"
            />
            <path
              d="M284 358c23 134 225 151 279 35"
              fill="none"
              stroke="#ECE3F1"
              strokeWidth="19"
              strokeLinecap="round"
            />
            <path
              className="conduit-energy"
              d="M284 358c23 134 225 151 279 35"
              fill="none"
              stroke="url(#hero-purple)"
              strokeWidth="7"
              strokeLinecap="round"
              strokeDasharray="2 17"
            />
            <circle cx="284" cy="358" r="25" fill="#F8F2FC" stroke="#C98AFD" strokeWidth="2" />
            <circle cx="284" cy="358" r="13" fill="#A855F7" opacity="0.15" />
            <path d="m276 358 7 7 11-14" fill="none" stroke="#9333EA" strokeWidth="3" strokeLinecap="round" />
          </g>

          <g className="approval-gate" filter="url(#hero-shadow)">
            <rect x="365" y="414" width="151" height="58" rx="18" fill="#FEFCFF" stroke="#DFCDEB" />
            <circle cx="393" cy="443" r="15" fill="#F0DDFE" />
            <path d="M393 433.5 385.5 437v5.3c0 4.5 3.2 7.6 7.5 8.9 4.3-1.3 7.5-4.4 7.5-8.9V437Z" fill="#A855F7" />
            <path d="m389.5 442 2.2 2.2 4.5-4.6" fill="none" stroke="white" strokeWidth="1.8" strokeLinecap="round" />
            <text x="418" y="439" fill="#201B24" fontSize="12" fontWeight="700">Approved</text>
            <text x="418" y="455" fill="#74697B" fontSize="10">Maya’s phone</text>
          </g>

          <g className="scene-phone" filter="url(#hero-shadow)">
            <rect x="536" y="157" width="160" height="309" rx="36" fill="#E4DFE7" stroke="#CFC6D3" strokeWidth="2" />
            <rect x="546" y="168" width="140" height="285" rx="28" fill="url(#hero-phone)" />
            <rect x="587" y="177" width="58" height="8" rx="4" fill="#0B090D" />
            <g clipPath="url(#phone-screen-clip)">
              <circle cx="616" cy="271" r="76" fill="#A855F7" opacity="0.1" />
              <circle cx="616" cy="275" r="49" fill="url(#hero-orb)" opacity="0.55" />
              <g className="received-file">
                <rect x="579" y="224" width="74" height="92" rx="12" fill="#FCF9FD" stroke="#C98AFD" strokeWidth="1.5" />
                <path d="M630 224h23v23Z" fill="#DEC0F6" />
                <rect x="590" y="239" width="26" height="26" rx="7" fill="#E9D2FA" />
                <path d="m595 259 8-9 5 5 5-5 6 9Z" fill="#9E47EF" />
                <rect x="590" y="278" width="46" height="6" rx="3" fill="#C6BACD" />
                <rect x="590" y="291" width="34" height="5" rx="2.5" fill="#DFD6E4" />
              </g>
              <rect x="564" y="337" width="104" height="42" rx="12" fill="#2F2935" stroke="#4B4251" />
              <circle cx="581" cy="358" r="8" fill="#A855F7" />
              <path d="m577.5 358 2.2 2.2 4.5-4.6" fill="none" stroke="white" strokeWidth="1.5" />
              <text x="595" y="355" fill="#FCF9FD" fontSize="10" fontWeight="700">Received</text>
              <text x="595" y="368" fill="#A89EAF" fontSize="8">Verified safely</text>
              <rect x="564" y="393" width="104" height="5" rx="2.5" fill="#3F3745" />
              <rect x="564" y="393" width="104" height="5" rx="2.5" fill="#A855F7" />
            </g>
          </g>

          <g className="transfer-packets">
            <g className="packet packet-one">
              <rect x="0" y="0" width="28" height="34" rx="6" fill="#FCF9FE" stroke="#A855F7" />
              <path d="M19 0h9v9Z" fill="#DDBCF7" />
            </g>
            <g className="packet packet-two">
              <rect x="0" y="0" width="22" height="27" rx="5" fill="#EEE0F9" stroke="#B869F4" />
              <path d="M14 0h8v8Z" fill="#CAA0E9" />
            </g>
            <circle className="packet-spark packet-spark-one" cx="0" cy="0" r="5" fill="#D795FF" />
            <circle className="packet-spark packet-spark-two" cx="0" cy="0" r="3" fill="#A855F7" />
          </g>
        </svg>
        <div className="hero-file-label">
          <span className="file-type-icon">JPG</span>
          <span>
            <strong>summer-photos</strong>
            <small>42 files · structure preserved</small>
          </span>
        </div>
      </div>
      <figcaption className="sr-only">
        An illustration of encrypted files moving from a laptop to a phone after receiver approval.
      </figcaption>
    </figure>
  );
}
