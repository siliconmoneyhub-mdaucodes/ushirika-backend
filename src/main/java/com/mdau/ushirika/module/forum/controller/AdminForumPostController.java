package com.mdau.ushirika.module.forum.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.forum.dto.AdminEditForumPostRequest;
import com.mdau.ushirika.module.forum.dto.ForumPostDto;
import com.mdau.ushirika.module.forum.dto.RejectForumPostRequest;
import com.mdau.ushirika.module.forum.enums.ForumPostStatus;
import com.mdau.ushirika.module.forum.service.ForumPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/forum")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_CONTENT')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Forum — Admin", description = "Review, edit, approve, reject, and feature member stories")
public class AdminForumPostController {

    private final ForumPostService forumPostService;

    @GetMapping
    @Operation(summary = "List all forum posts with optional status filter")
    public ResponseEntity<ApiResponse<PagedResponse<ForumPostDto>>> list(
            @RequestParam(required = false) ForumPostStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok("Posts retrieved", forumPostService.listAll(status, pr)));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve a forum post and make it publicly visible")
    public ResponseEntity<ApiResponse<ForumPostDto>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Post approved", forumPostService.approve(id)));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject a forum post with a reason for the member")
    public ResponseEntity<ApiResponse<ForumPostDto>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectForumPostRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Post rejected", forumPostService.reject(id, req.reason())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a post's content, notes, and featured flag")
    public ResponseEntity<ApiResponse<ForumPostDto>> edit(
            @PathVariable UUID id,
            @Valid @RequestBody AdminEditForumPostRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Post updated", forumPostService.edit(id, req)));
    }

    @PatchMapping("/{id}/featured")
    @Operation(summary = "Toggle featured pin on an approved post")
    public ResponseEntity<ApiResponse<ForumPostDto>> toggleFeatured(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Featured status toggled", forumPostService.toggleFeatured(id)));
    }
}
