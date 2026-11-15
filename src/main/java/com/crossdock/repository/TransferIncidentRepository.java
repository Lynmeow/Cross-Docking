package com.crossdock.repository;

import com.crossdock.model.TransferIncident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferIncidentRepository extends JpaRepository<TransferIncident, Long> {
    List<TransferIncident> findAllByOrderByIdDesc();
    List<TransferIncident> findTop5ByOrderByIdDesc();
    List<TransferIncident> findByPalletIdOrderByIdDesc(Long palletId);
    List<TransferIncident> findByTripIdOrderByIdDesc(Long tripId);
    long countByStatus(TransferIncident.Status status);
    long countByType(TransferIncident.Type type);
    boolean existsByPalletIdAndStatusAndHoldsPalletTrueAndIdNot(Long palletId, TransferIncident.Status status, Long id);
}