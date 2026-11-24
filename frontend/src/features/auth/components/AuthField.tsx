import type { ReactNode } from "react";

export function AuthField({
  id,
  label,
  error,
  hint,
  children,
  optional = false,
}: {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
  optional?: boolean;
}) {
  return (
    <div className={`auth-field ${error ? "has-error" : ""}`}>
      <label htmlFor={id}>
        {label}
        {optional && <span className="field-optional">Không bắt buộc</span>}
      </label>
      {children}
      {error ? (
        <p className="field-error" id={`${id}-help`} role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="field-hint" id={`${id}-help`}>
          {hint}
        </p>
      ) : null}
    </div>
  );
}
