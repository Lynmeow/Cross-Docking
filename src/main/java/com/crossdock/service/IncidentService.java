package com.crossdock.service;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.*;
import com.crossdock.repository.PalletRepository;
import com.crossdock.repository.TransferIncidentRepository;
import com.crossdock.repository.TripRepository;
import com.crossdock.util.AppUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Ngoại lệ chuyển tải */
@Service
public class IncidentService {

    private final TransferIncidentRepository repo;
    private final PalletRepository palletRepo;
    private final TripRepository tripRepo;
    private final CustodyService custodyService;
    private final AccessPolicy policy;
    private final AuditService audit;

    public IncidentService(TransferIncidentRepository repo, PalletRepository palletRepo, TripRepository tripRepo,
                           CustodyService custodyService, AccessPolicy policy, AuditService audit) {
        this.repo = repo;
        this.palletRepo = palletRepo;
        this.tripRepo = tripRepo;
        this.custodyService = custodyService;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TransferIncident> findAll() {
        return repo.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public List<TransferIncident> findByPallet(Long palletId) {
        return repo.findByPalletIdOrderByIdDesc(palletId);
    }

    @Transactional(readOnly = true)
    public List<TransferIncident> findByTrip(Long tripId) {
        return repo.findByTripIdOrderByIdDesc(tripId);
    }

    /** Nhân viên / quản lý báo ngoại lệ từ giao diện */
    @Transactional
    public TransferIncident report(TransferIncident.Type type, Long palletId, Long tripId,
                                   String description, boolean hold) {
        AppUser actor = policy.requireAny(Role.MANAGER, Role.INBOUND, Role.OUTBOUND);
        if (type == null) throw new BusinessException("Phải chọn loại ngoại lệ");
        String desc = AppUtils.requireText(description, "Mô tả");
        Pallet pallet = palletId == null ? null : palletRepo.findByIdForUpdate(palletId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy pallet #" + palletId));
        Trip trip = tripId == null ? null : tripRepo.findById(tripId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy chuyến #" + tripId));
        if (pallet == null && trip == null) {
            throw new BusinessException("Ngoại lệ phải gắn với ít nhất một pallet hoặc một chuyến");
        }
        return record(type, pallet, trip, desc, hold, actor);
    }

    /** Ghi ngoại lệ (dùng nội bộ bởi các service khác) */
    @Transactional
    public TransferIncident record(TransferIncident.Type type, Pallet pallet, Trip trip,
                                   String description, boolean hold, AppUser actor) {
        boolean holding = false;
        if (hold && pallet != null) {
            boolean newlyHeld = pallet.hold();
            holding = true;
            if (newlyHeld) {
                custodyService.record(pallet, CustodyEvent.Type.HOLD, pallet.getCustodianUser(),
                        pallet.getCurrentCustodian(), pallet.getCurrentLocation(), actor,
                        type.getLabel() + ": " + description);
            }
        }
        TransferIncident incident = repo.save(new TransferIncident(type, pallet, trip,
                AppUtils.truncate(description, 1000), holding, actor.getDisplayName(), AppUtils.now()));
        String ref = pallet != null ? pallet.getCode() : (trip != null ? trip.getCode() : "-");
        audit.log(AuditLog.Category.INCIDENT, "RAISE_" + type.name(), ref,
                "#" + incident.getId() + " " + type.getLabel() + ": " + description + (holding ? " (tạm giữ pallet)" : ""));
        return incident;
    }

    @Transactional
    public void resolve(Long incidentId, String resolution) {
        AppUser actor = policy.requireAny(Role.MANAGER);
        TransferIncident incident = repo.findById(incidentId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy ngoại lệ #" + incidentId));
        String res = AppUtils.requireText(resolution, "Cách xử lý");
        incident.resolve(actor.getDisplayName(), AppUtils.truncate(res, 1000), AppUtils.now());

        if (incident.isHoldsPallet() && incident.getPallet() != null) {
            Pallet pallet = palletRepo.findByIdForUpdate(incident.getPallet().getId()).orElseThrow();
            boolean otherHolds = repo.existsByPalletIdAndStatusAndHoldsPalletTrueAndIdNot(
                    pallet.getId(), TransferIncident.Status.OPEN, incident.getId());
            if (!otherHolds && pallet.getStatus() == Pallet.Status.ON_HOLD) {
                pallet.release();
                custodyService.record(pallet, CustodyEvent.Type.RELEASED, pallet.getCustodianUser(),
                        pallet.getCurrentCustodian(), pallet.getCurrentLocation(), actor,
                        "Đóng ngoại lệ #" + incident.getId() + ": " + res);
            }
        }
        audit.log(AuditLog.Category.INCIDENT, "RESOLVE", "#" + incident.getId(), "Xử lý: " + res);
    }
}