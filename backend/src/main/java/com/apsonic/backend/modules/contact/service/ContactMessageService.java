package com.apsonic.backend.modules.contact.service;

import com.apsonic.backend.modules.contact.dto.ContactMessageResponse;
import com.apsonic.backend.modules.contact.dto.CreateContactRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContactMessageService {
    ContactMessageResponse createContactMessage(CreateContactRequest request);
    Page<ContactMessageResponse> getContactMessages(String status, Pageable pageable);
    long getUnreadCount();
    ContactMessageResponse markAsRead(Long id, boolean read);
    ContactMessageResponse markAsReplied(Long id, boolean replied);
    void deleteContactMessage(Long id);
}
