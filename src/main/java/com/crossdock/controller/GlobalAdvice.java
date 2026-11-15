package com.crossdock.controller;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.Role;
import com.crossdock.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.RequestContextUtils;

@ControllerAdvice
public class GlobalAdvice {

    private final CurrentUser currentUser;

    public GlobalAdvice(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * Biến dùng chung cho mọi trang.
     *  - canInbound / canOutbound : ĐƯỢC THAO TÁC (chỉ nhân viên vận hành)
     *  - canViewInbound / canViewOutbound : ĐƯỢC XEM (nhân viên + quản lý giám sát)
     *  - isOps : nhân viên vận hành hoặc quản lý (xem pallet, ngoại lệ, quét mã)
     */
    @ModelAttribute
    public void addUserInfo(Model model) {
        currentUser.find().ifPresent(u -> {
            Role r = u.getRole();
            model.addAttribute("me", u);
            model.addAttribute("isAdmin", r == Role.ADMIN);
            model.addAttribute("isManager", r == Role.MANAGER);
            model.addAttribute("canInbound", r == Role.INBOUND);
            model.addAttribute("canOutbound", r == Role.OUTBOUND);
            model.addAttribute("canViewInbound", r == Role.INBOUND || r == Role.MANAGER);
            model.addAttribute("canViewOutbound", r == Role.OUTBOUND || r == Role.MANAGER);
            model.addAttribute("isOutboundStaff", r == Role.OUTBOUND);
            model.addAttribute("isOps", r == Role.INBOUND || r == Role.OUTBOUND || r == Role.MANAGER);
        });
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException e, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("error", e.getMessage());
        return "redirect:/";
    }
}