package com.mdau.ushirika.module.actionitems.dto;

import java.util.List;

public record ActionItemsDto(
        int totalCount,
        int applicationsCount,
        int messagesCount,
        List<ActionItemDto> items
) {}
