import { ImageResponse } from "next/og";

export const dynamic = "force-static";

const imageSize = {
  width: 1200,
  height: 630,
};

function BrandMark() {
  return (
    <svg width="54" height="54" viewBox="0 0 1024 1024" fill="none">
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M236.68 148H338.36C366.24 148 387.56 170.96 387.56 198.84V564.56C387.56 597.36 372.8 620.32 372.8 646.56C372.8 725.28 436.76 787.6 522.04 787.6C607.32 787.6 668 725.28 668 646.56C668 620.32 656.52 597.36 656.52 564.56V198.84C656.52 170.96 677.84 148 705.72 148H781.16C817.24 148 846.76 177.52 846.76 213.6V564.56C846.76 738.4 704.08 879.44 522.04 879.44C340 879.44 194.04 738.4 194.04 564.56V374.32H220.28V305.44C195.68 305.44 176 297.24 176 280.84V246.4C176 231.64 187.48 220.16 202.24 220.16H236.68V148ZM256.36 239.84C251.44 239.84 248.16 244.76 248.16 249.68V275.92C248.16 282.48 253.08 285.76 259.64 285.76H282.6C289.16 285.76 292.44 280.84 292.44 274.28V251.32C292.44 244.76 287.52 239.84 280.96 239.84H256.36Z"
        fill="url(#brand-gradient)"
      />
      <path
        d="M520.4 431.72C495.8 464.52 443.32 530.12 420.36 577.68C390.84 636.72 403.96 699.04 446.6 731.84C487.6 758.08 549.92 758.08 592.56 730.2C633.56 700.68 646.68 636.72 620.44 577.68C597.48 530.12 546.64 464.52 520.4 431.72Z"
        fill="url(#drop-gradient)"
      />
      <defs>
        <linearGradient id="brand-gradient" x1="176" y1="148" x2="905" y2="816">
          <stop stopColor="#C084FC" />
          <stop offset="0.52" stopColor="#A855F7" />
          <stop offset="1" stopColor="#7C2AEF" />
        </linearGradient>
        <linearGradient id="drop-gradient" x1="404" y1="432" x2="707" y2="649">
          <stop stopColor="#E9D5FF" />
          <stop offset="1" stopColor="#A855F7" />
        </linearGradient>
      </defs>
    </svg>
  );
}

function FileGlyph() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <path
        d="M6 3h8l4 4v14H6z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
      <path d="M14 3v5h4M9 13h6M9 17h4" stroke="currentColor" strokeWidth="1.7" />
    </svg>
  );
}

function CheckGlyph() {
  return (
    <svg width="27" height="27" viewBox="0 0 24 24" fill="none">
      <path
        d="m6.5 12.5 3.4 3.4 7.8-8"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function LockGlyph() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
      <rect x="5" y="10" width="14" height="11" rx="3" stroke="currentColor" strokeWidth="1.8" />
      <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}

function TransferIllustration() {
  return (
    <div
      style={{
        position: "absolute",
        top: 86,
        right: 72,
        width: 470,
        height: 474,
        display: "flex",
      }}
    >
      <div
        style={{
          position: "absolute",
          top: 24,
          left: 48,
          width: 400,
          height: 400,
          borderRadius: 999,
          display: "flex",
          background:
            "radial-gradient(circle, rgba(168, 85, 247, 0.24) 0%, rgba(124, 42, 239, 0.09) 44%, rgba(13, 10, 16, 0) 72%)",
        }}
      />

      <div
        style={{
          position: "absolute",
          top: 0,
          right: 2,
          display: "flex",
          alignItems: "center",
          color: "#A99EAD",
          fontSize: 12,
          fontWeight: 700,
          letterSpacing: 2.1,
        }}
      >
        DIRECT · ENCRYPTED
      </div>

      <svg
        width="470"
        height="474"
        viewBox="0 0 470 474"
        fill="none"
        style={{ position: "absolute", inset: 0 }}
      >
        <path
          d="M264 229C305 233 310 300 353 313"
          stroke="url(#route-gradient)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeDasharray="8 9"
        />
        <circle cx="264" cy="229" r="6" fill="#D8B4FE" />
        <circle cx="353" cy="313" r="6" fill="#A855F7" />
        <defs>
          <linearGradient id="route-gradient" x1="264" y1="229" x2="353" y2="313">
            <stop stopColor="#D8B4FE" />
            <stop offset="1" stopColor="#7C2AEF" />
          </linearGradient>
        </defs>
      </svg>

      <div
        style={{
          position: "absolute",
          top: 79,
          left: 1,
          width: 286,
          height: 184,
          display: "flex",
          flexDirection: "column",
          border: "2px solid #44384A",
          borderRadius: 18,
          background: "#18131D",
          boxShadow: "0 24px 70px rgba(0, 0, 0, 0.38)",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            height: 34,
            display: "flex",
            alignItems: "center",
            padding: "0 14px",
            borderBottom: "1px solid #382E3D",
          }}
        >
          <div style={{ width: 7, height: 7, borderRadius: 99, background: "#685B6E", display: "flex" }} />
          <div style={{ width: 7, height: 7, borderRadius: 99, background: "#685B6E", display: "flex", marginLeft: 7 }} />
          <div style={{ width: 7, height: 7, borderRadius: 99, background: "#A855F7", display: "flex", marginLeft: 7 }} />
        </div>
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            padding: "20px 21px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center" }}>
            <div
              style={{
                width: 42,
                height: 42,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                border: "1px solid #4C3A57",
                borderRadius: 11,
                background: "#241A2B",
                color: "#C084FC",
              }}
            >
              <FileGlyph />
            </div>
            <div style={{ display: "flex", flexDirection: "column", marginLeft: 13 }}>
              <div style={{ color: "#F8F4FA", fontSize: 16, fontWeight: 650 }}>Project files</div>
              <div style={{ color: "#8E8292", fontSize: 12, marginTop: 3 }}>12 items · 284 MB</div>
            </div>
          </div>
          <div
            style={{
              width: "100%",
              height: 5,
              display: "flex",
              marginTop: 21,
              borderRadius: 99,
              background: "#342A39",
              overflow: "hidden",
            }}
          >
            <div
              style={{
                width: "72%",
                height: "100%",
                display: "flex",
                borderRadius: 99,
                background: "linear-gradient(90deg, #C084FC, #7C2AEF)",
              }}
            />
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", marginTop: 10 }}>
            <div style={{ color: "#A99EAD", fontSize: 11 }}>Sending directly</div>
            <div style={{ color: "#D8B4FE", fontSize: 11, fontWeight: 700 }}>72%</div>
          </div>
        </div>
      </div>

      <div
        style={{
          position: "absolute",
          top: 263,
          left: 112,
          width: 62,
          height: 8,
          display: "flex",
          borderRadius: 99,
          background: "#44384A",
        }}
      />
      <div
        style={{
          position: "absolute",
          top: 270,
          left: 87,
          width: 112,
          height: 8,
          display: "flex",
          borderRadius: 99,
          background: "#29212E",
        }}
      />

      <div
        style={{
          position: "absolute",
          top: 226,
          left: 286,
          width: 50,
          height: 50,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          border: "1px solid #6A477B",
          borderRadius: 15,
          background: "#211627",
          color: "#D8B4FE",
          boxShadow: "0 12px 32px rgba(124, 42, 239, 0.3)",
        }}
      >
        <LockGlyph />
      </div>

      <div
        style={{
          position: "absolute",
          top: 238,
          right: 6,
          width: 127,
          height: 230,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          border: "2px solid #51415A",
          borderRadius: 30,
          background: "#18131D",
          boxShadow: "0 24px 70px rgba(0, 0, 0, 0.42)",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            width: 48,
            height: 5,
            display: "flex",
            marginTop: 10,
            borderRadius: 99,
            background: "#45374C",
          }}
        />
        <div
          style={{
            width: 58,
            height: 58,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            marginTop: 38,
            border: "1px solid #6F4A82",
            borderRadius: 99,
            background: "#27182E",
            color: "#D8B4FE",
          }}
        >
          <CheckGlyph />
        </div>
        <div style={{ color: "#F8F4FA", fontSize: 15, fontWeight: 700, marginTop: 15 }}>Received</div>
        <div style={{ color: "#8E8292", fontSize: 10, marginTop: 5 }}>Verified on arrival</div>
        <div
          style={{
            width: 46,
            height: 4,
            display: "flex",
            marginTop: 32,
            borderRadius: 99,
            background: "#45374C",
          }}
        />
      </div>
    </div>
  );
}

export function GET() {
  return new ImageResponse(
    <div
      style={{
        position: "relative",
        width: "100%",
        height: "100%",
        display: "flex",
        overflow: "hidden",
        background: "#0D0A10",
        color: "#FAF7FC",
        fontFamily: "sans-serif",
      }}
    >
      <div
        style={{
          position: "absolute",
          inset: 0,
          display: "flex",
          background:
            "radial-gradient(circle at 84% 48%, rgba(168, 85, 247, 0.14), transparent 34%), radial-gradient(circle at 16% 100%, rgba(124, 42, 239, 0.09), transparent 30%)",
        }}
      />

      <div style={{ position: "absolute", top: 64, bottom: 64, left: 64, display: "flex", borderLeft: "1px dashed #3A3040" }} />
      <div style={{ position: "absolute", top: 64, bottom: 64, right: 64, display: "flex", borderRight: "1px dashed #3A3040" }} />
      <div style={{ position: "absolute", top: 64, right: 0, left: 0, display: "flex", borderTop: "1px solid #3A3040" }} />
      <div style={{ position: "absolute", right: 0, bottom: 64, left: 0, display: "flex", borderBottom: "1px solid #3A3040" }} />

      <div
        style={{
          position: "absolute",
          top: 89,
          left: 96,
          display: "flex",
          alignItems: "center",
        }}
      >
        <BrandMark />
        <div style={{ display: "flex", alignItems: "baseline", marginLeft: 12 }}>
          <div style={{ fontSize: 28, fontWeight: 750, letterSpacing: -1.1 }}>VniDrop</div>
          <div
            style={{
              marginLeft: 18,
              color: "#8E8292",
              fontSize: 11,
              fontWeight: 700,
              letterSpacing: 1.8,
            }}
          >
            OPEN SOURCE · LOCAL P2P
          </div>
        </div>
      </div>

      <div
        style={{
          position: "absolute",
          top: 185,
          left: 96,
          width: 625,
          display: "flex",
          flexDirection: "column",
          fontSize: 66,
          fontWeight: 750,
          lineHeight: 0.99,
          letterSpacing: -3.4,
        }}
      >
        <div style={{ display: "flex" }}>Your files.</div>
        <div style={{ display: "flex", color: "#C084FC", marginTop: 3 }}>A straight line</div>
        <div style={{ display: "flex", marginTop: 3 }}>between devices.</div>
      </div>

      <div
        style={{
          position: "absolute",
          bottom: 101,
          left: 96,
          display: "flex",
          color: "#AAA0AE",
          fontSize: 25,
          fontWeight: 450,
          letterSpacing: -0.5,
        }}
      >
        Send files directly. Stay in control.
      </div>

      <TransferIllustration />
    </div>,
    imageSize,
  );
}
