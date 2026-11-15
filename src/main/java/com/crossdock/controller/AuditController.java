package com.crossdock.controller;

import com.crossdock.model.AppUser;
import com.crossdock.model.AuditLog;
import com.crossdock.model.Role;
import com.crossdock.security.CurrentUser;
import com.crossdock.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Controller
public class AuditController {

    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AuditController(AuditService auditService, CurrentUser currentUser) {
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    @GetMapping("/audit")
    public String list(@RequestParam(required = false) AuditLog.Category category,
                       @RequestParam(required = false) String username,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        AppUser me = currentUser.require();
        Set<AuditLog.Category> allowed = me.getRole() == Role.ADMIN
                ? EnumSet.allOf(AuditLog.Category.class)
                : AuditService.MANAGER_VISIBLE;
        Collection<AuditLog.Category> filter =
                (category != null && allowed.contains(category)) ? List.of(category) : allowed;

        Page<AuditLog> logs = auditService.search(filter, username, from, to, page);
        model.addAttribute("logs", logs);
        model.addAttribute("categories", allowed);
        model.addAttribute("category", category);
        model.addAttribute("username", username);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "audit";
    }
}