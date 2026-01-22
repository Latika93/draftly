import { useState, useEffect } from "react";
import api from "../api/axios";
import Button from "./common/Button";
import { useNavigate } from "react-router-dom";
import ComposeEmailBox from "./ComposeEmail";

interface Email {
  messageId: string;
  threadId: string;
  from: string;
  subject: string;
  body: string;
}

const IncomingEmails = () => {
  const navigate = useNavigate();
  const [emails, setEmails] = useState<Email[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedEmail, setSelectedEmail] = useState<Email | null>(null);
  const [existingBody, setExistingBody] = useState<string | null>(null);

  useEffect(() => {
    const fetchEmails = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await api.get("/emails/inbox");
        setEmails(response.data || []);
      } catch (err) {
        const errorMessage =
          err && typeof err === "object" && "response" in err
            ? (err as { response?: { data?: { message?: string } } }).response
              ?.data?.message
            : undefined;
        setError(errorMessage || "Failed to fetch incoming emails");
        navigate("/login", { replace: true });
      } finally {
        setLoading(false);
      }
    };

    fetchEmails();
  }, [navigate]);

  const handleGenerateDraft = async (email: Email) => {
    try {
      const response = await api.get(
        `/emails/thread/body?threadId=${email.threadId}`
      );
      const data = response.data;

      if (data && data.body) {
        setExistingBody(data.body);
      } else {
        setExistingBody(null);
      }

      setSelectedEmail(email);
    } catch {
      setExistingBody(null);
      setSelectedEmail(email);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: "20px", color: "#888" }}>
        Loading incoming emails...
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: "20px", color: "#ff6666" }}>
        {error}
      </div>
    );
  }

  return (
    <>
      <div style={{ padding: "20px", width: "100%", boxSizing: "border-box" }}>
        <h2
          style={{
            fontSize: "1.2rem",
            fontWeight: "400",
            color: "#888",
            marginBottom: "20px",
          }}
        >
          Incoming emails
        </h2>

        {emails.length === 0 ? (
          <div style={{ color: "#888", padding: "20px", textAlign: "center" }}>
            No incoming emails
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            {emails.map((email) => (
              <div
                key={email.messageId}
                style={{
                  backgroundColor: "#2a2a2a",
                  border: "1px solid #3a3a3a",
                  borderRadius: "12px",
                  padding: "12px 16px",
                  display: "flex",
                  alignItems: "center",
                  gap: "12px",
                  height: "80px",
                  width: "100%",
                  boxSizing: "border-box",
                  transition: "border-color 0.2s ease",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = "#667eea";
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = "#3a3a3a";
                }}
              >
                <div
                  style={{
                    flex: 1,
                    minWidth: 0,
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: "center",
                    gap: "6px",
                    overflow: "hidden",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "8px",
                      overflow: "hidden",
                    }}
                  >
                    <h3
                      style={{
                        fontSize: "0.95rem",
                        fontWeight: "600",
                        color: "#ffffff",
                        margin: 0,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                        flex: "0 1 auto",
                      }}
                    >
                      {email.subject}
                    </h3>
                    <span
                      style={{
                        color: "#888",
                        fontSize: "0.85rem",
                        flexShrink: 0,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                        maxWidth: "200px",
                      }}
                    >
                      {email.from}
                    </span>
                  </div>
                  <p
                    style={{
                      color: "#aaa",
                      fontSize: "0.85rem",
                      margin: 0,
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                      lineHeight: "1.3",
                    }}
                  >
                    {email.body}
                  </p>
                </div>
                <div style={{ flexShrink: 0 }}>
                  <Button
                    onClick={() => handleGenerateDraft(email)}
                    style={{
                      padding: "8px 16px",
                      fontSize: "0.85rem",
                      whiteSpace: "nowrap",
                    }}
                  >
                    Generate Draft
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {selectedEmail && (
        <ComposeEmailBox
          email={selectedEmail}
          onClose={() => {
            setSelectedEmail(null);
            setExistingBody(null);
          }}
          existingBody={existingBody}
        />
      )}
    </>
  );
};

export default IncomingEmails;
