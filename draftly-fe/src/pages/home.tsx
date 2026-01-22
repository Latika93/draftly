import { useEffect, useState } from "react";
import Button from "../components/common/Button";
import api from "../api/axios";
import IncomingEmails from "../components/IncomingEmails";
import { useNavigate } from "react-router-dom";

export default function Home() {
  const navigate = useNavigate();
  const [recipient, setRecipient] = useState("");
  const [context, setContext] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      navigate("/login", { replace: true });
    }
  }, [navigate]);

  const handleCreateDraft = async () => {
    if (!recipient.trim() || !context.trim()) {
      setError("Please fill in both recipient email and context");
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);

    try {
      await api.post("/emails/draft", {
        recipient: recipient.trim(),
        context: context.trim(),
      });
      setSuccess("Draft created successfully!");
      setRecipient("");
      setContext("");
    } catch (err) {
      const errorMessage =
        err && typeof err === "object" && "response" in err
          ? (err as { response?: { data?: { message?: string } } }).response
            ?.data?.message
          : undefined;
      setError(errorMessage || "Failed to create draft. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        backgroundColor: "#1a1a1a",
        color: "#ffffff",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        padding: "40px 20px",
      }}
    >

      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          width: "100%",
          maxWidth: "1200px",
          marginBottom: "40px",
        }}
      >
        <h1
          style={{
            fontSize: "1.5rem",
            fontWeight: "600",
            color: "#ffffff",
            margin: 0,
          }}
        >
          Draftly
        </h1>
        <Button
          onClick={() => {
            localStorage.removeItem("accessToken");
            navigate("/login", { replace: true });
          }}
          style={{
            padding: "10px 20px",
            fontSize: "0.9rem",
          }}
        >
          Logout
        </Button>
      </div>


      <h2
        style={{
          fontSize: "1.5rem",
          fontWeight: "400",
          color: "#ffffff",
          marginBottom: "3rem",
          textAlign: "center",
        }}
      >
        Create drafts for your emails
      </h2>

      <div
        style={{
          width: "100%",
          maxWidth: "800px",
          display: "flex",
          flexDirection: "column",
          gap: "20px",
        }}
      >
        <input
          type="email"
          placeholder="Recipient email"
          value={recipient}
          onChange={(e) => setRecipient(e.target.value)}
          style={{
            padding: "16px 20px",
            fontSize: "1rem",
            color: "#ffffff",
            backgroundColor: "#2a2a2a",
            border: "1px solid #3a3a3a",
            borderRadius: "12px",
            outline: "none",
            transition: "border-color 0.2s ease",
          }}
          onFocus={(e) => {
            e.currentTarget.style.borderColor = "#667eea";
          }}
          onBlur={(e) => {
            e.currentTarget.style.borderColor = "#3a3a3a";
          }}
        />

        <div
          style={{
            position: "relative",
            display: "flex",
            alignItems: "flex-end",
            gap: "12px",
          }}
        >
          <textarea
            placeholder="Write context for you draft (e.g., Follow up on the last job application for spring developer and ask for feedback)"
            value={context}
            onChange={(e) => setContext(e.target.value)}
            rows={2}
            style={{
              flex: 1,
              padding: "16px 20px",
              fontSize: "1rem",
              color: "#ffffff",
              backgroundColor: "#2a2a2a",
              border: "1px solid #3a3a3a",
              borderRadius: "12px",
              outline: "none",
              resize: "vertical",
              minHeight: "80px",
              fontFamily: "inherit",
              transition: "border-color 0.2s ease",
            }}
            onFocus={(e) => {
              e.currentTarget.style.borderColor = "#667eea";
            }}
            onBlur={(e) => {
              e.currentTarget.style.borderColor = "#3a3a3a";
            }}
          />
          <Button
            onClick={handleCreateDraft}
            disabled={loading || !recipient.trim() || !context.trim()}
          >
            {loading ? "Creating..." : "Create Draft"}
          </Button>
        </div>

        {error && (
          <div
            style={{
              padding: "12px 16px",
              backgroundColor: "#3a1a1a",
              border: "1px solid #ff4444",
              borderRadius: "8px",
              color: "#ff6666",
              fontSize: "0.9rem",
            }}
          >
            {error}
          </div>
        )}

        {success && (
          <div
            style={{
              padding: "12px 16px",
              backgroundColor: "#1a3a1a",
              border: "1px solid #44ff44",
              borderRadius: "8px",
              color: "#66ff66",
              fontSize: "0.9rem",
            }}
          >
            {success}
          </div>
        )}
      </div>

      <IncomingEmails />
    </div>
  );
}
