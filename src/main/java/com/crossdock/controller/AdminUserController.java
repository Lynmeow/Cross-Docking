package com.crossdock.controller;

import com.crossdock.model.Role;
import com.crossdock.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController extends BaseController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }

    @PostMapping
    public String create(@RequestParam String username, @RequestParam String fullName,
                         @RequestParam Role role, @RequestParam String password, RedirectAttributes ra) {
        return exec(ra, "/admin/users", "Đã cấp tài khoản " + username.trim().toLowerCase()
                        + " (người dùng phải đổi mật khẩu ở lần đăng nhập đầu)",
                () -> userService.create(username, fullName, role, password));
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.get(id));
        model.addAttribute("roles", Role.values());
        return "admin/user-edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam String fullName, @RequestParam Role role,
                         @RequestParam(defaultValue = "false") boolean active, RedirectAttributes ra) {
        return exec(ra, "/admin/users/" + id, "Đã cập nhật tài khoản",
                () -> userService.update(id, fullName, role, active));
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, @RequestParam String password, RedirectAttributes ra) {
        return exec(ra, "/admin/users/" + id, "Đã đặt lại mật khẩu tạm", () -> userService.resetPassword(id, password));
    }

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id, RedirectAttributes ra) {
        return exec(ra, "/admin/users/" + id, "Đã mở khóa tài khoản", () -> userService.unlock(id));
    }
}