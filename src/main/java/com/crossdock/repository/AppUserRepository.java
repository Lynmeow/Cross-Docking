package com.crossdock.repository;

import com.crossdock.model.AppUser;
import com.crossdock.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
    List<AppUser> findAllByOrderByRoleAscUsernameAsc();
    List<AppUser> findByRoleAndActiveTrueOrderByFullNameAsc(Role role);
    long countByRoleAndActiveTrue(Role role);
}