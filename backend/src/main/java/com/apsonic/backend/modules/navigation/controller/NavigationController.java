package com.apsonic.backend.modules.navigation.controller;

import com.apsonic.backend.modules.navigation.domain.MenuKey;
import com.apsonic.backend.modules.navigation.dto.NavigationDtos.*;
import com.apsonic.backend.modules.navigation.service.NavigationService;
import com.apsonic.backend.shared.api.ApiResponse;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Navigation", description = "Header and footer menus")
public class NavigationController {

    private final NavigationService navigation;

    @GetMapping("/api/v1/navigation/{menu}")
    public ApiResponse<ResolvedMenu> published(@PathVariable String menu) {
        return ApiResponse.success(navigation.published(key(menu)));
    }

    @GetMapping("/api/v1/admin/navigation/page-options")
    @PreAuthorize("hasAuthority('MANAGE_NAVIGATION')")
    public ApiResponse<List<PageOption>> pageOptions() {
        return ApiResponse.success(navigation.pageOptions());
    }

    @GetMapping("/api/v1/admin/navigation/{menu}")
    @PreAuthorize("hasAuthority('MANAGE_NAVIGATION')")
    public ApiResponse<MenuView> get(@PathVariable String menu) {
        return ApiResponse.success(navigation.get(key(menu)));
    }

    @GetMapping("/api/v1/admin/navigation/{menu}/preview")
    @PreAuthorize("hasAuthority('MANAGE_NAVIGATION')")
    public ApiResponse<ResolvedMenu> preview(@PathVariable String menu) {
        return ApiResponse.success(navigation.previewDraft(key(menu)));
    }

    @PutMapping("/api/v1/admin/navigation/{menu}")
    @PreAuthorize("hasAuthority('MANAGE_NAVIGATION')")
    public ApiResponse<MenuView> save(@PathVariable String menu, @Valid @RequestBody SaveMenuRequest request) {
        return ApiResponse.success("Menu draft saved", navigation.saveDraft(key(menu), request.expectedVersion(), request.items()));
    }

    @PostMapping("/api/v1/admin/navigation/{menu}/trash")
    @PreAuthorize("hasAuthority('MANAGE_NAVIGATION')")
    public ApiResponse<MenuView> trash(@PathVariable String menu, @Valid @RequestBody TrashItemRequest request) {
        return ApiResponse.success("Menu item moved to the trash", navigation.trashItem(key(menu), request.item(), request.title()));
    }

    @PostMapping("/api/v1/admin/navigation/{menu}/reset")
    @PreAuthorize("hasAuthority('MANAGE_NAVIGATION')")
    public ApiResponse<MenuView> reset(@PathVariable String menu, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success("The original menu is back", navigation.resetToOriginal(key(menu), request.expectedVersion()));
    }

    @PutMapping("/api/v1/admin/navigation/footer/pages/{pageId}")
    @PreAuthorize("hasAuthority('MANAGE_NAVIGATION')")
    public ApiResponse<Void> showPageInFooter(@PathVariable long pageId, @Valid @RequestBody FooterPageRequest request) {
        navigation.showPageInFooter(pageId, request.section());
        return ApiResponse.success("The page will show in the footer", null);
    }

    @DeleteMapping("/api/v1/admin/navigation/footer/pages/{pageId}")
    @PreAuthorize("hasAuthority('MANAGE_NAVIGATION')")
    public ApiResponse<Void> hidePageFromFooter(@PathVariable long pageId) {
        navigation.hidePageFromFooter(pageId);
        return ApiResponse.success("The page no longer shows in the footer", null);
    }

    @PostMapping("/api/v1/admin/navigation/{menu}/publish")
    @PreAuthorize("hasAuthority('MANAGE_NAVIGATION')")
    public ApiResponse<MenuView> publish(@PathVariable String menu, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success("Menu published", navigation.publish(key(menu), request.expectedVersion()));
    }

    @PostMapping("/api/v1/admin/navigation/{menu}/discard")
    @PreAuthorize("hasAuthority('MANAGE_NAVIGATION')")
    public ApiResponse<MenuView> discard(@PathVariable String menu, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.success("Draft changes discarded", navigation.discardDraft(key(menu), request.expectedVersion()));
    }

    private static MenuKey key(String menu) {
        return MenuKey.parse(menu).orElseThrow(() -> new ResourceNotFoundException("Menu", "key", menu));
    }
}
