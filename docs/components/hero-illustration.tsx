"use client";

import type { CSSProperties, PointerEvent } from "react";
import { useRef } from "react";
import { motion, useReducedMotion } from "motion/react";
import { BrandMark } from "@/components/brand";
import { Icon } from "@/components/icons";

type StageStyle = CSSProperties & {
  "--pointer-x": string;
  "--pointer-y": string;
};

const cycle = 10.5;
const rest = 1.2;

export function HeroIllustration() {
  const stageRef = useRef<HTMLDivElement>(null);
  const reduceMotion = useReducedMotion();

  const updateParallax = (event: PointerEvent<HTMLDivElement>) => {
    const stage = stageRef.current;
    if (!stage || reduceMotion) return;
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

  const repeating = { duration: cycle, repeat: Infinity, repeatDelay: rest };

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
        <div className="hero-stage-grid" />
        <div className="hero-stage-glow" />
        <span className="stage-coordinate stage-coordinate-top">NODE / SEND · 01</span>
        <span className="stage-coordinate stage-coordinate-side">SECURE CHANNEL</span>

        <div className="desktop-product-shell">
          <div className="desktop-product-window">
            <div className="desktop-window-bar">
              <span className="window-controls"><i /><i /><i /></span>
              <span className="window-title">VniDrop · Send</span>
              <span className="window-security"><Icon name="lock" /> Encrypted</span>
            </div>

            <div className="desktop-app-layout">
              <aside className="desktop-sidebar">
                <BrandMark className="product-brand-mark" />
                <span className="desktop-sidebar-item is-active"><Icon name="file" /></span>
                <span className="desktop-sidebar-item"><Icon name="devices" /></span>
                <span className="desktop-sidebar-item"><Icon name="shield" /></span>
                <span className="desktop-sidebar-spacer" />
                <span className="desktop-profile">AH</span>
              </aside>

              <div className="desktop-transfer-view">
                <div className="desktop-view-heading">
                  <div>
                    <small>OUTGOING TRANSFER</small>
                    <strong>Summer archive</strong>
                  </div>
                  <span className="sharing-pill"><i /> Sharing</span>
                </div>

                <div className="file-collection-card">
                  <div className="file-collage" aria-hidden="true">
                    <span /><span /><span /><span />
                  </div>
                  <div className="file-collection-copy">
                    <strong>summer-photos</strong>
                    <span>42 files · 486 MB</span>
                    <small><Icon name="folder" /> Structure preserved</small>
                  </div>
                  <span className="collection-menu">•••</span>
                </div>

                <div className="receiver-section-heading">
                  <span>Receivers</span>
                  <small>1 connected</small>
                </div>

                <div className="desktop-receiver-row">
                  <span className="receiver-avatar">M</span>
                  <span className="receiver-identity">
                    <strong>Maya&apos;s iPhone</strong>
                    <small>iOS · nearby device</small>
                  </span>
                  <span className="receiver-state-stack">
                    <motion.span
                      className="receiver-row-state is-waiting"
                      animate={
                        reduceMotion
                          ? { opacity: 0 }
                          : { opacity: [1, 1, 0, 0] }
                      }
                      transition={{ ...repeating, times: [0, 0.4, 0.48, 1] }}
                    >
                      Approval required
                    </motion.span>
                    <motion.span
                      className="receiver-row-state is-sending"
                      animate={
                        reduceMotion
                          ? { opacity: 0 }
                          : { opacity: [0, 0, 1, 1, 0, 0] }
                      }
                      transition={{ ...repeating, times: [0, 0.43, 0.5, 0.71, 0.78, 1] }}
                    >
                      Sending securely
                    </motion.span>
                    <motion.span
                      className="receiver-row-state is-complete"
                      animate={
                        reduceMotion
                          ? { opacity: 1 }
                          : { opacity: [0, 0, 1, 1] }
                      }
                      transition={{ ...repeating, times: [0, 0.72, 0.79, 1] }}
                    >
                      <Icon name="verified" /> Verified
                    </motion.span>
                  </span>
                  <div className="receiver-progress-track">
                    <motion.span
                      animate={
                        reduceMotion
                          ? { scaleX: 1 }
                          : { scaleX: [0, 0, 0.04, 0.72, 1, 1] }
                      }
                      transition={{ ...repeating, times: [0, 0.46, 0.51, 0.67, 0.76, 1] }}
                    />
                  </div>
                </div>

                <div className="desktop-transfer-foot">
                  <span><Icon name="shield" /> Approval by default</span>
                  <span><Icon name="lock" /> End-to-end encrypted</span>
                </div>
              </div>
            </div>

            <motion.div
              className="approval-knock-card"
              animate={
                reduceMotion
                  ? { opacity: 0, y: 8, scale: 0.98 }
                  : {
                      opacity: [0, 0, 1, 1, 0, 0],
                      y: [12, 12, 0, 0, -5, -5],
                      scale: [0.97, 0.97, 1, 1, 0.985, 0.985],
                    }
              }
              transition={{ ...repeating, times: [0, 0.24, 0.3, 0.42, 0.49, 1], ease: "easeInOut" }}
            >
              <div className="approval-knock-top">
                <span className="receiver-avatar">M</span>
                <span>
                  <small>NEW RECEIVER</small>
                  <strong>Maya&apos;s iPhone</strong>
                </span>
                <span className="approval-lock"><Icon name="lock" /></span>
              </div>
              <p>Wants to receive <strong>summer-photos</strong></p>
              <div className="approval-knock-actions">
                <span>Refuse</span>
                <motion.span
                  className="approval-accept"
                  animate={reduceMotion ? undefined : { scale: [1, 1, 1.055, 1, 1] }}
                  transition={{ ...repeating, times: [0, 0.38, 0.42, 0.46, 1] }}
                >
                  <Icon name="check" /> Approve
                </motion.span>
              </div>
            </motion.div>
          </div>
        </div>

        <svg className="hero-conduit" viewBox="0 0 760 620" fill="none">
          <defs>
            <linearGradient id="conduit-gradient" x1="410" y1="340" x2="650" y2="390" gradientUnits="userSpaceOnUse">
              <stop stopColor="#C084FC" />
              <stop offset="1" stopColor="#7C2AEF" />
            </linearGradient>
            <filter id="conduit-blur" x="-30%" y="-80%" width="160%" height="260%">
              <feGaussianBlur stdDeviation="8" />
            </filter>
          </defs>
          <path
            d="M414 342C480 344 508 392 571 390C606 389 624 373 646 351"
            stroke="#D9D0DE"
            strokeWidth="14"
            strokeLinecap="round"
          />
          <motion.path
            d="M414 342C480 344 508 392 571 390C606 389 624 373 646 351"
            stroke="#A855F7"
            strokeOpacity="0.24"
            strokeWidth="28"
            strokeLinecap="round"
            filter="url(#conduit-blur)"
            animate={
              reduceMotion
                ? { opacity: 0.55, pathLength: 1 }
                : { opacity: [0.05, 0.05, 0.55, 0.55, 0.15], pathLength: [0, 0, 1, 1, 1] }
            }
            transition={{ ...repeating, times: [0, 0.46, 0.7, 0.82, 1], ease: "easeInOut" }}
          />
          <motion.path
            d="M414 342C480 344 508 392 571 390C606 389 624 373 646 351"
            stroke="url(#conduit-gradient)"
            strokeWidth="6"
            strokeLinecap="round"
            animate={
              reduceMotion
                ? { pathLength: 1, opacity: 0.85 }
                : { pathLength: [0, 0, 1, 1, 1], opacity: [0.25, 0.25, 1, 1, 0.45] }
            }
            transition={{ ...repeating, times: [0, 0.46, 0.7, 0.82, 1], ease: "easeInOut" }}
          />
          <motion.circle
            r="8"
            fill="#FCF9FF"
            stroke="#A855F7"
            strokeWidth="3"
            animate={
              reduceMotion
                ? { cx: 646, cy: 351, opacity: 0 }
                : {
                    cx: [414, 414, 448, 505, 570, 620, 646, 646],
                    cy: [342, 342, 348, 382, 390, 372, 351, 351],
                    opacity: [0, 0, 1, 1, 1, 1, 0, 0],
                    scale: [0.7, 0.7, 1, 1, 1, 1, 0.7, 0.7],
                  }
            }
            transition={{ ...repeating, times: [0, 0.49, 0.53, 0.59, 0.65, 0.7, 0.74, 1], ease: "easeInOut" }}
          />
          <motion.circle
            r="5"
            fill="#D8B4FE"
            animate={
              reduceMotion
                ? { cx: 646, cy: 351, opacity: 0 }
                : {
                    cx: [414, 414, 448, 505, 570, 620, 646, 646],
                    cy: [342, 342, 348, 382, 390, 372, 351, 351],
                    opacity: [0, 0, 1, 1, 1, 1, 0, 0],
                  }
            }
            transition={{ ...repeating, times: [0, 0.56, 0.6, 0.66, 0.72, 0.77, 0.81, 1], ease: "easeInOut" }}
          />
        </svg>

        <div className="receiver-phone-shell">
          <span className="phone-side-button phone-side-button-one" />
          <span className="phone-side-button phone-side-button-two" />
          <div className="receiver-phone-frame">
            <div className="phone-screen">
              <div className="phone-status-bar">
                <span>9:41</span>
                <span className="phone-island" />
                <span className="phone-signal">● ◒</span>
              </div>
              <div className="phone-app-bar">
                <BrandMark className="phone-brand-mark" />
                <strong>Receive</strong>
                <span><Icon name="shield" /></span>
              </div>
              <div className="phone-transfer-card">
                <div className="phone-file-art"><Icon name="folder" /></div>
                <small>INCOMING FROM ALEX</small>
                <strong>summer-photos</strong>
                <span>42 files · 486 MB</span>
              </div>
              <div className="phone-state-area">
                <motion.div
                  className="phone-state is-waiting"
                  animate={
                    reduceMotion
                      ? { opacity: 0 }
                      : { opacity: [1, 1, 0, 0] }
                  }
                  transition={{ ...repeating, times: [0, 0.43, 0.5, 1] }}
                >
                  <span className="state-symbol"><Icon name="lock" /></span>
                  <strong>Waiting for approval</strong>
                  <small>The sender stays in control.</small>
                </motion.div>
                <motion.div
                  className="phone-state is-transferring"
                  animate={
                    reduceMotion
                      ? { opacity: 0 }
                      : { opacity: [0, 0, 1, 1, 0, 0] }
                  }
                  transition={{ ...repeating, times: [0, 0.45, 0.51, 0.71, 0.78, 1] }}
                >
                  <span className="transfer-percentage">68<span>%</span></span>
                  <strong>Receiving securely</strong>
                  <small>Direct path · 38 MB/s</small>
                </motion.div>
                <motion.div
                  className="phone-state is-verified"
                  animate={
                    reduceMotion
                      ? { opacity: 1, scale: 1 }
                      : { opacity: [0, 0, 1, 1], scale: [0.92, 0.92, 1, 1] }
                  }
                  transition={{ ...repeating, times: [0, 0.72, 0.79, 1], ease: "easeOut" }}
                >
                  <span className="state-symbol is-success"><Icon name="verified" /></span>
                  <strong>Received &amp; verified</strong>
                  <small>Saved to Downloads</small>
                </motion.div>
              </div>
              <div className="phone-home-indicator" />
            </div>
          </div>
        </div>

        <motion.div
          className="hero-route-card"
          animate={
            reduceMotion
              ? { opacity: 1, y: 0 }
              : { opacity: [0, 1, 1, 1], y: [-8, 0, 0, 0] }
          }
          transition={{ ...repeating, times: [0, 0.08, 0.92, 1], ease: "easeOut" }}
        >
          <span className="route-pulse" />
          <span>
            <small>ROUTE</small>
            <strong>Direct path established</strong>
          </span>
          <span className="route-latency">18 ms</span>
        </motion.div>

        <motion.div
          className="hero-verified-card"
          animate={
            reduceMotion
              ? { opacity: 1, y: 0, scale: 1 }
              : {
                  opacity: [0, 0, 1, 1],
                  y: [8, 8, 0, 0],
                  scale: [0.96, 0.96, 1, 1],
                }
          }
          transition={{ ...repeating, times: [0, 0.74, 0.81, 1], ease: "easeOut" }}
        >
          <span><Icon name="verified" /></span>
          <span>
            <strong>Content verified</strong>
            <small>42 / 42 files match</small>
          </span>
        </motion.div>

        <div className="hero-privacy-rail">
          <span><Icon name="lock" /> E2E encrypted</span>
          <i />
          <span>No hosted copy</span>
          <i />
          <span>Approval required</span>
        </div>
      </div>
      <figcaption className="sr-only">
        A VniDrop transfer moves from a desktop sender to a phone after receiver approval, then is
        verified on arrival.
      </figcaption>
    </figure>
  );
}
