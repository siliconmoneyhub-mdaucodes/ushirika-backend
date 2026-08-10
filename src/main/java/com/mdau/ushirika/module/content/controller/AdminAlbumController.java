package com.mdau.ushirika.module.content.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.content.dto.*;
import com.mdau.ushirika.module.content.enums.AlbumStatus;
import com.mdau.ushirika.module.content.service.CommunityAlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/albums")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_CONTENT')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Albums — Admin", description = "Manage community photo albums")
public class AdminAlbumController {

    private final CommunityAlbumService albumService;

    // ─── Album CRUD

    @GetMapping
    @Operation(summary = "List all albums, optionally filtered by status")
    public ResponseEntity<ApiResponse<PagedResponse<AlbumSummaryDto>>> listAll(
            @RequestParam(required = false) AlbumStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Albums retrieved",
                albumService.listAll(status,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full album detail including all media")
    public ResponseEntity<ApiResponse<CommunityAlbumDto>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Album retrieved", albumService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new album (starts as DRAFT)")
    public ResponseEntity<ApiResponse<CommunityAlbumDto>> create(
            @Valid @RequestBody CommunityAlbumRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Album created", albumService.create(req)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update album metadata")
    public ResponseEntity<ApiResponse<CommunityAlbumDto>> update(
            @PathVariable UUID id, @Valid @RequestBody CommunityAlbumRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Album updated", albumService.update(id, req)));
    }

    @PatchMapping("/{id}/publish")
    @Operation(summary = "Publish a DRAFT album — makes it visible on the public site")
    public ResponseEntity<ApiResponse<CommunityAlbumDto>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Album published", albumService.publish(id)));
    }

    @PatchMapping("/{id}/unpublish")
    @Operation(summary = "Unpublish an album — moves it back to DRAFT")
    public ResponseEntity<ApiResponse<CommunityAlbumDto>> unpublish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Album unpublished", albumService.unpublish(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Permanently delete a DRAFT album and all its media entries")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        albumService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Album deleted"));
    }

    // ─── Media management

    @PostMapping("/{albumId}/media")
    @Operation(summary = "Add a media item to an album (use /admin/content/media/upload first to get publicId + url)")
    public ResponseEntity<ApiResponse<CommunityAlbumDto>> addMedia(
            @PathVariable UUID albumId,
            @Valid @RequestBody AddAlbumMediaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Media added", albumService.addMedia(albumId, req)));
    }

    @DeleteMapping("/{albumId}/media/{mediaId}")
    @Operation(summary = "Remove a specific media item from an album")
    public ResponseEntity<ApiResponse<Void>> removeMedia(
            @PathVariable UUID albumId,
            @PathVariable UUID mediaId) {
        albumService.removeMedia(albumId, mediaId);
        return ResponseEntity.ok(ApiResponse.ok("Media removed"));
    }
}
