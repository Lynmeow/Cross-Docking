package com.crossdock.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/** Bắt buộc đổi mật khẩu tạm trước khi dùng hệ thống */
public class MustChangePasswordInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY = "MUST_CHANGE_PASSWORD";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY))) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!path.startsWith("/change-password") && !path.startsWith("/login")
                    && !path.startsWith("/error") && !path.startsWith("/access-denied")) {
                response.sendRedirect(request.getContextPath() + "/change-password");
                return false;
            }
        }
        return true;
    }
}