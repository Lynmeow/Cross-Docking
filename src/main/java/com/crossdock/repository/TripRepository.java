package com.crossdock.repository;

import com.crossdock.model.Dock;
import com.crossdock.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findAllByOrderByScheduledArrivalDesc();
    List<Trip> findByDirectionAndStatusInOrderByScheduledArrivalAsc(Dock.Type direction, Collection<Trip.Status> statuses);
    List<Trip> findByStatusOrderByScheduledArrivalAsc(Trip.Status status);
    List<Trip> findByActualArrivalIsNotNull();
    boolean existsByCode(String code);
    long countByStatus(Trip.Status status);

    @Query("""
            select t from Trip t
            where t.dock.id = :dockId and t.status in :statuses
              and t.scheduledArrival < :end and t.scheduledDeparture > :start
            """)
    List<Trip> findOverlapping(@Param("dockId") Long dockId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end,
                               @Param("statuses") Collection<Trip.Status> statuses);
}