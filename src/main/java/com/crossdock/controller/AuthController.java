package com.crossdock.controller;

import com.crossdock.exception.BusinessException;
import com.crossdock.security.CurrentUser;
import com.crossdock.security.MustChangePasswordInterceptor;
import com.crossdock.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final CurrentUser currentUser;
    private final UserService userService;

    public AuthController(CurrentUser currentUser, UserService userService) {
        this.currentUser = currentUser;
        this.userService = userService;
    }

    @GetMapping("/")
    public String home() {
        return currentUser.find()
                .map(u -> "redirect:" + u.getRole().getHomeUrl())
                .orElse("redirect:/login");
    }

    @GetMapping("/login")
    public String login() {
        return currentUser.username().isPresent() ? "redirect:/" : "login";
    }

    @GetMapping("/change-password")
    public String changePasswordForm() {
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
                                 @RequestParam String confirmPassword, HttpSession session, RedirectAttributes ra) {
        try {
            userService.changeOwnPassword(currentPassword, newPassword, confirmPassword);
            session.removeAttribute(MustChangePasswordInterceptor.SESSION_KEY);
            ra.addFlashAttribute("success", "Đổi mật khẩu thành công");
            return "redirect:/";
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/change-password";
        }
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}