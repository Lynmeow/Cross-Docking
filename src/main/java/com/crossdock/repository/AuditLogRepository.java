package com.crossdock.repository;

import com.crossdock.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value = """
            select a from AuditLog a
            where a.category in :categories
              and lower(a.username) like lower(concat('%', :username, '%'))
              and a.occurredAt >= :fromTime and a.occurredAt < :toTime
            order by a.id desc
            """,
            countQuery = """
            select count(a) from AuditLog a
            where a.category in :categories
              and lower(a.username) like lower(concat('%', :username, '%'))
              and a.occurredAt >= :fromTime and a.occurredAt < :toTime
            """)
    Page<AuditLog> search(@Param("categories") Collection<AuditLog.Category> categories,
                          @Param("username") String username,
                          @Param("fromTime") LocalDateTime fromTime,
                          @Param("toTime") LocalDateTime toTime,
                          Pageable pageable);
}