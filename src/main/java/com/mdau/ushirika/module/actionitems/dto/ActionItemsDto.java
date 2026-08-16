package com.mdau.ushirika.module.actionitems.dto;

import java.util.List;

public record ActionItemsDto(
        int totalCount,
        int applicationsCount,
        int messagesCount,
        int benevolenceCount,
        int mgrCount,
        int meetingsCount,
        int electionsCount,
        List<ActionItemDto> items
) {}
