"use client";

import { useState } from "react";
import { Icon } from "@/components/icons";

type ApprovalState = "pending" | "approved" | "refused";

export function ApprovalDemo() {
  const [state, setState] = useState<ApprovalState>("pending");

  return (
    <div className={`approval-demo state-${state}`}>
      <div className="approval-window-bar">
        <span />
        <span />
        <span />
        <small>Receiver requests</small>
      </div>
      <div className="approval-body" aria-live="polite">
        <div className="approval-heading">
          <div className="device-avatar">
            <span className="device-avatar-screen" />
            <span className="device-avatar-glow" />
          </div>
          <div>
            <span className="mono-label">
              {state === "pending" ? "NEW REQUEST" : state === "approved" ? "IN PROGRESS" : "REQUEST CLOSED"}
            </span>
            <h3>
              {state === "pending" && "Maya’s phone wants to receive"}
              {state === "approved" && "Sending securely to Maya"}
              {state === "refused" && "Request refused"}
            </h3>
          </div>
        </div>

        <div className="approval-file">
          <span className="approval-file-icon">
            <Icon name="folder" />
          </span>
          <span>
            <strong>Summer photos</strong>
            <small>42 files · 1.8 GB</small>
          </span>
          {state === "approved" && <span className="approval-percent">68%</span>}
        </div>

        {state === "pending" && (
          <div className="approval-actions">
            <button className="approval-refuse" type="button" onClick={() => setState("refused")}>
              Refuse
            </button>
            <button className="approval-accept" type="button" onClick={() => setState("approved")}>
              <Icon name="check" />
              Approve
            </button>
          </div>
        )}

        {state === "approved" && (
          <div className="approval-progress">
            <div className="approval-progress-track">
              <span />
            </div>
            <div className="approval-progress-meta">
              <span><i className="live-dot" /> Direct connection</span>
              <button type="button" onClick={() => setState("pending")}>Reset demo</button>
            </div>
          </div>
        )}

        {state === "refused" && (
          <div className="approval-refused">
            <span><Icon name="x" /></span>
            <p>This device was not given access.</p>
            <button type="button" onClick={() => setState("pending")}>Try again</button>
          </div>
        )}
      </div>
      <div className="approval-footer">
        <Icon name="shield" />
        Approval is required by default
      </div>
    </div>
  );
}
