package com.crossdock.service;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.AppUser;
import com.crossdock.model.AuditLog;
import com.crossdock.model.Role;
import com.crossdock.repository.AppUserRepository;
import com.crossdock.security.CurrentUser;
import com.crossdock.util.AppUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Quản lý tài khoản (chỉ Admin) + đổi mật khẩu + theo dõi đăng nhập */
@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,50}$");

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public UserService(AppUserRepository repo, PasswordEncoder encoder, AccessPolicy policy,
                       AuditService audit, CurrentUser currentUser) {
        this.repo = repo;
        this.encoder = encoder;
        this.policy = policy;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return repo.findAllByOrderByRoleAscUsernameAsc();
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        return repo.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản #" + id));
    }

    @Transactional(readOnly = true)
    public List<AppUser> activeByRole(Role role) {
        return repo.findByRoleAndActiveTrueOrderByFullNameAsc(role);
    }

    @Transactional
    public AppUser create(String username, String fullName, Role role, String tempPassword) {
        policy.requireAny(Role.ADMIN);
        String u = AppUtils.requireText(username, "Tên đăng nhập").toLowerCase();
        if (!USERNAME.matcher(u).matches()) {
            throw new BusinessException("Tên đăng nhập 3–50 ký tự, chỉ gồm chữ thường, số và . _ -");
        }
        if (repo.existsByUsername(u)) throw new BusinessException("Tên đăng nhập " + u + " đã tồn tại");
        if (role == null) throw new BusinessException("Phải chọn vai trò");
        validatePassword(tempPassword);
        AppUser user = repo.save(new AppUser(u, encoder.encode(tempPassword),
                AppUtils.requireText(fullName, "Họ tên"), role, true, AppUtils.now()));
        audit.log(AuditLog.Category.USER, "CREATE_USER", u,
                "Tạo tài khoản " + user.getDisplayName() + ", vai trò " + role.getLabel());
        return user;
    }

    @Transactional
    public void update(Long id, String fullName, Role role, boolean active) {
        AppUser admin = policy.requireAny(Role.ADMIN);
        AppUser user = get(id);
        String name = AppUtils.requireText(fullName, "Họ tên");
        if (role == null) throw new BusinessException("Phải chọn vai trò");

        boolean self = user.getId().equals(admin.getId());
        if (self && (!active || role != Role.ADMIN)) {
            throw new BusinessException("Không thể tự vô hiệu hóa hoặc tự đổi vai trò của chính mình");
        }
        boolean removingAdmin = user.getRole() == Role.ADMIN && user.isActive() && (role != Role.ADMIN || !active);
        if (removingAdmin && repo.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new BusinessException("Hệ thống phải còn ít nhất 1 admin đang hoạt động");
        }

        List<String> changes = new ArrayList<>();
        if (!user.getFullName().equals(name)) changes.add("Họ tên: " + user.getFullName() + " → " + name);
        if (user.getRole() != role) changes.add("Vai trò: " + user.getRole().getLabel() + " → " + role.getLabel());
        if (user.isActive() != active) {
            changes.add("Trạng thái: " + (user.isActive() ? "Hoạt động" : "Vô hiệu") + " → " + (active ? "Hoạt động" : "Vô hiệu"));
        }
        if (changes.isEmpty()) throw new BusinessException("Không có thay đổi nào");

        user.updateProfile(name, role, active);
        audit.log(AuditLog.Category.USER, "UPDATE_USER", user.getUsername(), String.join("; ", changes));
    }

    @Transactional
    public void resetPassword(Long id, String tempPassword) {
        policy.requireAny(Role.ADMIN);
        AppUser user = get(id);
        validatePassword(tempPassword);
        user.changePassword(encoder.encode(tempPassword), true);
        user.unlock();
        audit.log(AuditLog.Category.USER, "RESET_PASSWORD", user.getUsername(),
                "Đặt lại mật khẩu tạm và mở khóa, yêu cầu đổi khi đăng nhập");
    }

    @Transactional
    public void unlock(Long id) {
        policy.requireAny(Role.ADMIN);
        AppUser user = get(id);
        if (!user.isLocked()) throw new BusinessException("Tài khoản không bị khóa");
        user.unlock();
        audit.log(AuditLog.Category.USER, "UNLOCK_USER", user.getUsername(), "Mở khóa tài khoản");
    }

    @Transactional
    public void changeOwnPassword(String current, String newPassword, String confirm) {
        AppUser user = currentUser.require();
        if (current == null || !encoder.matches(current, user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu hiện tại không đúng");
        }
        if (!Objects.equals(newPassword, confirm)) throw new BusinessException("Xác nhận mật khẩu không khớp");
        validatePassword(newPassword);
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu mới phải khác mật khẩu cũ");
        }
        user.changePassword(encoder.encode(newPassword), false);
        audit.log(AuditLog.Category.AUTH, "CHANGE_PASSWORD", user.getUsername(), "Người dùng tự đổi mật khẩu");
    }

    @Transactional
    public AppUser recordLoginSuccess(String username) {
        AppUser user = repo.findByUsername(username).orElseThrow();
        user.registerSuccessfulLogin(AppUtils.now());
        audit.logFor(user.getUsername(), user.getRole().name(), AuditLog.Category.AUTH, "LOGIN",
                user.getUsername(), "Đăng nhập thành công");
        return user;
    }

    /** @return true nếu tài khoản vừa bị khóa */
    @Transactional
    public boolean recordLoginFailure(String username) {
        String u = username == null ? "" : username.trim().toLowerCase();
        Optional<AppUser> opt = repo.findByUsername(u);
        if (opt.isEmpty()) {
            audit.logFor(u, "-", AuditLog.Category.AUTH, "LOGIN_FAILED", u, "Tên đăng nhập không tồn tại");
            return false;
        }
        AppUser user = opt.get();
        boolean nowLocked = user.registerFailedLogin();
        audit.logFor(user.getUsername(), user.getRole().name(), AuditLog.Category.AUTH,
                nowLocked ? "ACCOUNT_LOCKED" : "LOGIN_FAILED", user.getUsername(),
                nowLocked ? "Sai mật khẩu " + AppUser.MAX_FAILED_ATTEMPTS + " lần – tài khoản bị khóa"
                          : "Sai mật khẩu (lần " + user.getFailedAttempts() + ")");
        return nowLocked;
    }

    public void validatePassword(String p) {
        if (p == null || p.length() < 8 || !p.matches(".*[A-Za-z].*") || !p.matches(".*\\d.*")) {
            throw new BusinessException("Mật khẩu tối thiểu 8 ký tự, gồm cả chữ và số");
        }
    }
}