package com.apsonic.backend.modules.contact.controller;

import com.apsonic.backend.modules.contact.dto.ContactMessageResponse;
import com.apsonic.backend.modules.contact.dto.ContactUnreadCountResponse;
import com.apsonic.backend.modules.contact.service.ContactMessageService;
import com.apsonic.backend.shared.api.ApiResponse;
import com.apsonic.backend.shared.api.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/contact")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EDITOR')")
public class AdminContactController {

    private final ContactMessageService contactMessageService;

    public AdminContactController(ContactMessageService contactMessageService) {
        this.contactMessageService = contactMessageService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ContactMessageResponse>>> getContactMessages(
            @RequestParam(name = "status", required = false, defaultValue = "all") String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ContactMessageResponse> result = contactMessageService.getContactMessages(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Contact messages retrieved successfully", PageResponse.from(result)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<ContactUnreadCountResponse>> getUnreadCount() {
        long count = contactMessageService.getUnreadCount();
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved", new ContactUnreadCountResponse(count)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<ContactMessageResponse>> markAsRead(
            @PathVariable(name = "id") Long id,
            @RequestParam(name = "read", defaultValue = "true") boolean read
    ) {
        ContactMessageResponse response = contactMessageService.markAsRead(id, read);
        return ResponseEntity.ok(ApiResponse.success("Message status updated", response));
    }

    @PutMapping("/{id}/reply")
    public ResponseEntity<ApiResponse<ContactMessageResponse>> markAsReplied(
            @PathVariable(name = "id") Long id,
            @RequestParam(name = "replied", defaultValue = "true") boolean replied
    ) {
        ContactMessageResponse response = contactMessageService.markAsReplied(id, replied);
        return ResponseEntity.ok(ApiResponse.success("Reply status updated", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(@PathVariable(name = "id") Long id) {
        contactMessageService.deleteContactMessage(id);
        return ResponseEntity.ok(ApiResponse.success("Message deleted successfully", null));
    }
}
