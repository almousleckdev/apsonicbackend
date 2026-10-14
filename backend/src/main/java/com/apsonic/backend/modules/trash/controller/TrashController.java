package com.apsonic.backend.modules.trash.controller;

import com.apsonic.backend.modules.trash.dto.TrashItemResponse;
import com.apsonic.backend.modules.trash.service.TrashService;
import com.apsonic.backend.shared.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/trash")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class TrashController {

    private final TrashService trashService;

    public TrashController(TrashService trashService) {
        this.trashService = trashService;
    }

    @GetMapping("/{type}")
    public ResponseEntity<ApiResponse<List<TrashItemResponse>>> getTrashItems(@PathVariable("type") String type) {
        List<TrashItemResponse> items = trashService.getTrashItems(type);
        return ResponseEntity.ok(ApiResponse.success("Trash items retrieved", items));
    }

    @PostMapping("/{type}/{id}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreItem(@PathVariable("type") String type, @PathVariable("id") String id) {
        trashService.restoreItem(type, id);
        return ResponseEntity.ok(ApiResponse.success("Item restored successfully", null));
    }

    @DeleteMapping("/{type}/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePermanently(@PathVariable("type") String type, @PathVariable("id") String id) {
        trashService.deletePermanently(type, id);
        return ResponseEntity.ok(ApiResponse.success("Item deleted permanently", null));
    }

    @DeleteMapping("/{type}/clear")
    public ResponseEntity<ApiResponse<Void>> clearTrash(@PathVariable("type") String type) {
        trashService.clearTrash(type);
        return ResponseEntity.ok(ApiResponse.success("Trash cleared successfully", null));
    }
}
