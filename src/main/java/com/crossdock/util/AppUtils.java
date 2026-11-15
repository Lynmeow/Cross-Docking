package com.crossdock.util;

import com.crossdock.exception.BusinessException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class AppUtils {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private AppUtils() { }

    /** Thời gian hiện tại làm tròn tới giây (giúp hash chain of custody ổn định khi lưu DB) */
    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public static String fmt(LocalDateTime t) {
        return t == null ? "—" : t.format(FMT);
    }

    public static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " không được để trống");
        }
        return value.trim();
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    public static String nz(String s) {
        return s == null ? "" : s;
    }
}