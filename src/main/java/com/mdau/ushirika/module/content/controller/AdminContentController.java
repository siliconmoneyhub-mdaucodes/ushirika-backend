package com.mdau.ushirika.module.content.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.content.dto.*;
import com.mdau.ushirika.module.content.enums.ArticleStatus;
import com.mdau.ushirika.module.content.service.ArticleService;
import com.mdau.ushirika.module.content.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/admin/content")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_CONTENT')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Content — Admin", description = "Manage articles and media assets")
public class AdminContentController {

    private final ArticleService articleService;
    private final MediaService mediaService;

    // ─── Articles

    @GetMapping("/articles")
    @Operation(summary = "List all articles, optionally filtered by status")
    public ResponseEntity<ApiResponse<PagedResponse<ArticleSummaryDto>>> listAll(
            @RequestParam(required = false) ArticleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Articles retrieved",
                articleService.listAll(status,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/articles/{id}")
    @Operation(summary = "Get full article detail (admin view, all statuses)")
    public ResponseEntity<ApiResponse<ArticleDto>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Article retrieved", articleService.getById(id)));
    }

    @PostMapping("/articles")
    @Operation(summary = "Create a new article (starts as DRAFT)")
    public ResponseEntity<ApiResponse<ArticleDto>> create(@Valid @RequestBody ArticleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Article created", articleService.create(req)));
    }

    @PutMapping("/articles/{id}")
    @Operation(summary = "Update article content and metadata")
    public ResponseEntity<ApiResponse<ArticleDto>> update(
            @PathVariable UUID id, @Valid @RequestBody ArticleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Article updated", articleService.update(id, req)));
    }

    @PatchMapping("/articles/{id}/status")
    @Operation(summary = "Publish, unpublish, or archive an article")
    public ResponseEntity<ApiResponse<ArticleDto>> updateStatus(
            @PathVariable UUID id, @RequestParam ArticleStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Article status updated",
                articleService.updateStatus(id, status)));
    }

    @DeleteMapping("/articles/{id}")
    @Operation(summary = "Delete a DRAFT or ARCHIVED article permanently")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        articleService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Article deleted"));
    }

    // ─── Media

    @PostMapping(value = "/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an image or PDF to Cloudinary",
               description = "Returns publicId and URL. Use publicId for future deletion.")
    public ResponseEntity<ApiResponse<MediaAssetDto>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "articles") String folder
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("File uploaded", mediaService.upload(file, folder)));
    }

    @GetMapping("/media")
    @Operation(summary = "List uploaded media assets, optionally filtered by folder")
    public ResponseEntity<ApiResponse<PagedResponse<MediaAssetDto>>> listMedia(
            @RequestParam(required = false) String folder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Assets retrieved",
                mediaService.listAssets(folder,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @DeleteMapping("/media/{publicId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(summary = "Delete a media asset from Cloudinary and the database (SUPERADMIN only)")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(@PathVariable String publicId) {
        mediaService.delete(publicId);
        return ResponseEntity.ok(ApiResponse.ok("Media deleted"));
    }
}
