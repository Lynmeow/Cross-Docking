package com.crossdock.service;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.AppUser;
import com.crossdock.model.Role;
import com.crossdock.model.Trip;
import com.crossdock.security.CurrentUser;
import org.springframework.stereotype.Component;

/** Kiểm tra quyền ở tầng nghiệp vụ (bảo vệ thêm ngoài tầng URL) */
@Component
public class AccessPolicy {

    private final CurrentUser currentUser;

    public AccessPolicy(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public AppUser requireAny(Role... roles) {
        AppUser user = currentUser.require();
        if (!user.isActive()) throw new BusinessException("Tài khoản đã bị vô hiệu hóa");
        for (Role r : roles) {
            if (user.getRole() == r) return user;
        }
        throw new BusinessException("Vai trò " + user.getRole().getLabel() + " không có quyền thực hiện thao tác này");
    }

    /**
     * Tách biệt trách nhiệm: xe NHẬP do NV Inbound thao tác, xe XUẤT do NV Outbound thao tác.
     * Quản lý kho chỉ giám sát, không trực tiếp vận hành.
     */
    public AppUser requireForTrip(Trip trip) {
        return trip.isOutbound() ? requireAny(Role.OUTBOUND) : requireAny(Role.INBOUND);
    }
}