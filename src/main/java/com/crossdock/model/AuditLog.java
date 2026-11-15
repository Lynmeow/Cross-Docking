package com.crossdock.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Nhật ký hoạt động – chỉ thêm, không sửa/xóa (không có setter) */
@Entity
@Table(name = "audit_logs", indexes = @Index(name = "ix_audit_time", columnList = "occurred_at"))
public class AuditLog {

    public enum Category {
        AUTH("Đăng nhập / mật khẩu"), USER("Tài khoản"), DOCK("Cửa"), TRIP("Chuyến xe"),
        ASN("ASN / Nhận hàng"), PALLET("Pallet"), HANDOVER("Bàn giao"), INCIDENT("Ngoại lệ");
        private final String label;
        Category(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(length = 20)
    private String userRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false, length = 60)
    private String actionName;

    @Column(length = 100)
    private String targetRef;

    @Column(length = 2000)
    private String details;

    @Column(length = 45)
    private String ipAddress;

    protected AuditLog() { }

    public AuditLog(LocalDateTime occurredAt, String username, String userRole, Category category,
                    String actionName, String targetRef, String details, String ipAddress) {
        this.occurredAt = occurredAt;
        this.username = username;
        this.userRole = userRole;
        this.category = category;
        this.actionName = actionName;
        this.targetRef = targetRef;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public Long getId() { return id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getUsername() { return username; }
    public String getUserRole() { return userRole; }
    public Category getCategory() { return category; }
    public String getActionName() { return actionName; }
    public String getTargetRef() { return targetRef; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
}