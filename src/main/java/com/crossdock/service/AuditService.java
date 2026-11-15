package com.crossdock.service;

import com.crossdock.model.AuditLog;
import com.crossdock.repository.AuditLogRepository;
import com.crossdock.security.CurrentUser;
import com.crossdock.util.AppUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** Ghi và tra cứu nhật ký hoạt động */
@Service
public class AuditService {

    /** Quản lý kho chỉ xem nhật ký nghiệp vụ, không xem đăng nhập & tài khoản */
    public static final Set<AuditLog.Category> MANAGER_VISIBLE = EnumSet.of(
            AuditLog.Category.DOCK, AuditLog.Category.TRIP, AuditLog.Category.ASN,
            AuditLog.Category.PALLET, AuditLog.Category.HANDOVER, AuditLog.Category.INCIDENT);

    private final AuditLogRepository repo;
    private final CurrentUser currentUser;

    public AuditService(AuditLogRepository repo, CurrentUser currentUser) {
        this.repo = repo;
        this.currentUser = currentUser;
    }

    @Transactional
    public void log(AuditLog.Category category, String action, String target, String details) {
        save(currentUser.username().orElse("system"), currentRole(), category, action, target, details);
    }

    @Transactional
    public void logFor(String username, String role, AuditLog.Category category,
                       String action, String target, String details) {
        save(username, role, category, action, target, details);
    }

    private void save(String username, String role, AuditLog.Category category,
                      String action, String target, String details) {
        repo.save(new AuditLog(AppUtils.now(), AppUtils.truncate(AppUtils.nz(username), 50), role, category,
                action, AppUtils.truncate(target, 100), AppUtils.truncate(details, 2000), clientIp()));
    }

    private String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "-";
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst().orElse("-");
    }

    private String clientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest().getRemoteAddr();
        }
        return "-";
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(Collection<AuditLog.Category> categories, String username,
                                 LocalDate from, LocalDate to, int page) {
        LocalDateTime f = from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime t = to == null ? LocalDateTime.of(2999, 12, 31, 0, 0) : to.plusDays(1).atStartOfDay();
        return repo.search(categories, username == null ? "" : username.trim(), f, t,
                PageRequest.of(Math.max(page, 0), 50));
    }
}