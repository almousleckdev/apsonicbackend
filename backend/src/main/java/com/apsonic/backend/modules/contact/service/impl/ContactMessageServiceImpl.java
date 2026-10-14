package com.apsonic.backend.modules.contact.service.impl;

import com.apsonic.backend.modules.contact.domain.ContactMessage;
import com.apsonic.backend.modules.contact.dto.ContactMessageResponse;
import com.apsonic.backend.modules.contact.dto.CreateContactRequest;
import com.apsonic.backend.modules.contact.repository.ContactMessageRepository;
import com.apsonic.backend.modules.contact.service.ContactMessageService;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ContactMessageServiceImpl implements ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;

    public ContactMessageServiceImpl(ContactMessageRepository contactMessageRepository) {
        this.contactMessageRepository = contactMessageRepository;
    }

    @Override
    @Transactional
    public ContactMessageResponse createContactMessage(CreateContactRequest request) {
        ContactMessage message = ContactMessage.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .country(request.getCountry())
                .department(request.getDepartment())
                .subject(request.getSubject())
                .message(request.getMessage())
                .isRead(false)
                .isReplied(false)
                .build();

        ContactMessage saved = contactMessageRepository.save(message);
        return ContactMessageResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContactMessageResponse> getContactMessages(String status, Pageable pageable) {
        Page<ContactMessage> messages;
        if ("unread".equalsIgnoreCase(status)) {
            messages = contactMessageRepository.findByIsRead(false, pageable);
        } else if ("read".equalsIgnoreCase(status)) {
            messages = contactMessageRepository.findByIsRead(true, pageable);
        } else if ("replied".equalsIgnoreCase(status)) {
            messages = contactMessageRepository.findByIsReplied(true, pageable);
        } else {
            messages = contactMessageRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return messages.map(ContactMessageResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return contactMessageRepository.countByIsReadFalse();
    }

    @Override
    @Transactional
    public ContactMessageResponse markAsRead(Long id, boolean read) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", "id", id));
        message.setIsRead(read);
        ContactMessage updated = contactMessageRepository.save(message);
        return ContactMessageResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public ContactMessageResponse markAsReplied(Long id, boolean replied) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", "id", id));
        message.setIsReplied(replied);
        if (replied) {
            message.setIsRead(true);
            message.setRepliedAt(LocalDateTime.now());
        }
        ContactMessage updated = contactMessageRepository.save(message);
        return ContactMessageResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteContactMessage(Long id) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", "id", id));
        message.setDeleted(true);
        message.setDeletedAt(LocalDateTime.now());
        message.setDeletedBy(com.apsonic.backend.shared.util.SecurityUtils.getCurrentUsername().orElse("system"));
        contactMessageRepository.save(message);
    }
}
