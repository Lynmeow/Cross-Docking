package com.crossdock.repository;

import com.crossdock.model.Dock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DockRepository extends JpaRepository<Dock, Long> {

    List<Dock> findAllByOrderByCodeAsc();

    long countByStatus(Dock.Status status);

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dock d where d.id = :id")
    Optional<Dock> findByIdForUpdate(@Param("id") Long id);
}