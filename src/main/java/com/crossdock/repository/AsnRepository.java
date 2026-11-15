package com.crossdock.repository;

import com.crossdock.model.Asn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AsnRepository extends JpaRepository<Asn, Long> {
    Optional<Asn> findByTripId(Long tripId);
    boolean existsByTripId(Long tripId);
    boolean existsByAsnNumber(String asnNumber);
}