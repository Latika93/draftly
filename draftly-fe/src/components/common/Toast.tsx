import { useEffect } from "react";

interface ToastProps {
  message: string;
  type: "success" | "error";
  onClose: () => void;
  duration?: number;
}

export default function Toast({
  message,
  type,
  onClose,
  duration = 3000,
}: ToastProps) {
  useEffect(() => {
    const timer = setTimeout(() => {
      onClose();
    }, duration);

    return () => clearTimeout(timer);
  }, [duration, onClose]);

  return (
    <div
      style={{
        position: "fixed",
        top: "20px",
        right: "20px",
        backgroundColor: type === "success" ? "#1a3a1a" : "#3a1a1a",
        border: `1px solid ${type === "success" ? "#44ff44" : "#ff4444"}`,
        borderRadius: "8px",
        padding: "16px 20px",
        color: type === "success" ? "#66ff66" : "#ff6666",
        fontSize: "0.95rem",
        zIndex: 10000,
        boxShadow: "0 4px 12px rgba(0,0,0,0.3)",
        minWidth: "250px",
        maxWidth: "400px",
      }}
    >
      {message}
    </div>
  );
}

