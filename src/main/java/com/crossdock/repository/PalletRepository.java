package com.crossdock.repository;

import com.crossdock.model.Pallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PalletRepository extends JpaRepository<Pallet, Long> {
    List<Pallet> findAllByOrderByIdDesc();
    List<Pallet> findByStatusOrderByIdDesc(Pallet.Status status);
    List<Pallet> findByStatusNotOrderByReceivedAtAsc(Pallet.Status status);
    List<Pallet> findByOutboundTripIdOrderByCode(Long tripId);
    List<Pallet> findByInboundTripIdOrderByCode(Long tripId);
    List<Pallet> findByStatusAndOutboundTripIsNullOrderByCode(Pallet.Status status);
    List<Pallet> findByStatusAndPendingRecipientIdOrderByCode(Pallet.Status status, Long userId);
    Optional<Pallet> findByCode(String code);
    long countByOutboundTripId(Long tripId);
    long countByStatus(Pallet.Status status);
    boolean existsByCode(String code);

    /** SELECT ... WITH (UPDLOCK) – chống 2 người thao tác cùng 1 pallet đồng thời */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pallet p where p.id = :id")
    Optional<Pallet> findByIdForUpdate(@Param("id") Long id);
}