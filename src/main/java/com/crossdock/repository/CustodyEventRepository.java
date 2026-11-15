package com.crossdock.repository;

import com.crossdock.model.CustodyEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustodyEventRepository extends JpaRepository<CustodyEvent, Long> {

    List<CustodyEvent> findByPalletIdOrderBySequenceNoAsc(Long palletId);

    Optional<CustodyEvent> findTopByPalletIdOrderBySequenceNoDesc(Long palletId);

    List<CustodyEvent> findTop10ByOrderByIdDesc();
}