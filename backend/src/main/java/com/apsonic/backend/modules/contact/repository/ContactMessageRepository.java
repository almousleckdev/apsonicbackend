package com.apsonic.backend.modules.contact.repository;

import com.apsonic.backend.modules.contact.domain.ContactMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    long countByIsReadFalse();
    Page<ContactMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<ContactMessage> findByIsRead(Boolean isRead, Pageable pageable);
    Page<ContactMessage> findByIsReplied(Boolean isReplied, Pageable pageable);
}
