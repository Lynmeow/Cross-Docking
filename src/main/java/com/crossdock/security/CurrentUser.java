package com.crossdock.security;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.AppUser;
import com.crossdock.repository.AppUserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Lấy người dùng đang đăng nhập */
@Component
public class CurrentUser {

    private final AppUserRepository userRepo;

    public CurrentUser(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public Optional<String> username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }

    public Optional<AppUser> find() {
        return username().flatMap(userRepo::findByUsername);
    }

    public AppUser require() {
        return find().orElseThrow(() -> new BusinessException("Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại"));
    }
}