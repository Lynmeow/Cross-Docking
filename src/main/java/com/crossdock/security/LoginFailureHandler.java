package com.crossdock.security;

import com.crossdock.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final UserService userService;

    public LoginFailureHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String reason = "error";
        if (exception instanceof LockedException) {
            reason = "locked";
        } else if (exception instanceof DisabledException) {
            reason = "disabled";
        } else if (exception instanceof BadCredentialsException) {
            boolean nowLocked = userService.recordLoginFailure(request.getParameter("username"));
            if (nowLocked) reason = "locked";
        }
        response.sendRedirect(request.getContextPath() + "/login?" + reason);
    }
}