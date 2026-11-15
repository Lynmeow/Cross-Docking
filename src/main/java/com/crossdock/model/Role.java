package com.crossdock.model;

public enum Role {
    ADMIN("Quản trị hệ thống", "/admin/users"),
    MANAGER("Quản lý kho", "/dashboard"),
    INBOUND("Nhân viên Inbound", "/inbound"),
    OUTBOUND("Nhân viên Outbound", "/outbound");

    private final String label;
    private final String homeUrl;

    Role(String label, String homeUrl) {
        this.label = label;
        this.homeUrl = homeUrl;
    }

    public String getLabel() { return label; }
    public String getHomeUrl() { return homeUrl; }
}