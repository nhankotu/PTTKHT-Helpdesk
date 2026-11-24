import { Link } from "react-router-dom";

export function Brand({ light = false }: { light?: boolean }) {
  return (
    <Link
      to="/"
      className={`brand ${light ? "brand-light" : ""}`}
      aria-label="Helpdesk — Trang chủ"
    >
      <span className="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 32 32" fill="none">
          <path
            d="M8 17v-3a8 8 0 0 1 16 0v3M8 14H6v9h5v-9H8Zm16 0h2v9h-5v-9h3ZM24 23c0 3-3 4-7 4"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M15 27h3"
            stroke="currentColor"
            strokeWidth="3"
            strokeLinecap="round"
          />
        </svg>
      </span>
      <span>
        helpdesk<span className="brand-dot">.</span>
      </span>
    </Link>
  );
}
