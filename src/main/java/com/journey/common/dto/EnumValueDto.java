package com.journey.common.dto;

/**
 * The single wire shape for every enum-backed value (status, role, question type).
 *
 * <p>The database stores only {@code code} (a 1001-based integer). The backend enums own the
 * code/english/arabic triple, and every API response carries all three so the frontend can render
 * either language without keeping its own lookup table. Inbound requests send just the {@code code}.
 */
public record EnumValueDto(
        int code,
        String english,
        String arabic
) {}
