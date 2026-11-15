package com.crossdock.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
public class AppUser {

    public static final int MAX_FAILED_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    private boolean active = true;
    private boolean locked = false;
    private int failedAttempts = 0;
    private boolean mustChangePassword = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @Version
    private Long version;

    protected AppUser() { }

    public AppUser(String username, String passwordHash, String fullName, Role role,
                   boolean mustChangePassword, LocalDateTime createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = createdAt;
    }

    public String getDisplayName() {
        return fullName + " (" + username + ")";
    }

    /** @return true nếu tài khoản VỪA bị khóa do sai quá số lần cho phép */
    public boolean registerFailedLogin() {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS && !locked) {
            locked = true;
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(LocalDateTime now) {
        failedAttempts = 0;
        lastLoginAt = now;
    }

    public void unlock() {
        locked = false;
        failedAttempts = 0;
    }

    public void changePassword(String newHash, boolean mustChangeNextLogin) {
        this.passwordHash = newHash;
        this.mustChangePassword = mustChangeNextLogin;
    }

    public void updateProfile(String fullName, Role role, boolean active) {
        this.fullName = fullName;
        this.role = role;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean isLocked() { return locked; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
}