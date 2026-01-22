import { useState, useEffect } from "react";
import api from "../api/axios";
import Button from "./common/Button";
import Toast from "./common/Toast";

interface Email {
    messageId: string;
    threadId: string;
    from: string;
    subject: string;
    body: string;
}

interface DraftResponse {
    status: string;
    message: string;
    draftId?: string;
    threadId?: string;
    replyMessage?: string;
}

interface ComposeEmailBoxProps {
    email: Email;
    onClose: () => void;
    existingBody?: string | null;
}

const ComposeEmailBox = ({
    email,
    onClose,
    existingBody,
}: ComposeEmailBoxProps) => {
    const [loading, setLoading] = useState(false);
    const [approving, setApproving] = useState(false);
    const [rejecting, setRejecting] = useState(false);
    const [regenerating, setRegenerating] = useState(false);
    const [showConfirm, setShowConfirm] = useState(false);
    const [draftResponse, setDraftResponse] = useState<DraftResponse | null>(
        null
    );
    const [replyText, setReplyText] = useState(existingBody || "");
    const [selectedTone, setSelectedTone] = useState<"FORMAL" | "CONCISE" | "FRIENDLY">("FORMAL");
    const [toast, setToast] = useState<{
        message: string;
        type: "success" | "error";
    } | null>(null);

    useEffect(() => {
        if (existingBody) {
            setReplyText(existingBody);
        }
    }, [existingBody]);

    const extractEmailAddress = (emailString: string): string => {
        const match = emailString.match(/<(.+)>/);
        return match ? match[1] : emailString;
    };

    const handleStartGeneration = async () => {
        setLoading(true);
        setDraftResponse(null);

        try {
            const response = await api.post("/emails/draft/reply", {
                body: email.body,
                from: email.from,
                messageId: email.messageId,
                subject: email.subject,
                threadId: email.threadId,
                tone: selectedTone,
            });

            const data: DraftResponse = response.data;
            setDraftResponse(data);

            if (data.status === "DRAFT_CREATED" && data.replyMessage) {
                setReplyText(data.replyMessage);
            }
        } catch {
            setDraftResponse({
                status: "FAILED",
                message: "Failed to generate draft. Please try again.",
            });
        } finally {
            setLoading(false);
        }
    };

    const handleApprove = () => {
        setShowConfirm(true);
    };

    const handleConfirmApprove = async () => {
        if (!replyText.trim()) {
            return;
        }

        setApproving(true);
        setShowConfirm(false);

        try {
            await api.post(
                `/emails/draft/reply/approve?threadId=${email.threadId}`,
                {
                    replyMessage: replyText,
                }
            );
            setToast({
                message: "Draft approved and sent successfully!",
                type: "success",
            });
            setTimeout(() => {
                onClose();
            }, 1500);
        } catch {
            setToast({
                message: "Failed to approve draft. Please try again.",
                type: "error",
            });
        } finally {
            setApproving(false);
        }
    };

    const handleRegenerate = async () => {
        setRegenerating(true);

        try {
            const response = await api.post(
                `/emails/draft/reply/regenerate?threadId=${email.threadId}`,
                {
                    body: email.body,
                    from: email.from,
                    messageId: email.messageId,
                    subject: email.subject,
                    threadId: email.threadId,
                    tone: selectedTone,
                }
            );

            const data: DraftResponse = response.data;

            if (data.status === "DRAFT_CREATED" && data.replyMessage) {
                setReplyText(data.replyMessage);
                setDraftResponse(data);
                setToast({
                    message: "Draft regenerated successfully!",
                    type: "success",
                });
            } else {
                setToast({
                    message: "Failed to regenerate draft. Please try again.",
                    type: "error",
                });
            }
        } catch {
            setToast({
                message: "Failed to regenerate draft. Please try again.",
                type: "error",
            });
        } finally {
            setRegenerating(false);
        }
    };

    const handleReject = async () => {
        setRejecting(true);

        try {
            await api.post(`/emails/thread/reject?threadId=${email.threadId}`);
            setToast({
                message: "Thread rejected successfully",
                type: "success",
            });
            setTimeout(() => {
                onClose();
            }, 1500);
        } catch {
            setToast({
                message: "Failed to reject thread. Please try again.",
                type: "error",
            });
        } finally {
            setRejecting(false);
        }
    };

    return (
        <div
            style={{
                position: "fixed",
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                backgroundColor: "rgba(0, 0, 0, 0.7)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                zIndex: 1000,
                padding: "20px",
                boxSizing: "border-box",
            }}
            onClick={onClose}
        >
            <div
                style={{
                    backgroundColor: "#1a1a1a",
                    borderRadius: "16px",
                    padding: "32px",
                    maxWidth: "800px",
                    width: "100%",
                    maxHeight: "90vh",
                    overflowY: "auto",
                    boxSizing: "border-box",
                    border: "1px solid #3a3a3a",
                }}
                onClick={(e) => e.stopPropagation()}
            >
                <div
                    style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "flex-start",
                        marginBottom: "24px",
                    }}
                >
                    <h2
                        style={{
                            fontSize: "1.5rem",
                            fontWeight: "600",
                            color: "#ffffff",
                            margin: 0,
                        }}
                    >
                        Generate Draft Reply
                    </h2>
                    <div
                        style={{
                            display: "flex",
                            alignItems: "center",
                            gap: "12px",
                        }}
                    >
                        <select
                            value={selectedTone}
                            onChange={(e) =>
                                setSelectedTone(
                                    e.target.value as "FORMAL" | "CONCISE" | "FRIENDLY"
                                )
                            }
                            style={{
                                padding: "8px 12px",
                                fontSize: "0.9rem",
                                color: "#ffffff",
                                backgroundColor: "#2a2a2a",
                                border: "1px solid #3a3a3a",
                                borderRadius: "8px",
                                outline: "none",
                                cursor: "pointer",
                            }}
                            onFocus={(e) => {
                                e.currentTarget.style.borderColor = "#667eea";
                            }}
                            onBlur={(e) => {
                                e.currentTarget.style.borderColor = "#3a3a3a";
                            }}
                        >
                            <option value="FORMAL">Formal</option>
                            <option value="CONCISE">Concise</option>
                            <option value="FRIENDLY">Friendly</option>
                        </select>
                        <button
                            onClick={onClose}
                            style={{
                                background: "none",
                                border: "none",
                                color: "#888",
                                fontSize: "1.5rem",
                                cursor: "pointer",
                                padding: "0",
                                width: "32px",
                                height: "32px",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                            }}
                        >
                            ×
                        </button>
                    </div>
                </div>

                <div style={{ marginBottom: "24px" }}>
                    <div style={{ marginBottom: "16px" }}>
                        <label
                            style={{
                                fontSize: "0.9rem",
                                color: "#888",
                                display: "block",
                                marginBottom: "6px",
                            }}
                        >
                            From
                        </label>
                        <div
                            style={{
                                color: "#ffffff",
                                fontSize: "1rem",
                                padding: "8px 12px",
                                backgroundColor: "#2a2a2a",
                                borderRadius: "8px",
                                border: "1px solid #3a3a3a",
                            }}
                        >
                            {email.from}
                        </div>
                    </div>

                    <div style={{ marginBottom: "16px" }}>
                        <label
                            style={{
                                fontSize: "0.9rem",
                                color: "#888",
                                display: "block",
                                marginBottom: "6px",
                            }}
                        >
                            Subject
                        </label>
                        <div
                            style={{
                                color: "#ffffff",
                                fontSize: "1rem",
                                padding: "8px 12px",
                                backgroundColor: "#2a2a2a",
                                borderRadius: "8px",
                                border: "1px solid #3a3a3a",
                            }}
                        >
                            {email.subject}
                        </div>
                    </div>

                    <div style={{ marginBottom: "24px" }}>
                        <label
                            style={{
                                fontSize: "0.9rem",
                                color: "#888",
                                display: "block",
                                marginBottom: "6px",
                            }}
                        >
                            Body
                        </label>
                        <div
                            style={{
                                color: "#aaa",
                                fontSize: "0.95rem",
                                padding: "12px",
                                backgroundColor: "#2a2a2a",
                                borderRadius: "8px",
                                border: "1px solid #3a3a3a",
                                maxHeight: "150px",
                                overflowY: "auto",
                                whiteSpace: "pre-wrap",
                                lineHeight: "1.5",
                            }}
                        >
                            {email.body}
                        </div>
                    </div>
                </div>

                {!draftResponse && !existingBody && (
                    <div
                        style={{
                            border: "2px dashed #3a3a3a",
                            borderRadius: "12px",
                            padding: "80px 40px",
                            textAlign: "center",
                            backgroundColor: "#1a1a1a",
                            marginBottom: "24px",
                            minHeight: "300px",
                            display: "flex",
                            flexDirection: "column",
                            alignItems: "center",
                            justifyContent: "center",
                        }}
                    >
                        <Button
                            onClick={handleStartGeneration}
                            disabled={loading}
                            style={{
                                padding: "14px 32px",
                                fontSize: "1.1rem",
                            }}
                        >
                            {loading ? "Generating..." : "Start Generation"}
                        </Button>
                    </div>
                )}

                {(draftResponse?.status === "DRAFT_CREATED" || existingBody) && (
                    <>
                        <div style={{ marginBottom: "24px" }}>
                            <label
                                style={{
                                    fontSize: "0.9rem",
                                    color: "#888",
                                    display: "block",
                                    marginBottom: "8px",
                                }}
                            >
                                Draft Reply
                            </label>
                            <textarea
                                value={replyText}
                                onChange={(e) => setReplyText(e.target.value)}
                                style={{
                                    width: "100%",
                                    minHeight: "300px",
                                    padding: "16px",
                                    fontSize: "1rem",
                                    color: "#ffffff",
                                    backgroundColor: "#2a2a2a",
                                    border: "1px solid #3a3a3a",
                                    borderRadius: "8px",
                                    outline: "none",
                                    resize: "vertical",
                                    fontFamily: "inherit",
                                    lineHeight: "1.5",
                                    boxSizing: "border-box",
                                }}
                                onFocus={(e) => {
                                    e.currentTarget.style.borderColor = "#667eea";
                                }}
                                onBlur={(e) => {
                                    e.currentTarget.style.borderColor = "#3a3a3a";
                                }}
                            />
                        </div>
                        <div
                            style={{
                                display: "flex",
                                gap: "12px",
                                justifyContent: "flex-end",
                            }}
                        >
                            <Button
                                onClick={handleRegenerate}
                                disabled={regenerating}
                                style={{
                                    backgroundColor: "#444",
                                    padding: "12px 24px",
                                }}
                            >
                                {regenerating ? "Regenerating..." : "Regenerate"}
                            </Button>
                            <Button
                                onClick={handleReject}
                                disabled={rejecting}
                                style={{
                                    backgroundColor: "#555",
                                    padding: "12px 24px",
                                }}
                            >
                                {rejecting ? "Rejecting..." : "Reject"}
                            </Button>
                            <Button
                                onClick={handleApprove}
                                disabled={approving}
                                style={{
                                    padding: "12px 24px",
                                }}
                            >
                                {approving ? "Approving..." : "Approve"}
                            </Button>
                        </div>
                    </>
                )}

                {draftResponse && draftResponse.status === "FAILED" && (
                    <div
                        style={{
                            border: "1px solid #ff4444",
                            backgroundColor: "#3a1a1a",
                            borderRadius: "8px",
                            padding: "20px",
                            textAlign: "center",
                            color: "#ff6666",
                            marginBottom: "24px",
                        }}
                    >
                        <p style={{ margin: 0, fontSize: "1rem" }}>
                            Sorry, we're facing an error here. Please try again.
                        </p>
                        <div style={{ marginTop: "16px" }}>
                            <Button
                                onClick={() => {
                                    setDraftResponse(null);
                                    setReplyText("");
                                }}
                                style={{
                                    padding: "10px 20px",
                                }}
                            >
                                Try Again
                            </Button>
                        </div>
                    </div>
                )}

                {draftResponse && draftResponse.status === "NO_REPLY_REQUIRED" && (
                    <div
                        style={{
                            border: "1px solid #888",
                            backgroundColor: "#2a2a2a",
                            borderRadius: "8px",
                            padding: "40px 20px",
                            textAlign: "center",
                            color: "#888",
                            marginBottom: "24px",
                        }}
                    >
                        <p style={{ margin: 0, fontSize: "1rem" }}>
                            No reply is required, this is not a no-reply email.
                        </p>
                        <div style={{ marginTop: "20px" }}>
                            <Button onClick={onClose} style={{ padding: "10px 20px" }}>
                                Close
                            </Button>
                        </div>
                    </div>
                )}

                {showConfirm && (
                    <div
                        style={{
                            position: "fixed",
                            top: 0,
                            left: 0,
                            right: 0,
                            bottom: 0,
                            backgroundColor: "rgba(0, 0, 0, 0.8)",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            zIndex: 1001,
                            padding: "20px",
                        }}
                        onClick={() => setShowConfirm(false)}
                    >
                        <div
                            style={{
                                backgroundColor: "#1a1a1a",
                                borderRadius: "12px",
                                padding: "24px",
                                maxWidth: "500px",
                                width: "100%",
                                border: "1px solid #3a3a3a",
                            }}
                            onClick={(e) => e.stopPropagation()}
                        >
                            <h3
                                style={{
                                    fontSize: "1.2rem",
                                    fontWeight: "600",
                                    color: "#ffffff",
                                    margin: "0 0 16px 0",
                                }}
                            >
                                Confirm Approval
                            </h3>
                            <p
                                style={{
                                    color: "#aaa",
                                    fontSize: "1rem",
                                    margin: "0 0 24px 0",
                                    lineHeight: "1.5",
                                }}
                            >
                                This will send this reply to recipient{" "}
                                <span
                                    style={{
                                        color: "#667eea",
                                        fontWeight: "600",
                                    }}
                                >
                                    {extractEmailAddress(email.from)}
                                </span>
                                . Are you sure you want to proceed?
                            </p>
                            <div
                                style={{
                                    display: "flex",
                                    gap: "12px",
                                    justifyContent: "flex-end",
                                }}
                            >
                                <Button
                                    onClick={() => setShowConfirm(false)}
                                    style={{
                                        backgroundColor: "#555",
                                        padding: "10px 20px",
                                    }}
                                >
                                    Cancel
                                </Button>
                                <Button
                                    onClick={handleConfirmApprove}
                                    style={{
                                        padding: "10px 20px",
                                    }}
                                >
                                    Confirm
                                </Button>
                            </div>
                        </div>
                    </div>
                )}

                {toast && (
                    <Toast
                        message={toast.message}
                        type={toast.type}
                        onClose={() => setToast(null)}
                    />
                )}
            </div>
        </div>
    );
};

export default ComposeEmailBox;
