package com.assignment.draftly.repository;

import com.assignment.draftly.entity.EmailReplyDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmailReplyDraftRepository
        extends JpaRepository<EmailReplyDraft, Long> {

    Optional<EmailReplyDraft> findByThreadIdAndDeletedFalse(String threadId);

    @Query("SELECT e FROM EmailReplyDraft e WHERE e.threadId = :threadId AND e.deleted = false ORDER BY e.createdAt DESC")
    List<EmailReplyDraft> findByThreadIdAndDeletedFalseOrderByCreatedAtDesc(@Param("threadId") String threadId);

    @Query("SELECT e FROM EmailReplyDraft e WHERE e.threadId = :threadId AND e.deleted = false AND e.status = :status ORDER BY e.createdAt DESC")
    List<EmailReplyDraft> findByThreadIdAndDeletedFalseAndStatusOrderByCreatedAtDesc(@Param("threadId") String threadId, @Param("status") com.assignment.draftly.enums.ReplyDraftStatus status);
}

