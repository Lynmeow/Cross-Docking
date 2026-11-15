package com.crossdock.security;

import com.crossdock.model.AppUser;
import com.crossdock.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;

    public LoginSuccessHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        AppUser user = userService.recordLoginSuccess(authentication.getName());
        if (user.isMustChangePassword()) {
            request.getSession().setAttribute(MustChangePasswordInterceptor.SESSION_KEY, Boolean.TRUE);
            response.sendRedirect(request.getContextPath() + "/change-password");
            return;
        }
        response.sendRedirect(request.getContextPath() + user.getRole().getHomeUrl());
    }
}