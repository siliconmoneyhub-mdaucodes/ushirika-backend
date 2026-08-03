package com.mdau.ushirika.module.content.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.content.dto.AlbumSummaryDto;
import com.mdau.ushirika.module.content.dto.CommunityAlbumDto;
import com.mdau.ushirika.module.content.service.CommunityAlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/public/albums")
@RequiredArgsConstructor
@Tag(name = "Albums — Public", description = "Browse published community photo albums")
public class AlbumController {

    private final CommunityAlbumService albumService;

    @GetMapping
    @Operation(summary = "List published albums, newest event first")
    public ResponseEntity<ApiResponse<PagedResponse<AlbumSummaryDto>>> listPublished(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Albums retrieved",
                albumService.listPublished(
                        PageRequest.of(page, size, Sort.by("eventDate").descending()))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a published album with all its media")
    public ResponseEntity<ApiResponse<CommunityAlbumDto>> getAlbum(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Album retrieved", albumService.getPublished(id)));
    }
}
