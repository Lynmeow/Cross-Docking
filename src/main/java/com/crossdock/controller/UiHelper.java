package com.crossdock.controller;

import com.crossdock.model.Asn;
import com.crossdock.model.AsnLine;
import com.crossdock.model.Role;
import com.crossdock.util.AppUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Dùng trong Thymeleaf: ${@ui.xxx(...)} */
@Component("ui")
public class UiHelper {

    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public String dt(LocalDateTime t) {
        return AppUtils.fmt(t);
    }

    public String dtInput(LocalDateTime t) {
        return t == null ? "" : t.format(INPUT);
    }

    public String num(double v) {
        return String.format("%,.1f", v);
    }

    public String shortHash(String h) {
        return (h == null || h.length() < 12) ? h : h.substring(0, 12) + "…";
    }

    public String roleBadge(Role r) {
        if (r == null) return "text-bg-secondary";
        return switch (r) {
            case ADMIN -> "text-bg-dark";
            case MANAGER -> "text-bg-primary";
            case INBOUND -> "text-bg-info";
            case OUTBOUND -> "text-bg-warning";
        };
    }

    public String badge(Enum<?> e) {
        if (e == null) return "text-bg-secondary";
        if (e instanceof Asn.Status s) {
            return switch (s) {
                case OPEN -> "text-bg-secondary";
                case RECEIVING -> "text-bg-warning";
                case CLOSED -> "text-bg-success";
            };
        }
        if (e instanceof AsnLine.Status s) {
            return switch (s) {
                case PENDING -> "text-bg-secondary";
                case RECEIVED -> "text-bg-success";
                case DAMAGED, MISSING -> "text-bg-danger";
            };
        }
        return switch (e.name()) {
            case "AVAILABLE", "DEPARTED", "SHIPPED", "RESOLVED" -> "text-bg-success";
            case "OCCUPIED", "AT_DOCK", "ASSIGNED", "HANDOVER_PENDING" -> "text-bg-warning";
            case "SCHEDULED", "RECEIVED" -> "text-bg-info";
            case "STAGED", "READY" -> "text-bg-primary";
            case "LOADED" -> "text-bg-dark";
            case "ON_HOLD", "OPEN" -> "text-bg-danger";
            default -> "text-bg-secondary";
        };
    }
}