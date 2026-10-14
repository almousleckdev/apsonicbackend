package com.apsonic.backend.modules.contact.controller;

import com.apsonic.backend.modules.contact.dto.ContactMessageResponse;
import com.apsonic.backend.modules.contact.dto.CreateContactRequest;
import com.apsonic.backend.modules.contact.service.ContactMessageService;
import com.apsonic.backend.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {

    private final ContactMessageService contactMessageService;

    public ContactController(ContactMessageService contactMessageService) {
        this.contactMessageService = contactMessageService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ContactMessageResponse>> submitContactMessage(
            @Valid @RequestBody CreateContactRequest request
    ) {
        ContactMessageResponse response = contactMessageService.createContactMessage(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Your message has been sent successfully.", response));
    }
}
