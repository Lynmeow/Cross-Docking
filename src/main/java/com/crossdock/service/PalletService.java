package com.crossdock.service;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.*;
import com.crossdock.repository.AppUserRepository;
import com.crossdock.repository.PalletRepository;
import com.crossdock.repository.TripRepository;
import com.crossdock.util.AppUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

@Service
public class PalletService {

    private final PalletRepository palletRepo;
    private final TripRepository tripRepo;
    private final AppUserRepository userRepo;
    private final CustodyService custodyService;
    private final IncidentService incidentService;
    private final AccessPolicy policy;
    private final AuditService audit;

    public PalletService(PalletRepository palletRepo, TripRepository tripRepo, AppUserRepository userRepo,
                         CustodyService custodyService, IncidentService incidentService,
                         AccessPolicy policy, AuditService audit) {
        this.palletRepo = palletRepo;
        this.tripRepo = tripRepo;
        this.userRepo = userRepo;
        this.custodyService = custodyService;
        this.incidentService = incidentService;
        this.policy = policy;
        this.audit = audit;
    }

    // ================= Truy vấn =================

    @Transactional(readOnly = true)
    public List<Pallet> findAll(Pallet.Status status) {
        return status == null ? palletRepo.findAllByOrderByIdDesc() : palletRepo.findByStatusOrderByIdDesc(status);
    }

        @Transactional(readOnly = true)
    public Optional<Pallet> findByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return palletRepo.findByCode(code.trim().toUpperCase());
    }

    @Transactional(readOnly = true)
    public Pallet get(Long id) {
        return palletRepo.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy pallet #" + id));
    }

    @Transactional(readOnly = true)
    public List<Pallet> onTrip(Long tripId) {
        return palletRepo.findByOutboundTripIdOrderByCode(tripId);
    }

    @Transactional(readOnly = true)
    public long countOnTrip(Long tripId) {
        return palletRepo.countByOutboundTripId(tripId);
    }

    @Transactional(readOnly = true)
    public List<Pallet> fromInbound(Long tripId) {
        return palletRepo.findByInboundTripIdOrderByCode(tripId);
    }

    @Transactional(readOnly = true)
    public List<Pallet> ready() {
        return palletRepo.findByStatusAndOutboundTripIsNullOrderByCode(Pallet.Status.READY);
    }

    @Transactional(readOnly = true)
    public List<Pallet> readyFor(Trip trip) {
        return ready().stream().filter(p -> p.getDestination().equalsIgnoreCase(trip.getDestination())).toList();
    }

    @Transactional(readOnly = true)
    public List<Pallet> handoverPendingFor(Long userId) {
        return palletRepo.findByStatusAndPendingRecipientIdOrderByCode(Pallet.Status.HANDOVER_PENDING, userId);
    }

    // ================= Inbound =================

    /** Tạo pallet khi dỡ từ xe nhập (gọi từ AsnService) */
    @Transactional
    public Pallet createReceived(Trip trip, String code, String contents, double weightKg,
                                 String destination, AppUser actor, String note) {
        String c = AppUtils.requireText(code, "Mã pallet").toUpperCase();
        if (palletRepo.existsByCode(c)) throw new BusinessException("Mã pallet " + c + " đã tồn tại trong hệ thống");
        if (trip.isOutbound() || trip.getStatus() != Trip.Status.AT_DOCK) {
            throw new BusinessException("Chỉ nhận pallet từ chuyến NHẬP đang ở cửa");
        }
        if (weightKg <= 0) throw new BusinessException("Khối lượng phải lớn hơn 0");
        String location = "Cửa " + trip.getDock().getCode();
        Pallet p = palletRepo.save(new Pallet(c, AppUtils.requireText(contents, "Nội dung hàng"), weightKg,
                AppUtils.requireText(destination, "Điểm đến"), trip, trip.getCustodianName(), location, AppUtils.now()));
        custodyService.record(p, CustodyEvent.Type.RECEIVED, actor, actor.getDisplayName(), location, actor,
                "Dỡ từ chuyến " + trip.getCode() + (note != null ? ". " + note : ""));
        return p;
    }

    @Transactional
    public void stage(Long id, String zone) {
        AppUser actor = policy.requireAny(Role.INBOUND);
        Pallet p = lock(id);
        String z = AppUtils.requireText(zone, "Khu tập kết").toUpperCase();
        p.stage();
        custodyService.record(p, CustodyEvent.Type.STAGED, actor, actor.getDisplayName(), "Khu tập kết " + z, actor, null);
        audit.log(AuditLog.Category.PALLET, "STAGE", p.getCode(), "Đưa vào khu tập kết " + z);
    }

    // ================= Bàn giao 2 bước =================

    @Transactional
    public int requestHandover(List<Long> ids, Long recipientId) {
        AppUser actor = policy.requireAny(Role.INBOUND);
        if (ids == null || ids.isEmpty()) throw new BusinessException("Chưa chọn pallet nào");
        if (recipientId == null) throw new BusinessException("Chưa chọn nhân viên Outbound nhận bàn giao");
        AppUser to = userRepo.findById(recipientId).orElseThrow(() -> new BusinessException("Không tìm thấy người nhận"));
        if (to.getRole() != Role.OUTBOUND || !to.isActive()) {
            throw new BusinessException("Người nhận phải là nhân viên Outbound đang hoạt động");
        }
        List<String> codes = new ArrayList<>();
        for (Long id : ids) {
            Pallet p = lock(id);
            p.requestHandover(to);
            custodyService.record(p, CustodyEvent.Type.HANDOVER_REQUESTED, p.getCustodianUser(), p.getCurrentCustodian(),
                    p.getCurrentLocation(), actor, "Chờ " + to.getDisplayName() + " xác nhận");
            codes.add(p.getCode());
        }
        audit.log(AuditLog.Category.HANDOVER, "REQUEST_HANDOVER", to.getUsername(),
                "Bàn giao " + codes + " cho " + to.getDisplayName());
        return codes.size();
    }

    @Transactional
    public int acceptHandover(List<Long> ids) {
        AppUser actor = policy.requireAny(Role.OUTBOUND);
        if (ids == null || ids.isEmpty()) throw new BusinessException("Chưa chọn pallet nào");
        List<String> codes = new ArrayList<>();
        for (Long id : ids) {
            Pallet p = lock(id);
            p.acceptHandover(actor);
            custodyService.record(p, CustodyEvent.Type.HANDOVER_ACCEPTED, actor, actor.getDisplayName(),
                    p.getCurrentLocation(), actor, "Đã kiểm đếm và nhận bàn giao");
            codes.add(p.getCode());
        }
        audit.log(AuditLog.Category.HANDOVER, "ACCEPT_HANDOVER", actor.getUsername(), "Nhận bàn giao " + codes);
        return codes.size();
    }

    @Transactional
    public void rejectHandover(Long id, String reason) {
        AppUser actor = policy.requireAny(Role.OUTBOUND);
        String r = AppUtils.requireText(reason, "Lý do từ chối");
        Pallet p = lock(id);
        p.rejectHandover(actor);
        custodyService.record(p, CustodyEvent.Type.HANDOVER_REJECTED, p.getCustodianUser(), p.getCurrentCustodian(),
                p.getCurrentLocation(), actor, r);
        incidentService.record(TransferIncident.Type.HANDOVER_REJECTED, p, null,
                actor.getDisplayName() + " từ chối nhận pallet " + p.getCode() + ": " + r, false, actor);
        audit.log(AuditLog.Category.HANDOVER, "REJECT_HANDOVER", p.getCode(), "Lý do: " + r);
    }

    // ================= Outbound =================

    /**
     * Gán pallet vào chuyến xuất – CHỐNG GÁN 2 CHUYẾN:
     *  1. Khóa dòng pallet (PESSIMISTIC_WRITE) => 2 request đồng thời phải xếp hàng.
     *  2. Kiểm tra outboundTrip != null => từ chối + ghi ngoại lệ + nhật ký.
     *  3. @Version trên Pallet => lớp chặn cuối (optimistic lock).
     * noRollbackFor: bản ghi ngoại lệ vẫn được lưu dù thao tác bị từ chối
     * (mọi kiểm tra đều chạy TRƯỚC khi thay đổi pallet).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void assign(Long palletId, Long tripId) {
        AppUser actor = policy.requireAny(Role.OUTBOUND);
        Pallet p = lock(palletId);
        Trip trip = tripRepo.findById(tripId).orElseThrow(() -> new BusinessException("Không tìm thấy chuyến #" + tripId));

        if (p.getOutboundTrip() != null) {
            if (p.getOutboundTrip().getId().equals(trip.getId())) {
                throw new BusinessException("Pallet " + p.getCode() + " đã nằm trong chuyến " + trip.getCode());
            }
            String msg = "Pallet " + p.getCode() + " đã được gán cho chuyến " + p.getOutboundTrip().getCode()
                    + " – từ chối gán thêm vào chuyến " + trip.getCode();
            incidentService.record(TransferIncident.Type.DOUBLE_ASSIGNMENT_ATTEMPT, p, trip, msg, false, actor);
            audit.log(AuditLog.Category.PALLET, "ASSIGN_BLOCKED", p.getCode(), msg);
            throw new BusinessException(msg);
        }
        if (p.getStatus() != Pallet.Status.READY) {
            throw new BusinessException("Pallet " + p.getCode() + " đang '" + p.getStatus().getLabel()
                    + "' – chỉ pallet 'Sẵn sàng xuất' (Outbound đã nhận bàn giao) mới gán được");
        }
        validateTarget(p, trip, actor);

        p.assignTo(trip);
        custodyService.record(p, CustodyEvent.Type.ASSIGNED, p.getCustodianUser(), p.getCurrentCustodian(),
                p.getCurrentLocation(), actor, "Gán vào chuyến " + trip.getCode());
        audit.log(AuditLog.Category.PALLET, "ASSIGN", p.getCode(), "Gán vào chuyến " + trip.getCode());
    }

    @Transactional
    public void load(Long palletId) {
        AppUser actor = policy.requireAny(Role.OUTBOUND);
        Pallet p = lock(palletId);
        p.load();
        Trip trip = p.getOutboundTrip();
        custodyService.record(p, CustodyEvent.Type.LOADED, null, trip.getCustodianName(),
                "Cửa " + trip.getDock().getCode(), actor, "Lên xe " + trip.getTruckPlate());
        audit.log(AuditLog.Category.PALLET, "LOAD", p.getCode(), "Xếp lên xe chuyến " + trip.getCode());
    }

    // ================= Quản lý kho can thiệp =================

    @Transactional
    public void unassign(Long palletId, String reason) {
        AppUser actor = policy.requireAny(Role.MANAGER);
        String r = AppUtils.requireText(reason, "Lý do");
        Pallet p = lock(palletId);
        Trip old = p.unassign();
        custodyService.record(p, CustodyEvent.Type.UNASSIGNED, p.getCustodianUser(), p.getCurrentCustodian(),
                p.getCurrentLocation(), actor, "Gỡ khỏi chuyến " + old.getCode() + ". Lý do: " + r);
        audit.log(AuditLog.Category.PALLET, "UNASSIGN", p.getCode(), "Gỡ khỏi chuyến " + old.getCode() + ". Lý do: " + r);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void reassign(Long palletId, Long newTripId, String reason) {
        AppUser actor = policy.requireAny(Role.MANAGER);
        String r = AppUtils.requireText(reason, "Lý do");
        Pallet p = lock(palletId);
        if (p.getOutboundTrip() == null || p.getStatus() != Pallet.Status.ASSIGNED) {
            throw new BusinessException("Chỉ chuyển chuyến cho pallet đang 'Đã gán chuyến'");
        }
        if (newTripId == null) throw new BusinessException("Chưa chọn chuyến mới");
        Trip newTrip = tripRepo.findById(newTripId).orElseThrow(() -> new BusinessException("Không tìm thấy chuyến"));
        if (newTrip.getId().equals(p.getOutboundTrip().getId())) {
            throw new BusinessException("Pallet đã ở chuyến " + newTrip.getCode());
        }
        validateTarget(p, newTrip, actor);

        Trip old = p.unassign();
        p.assignTo(newTrip);
        String note = "Từ " + old.getCode() + " sang " + newTrip.getCode() + ". Lý do: " + r;
        custodyService.record(p, CustodyEvent.Type.REASSIGNED, p.getCustodianUser(), p.getCurrentCustodian(),
                p.getCurrentLocation(), actor, note);
        audit.log(AuditLog.Category.PALLET, "REASSIGN", p.getCode(), note);
    }

    // ================= Hỗ trợ =================

    private void validateTarget(Pallet p, Trip trip, AppUser actor) {
        if (!trip.isAcceptingPallets()) {
            throw new BusinessException("Chuyến " + trip.getCode() + " không nhận pallet (phải là chuyến XUẤT chưa rời kho)");
        }
        if (!p.getDestination().equalsIgnoreCase(trip.getDestination())) {
            String msg = "Pallet " + p.getCode() + " đi " + p.getDestination() + " nhưng chuyến "
                    + trip.getCode() + " đi " + trip.getDestination();
            incidentService.record(TransferIncident.Type.WRONG_DESTINATION, p, trip, msg, false, actor);
            throw new BusinessException(msg);
        }
        long count = palletRepo.countByOutboundTripId(trip.getId());
        if (count >= trip.getCapacity()) {
            String msg = "Chuyến " + trip.getCode() + " đã đủ " + trip.getCapacity() + " pallet";
            incidentService.record(TransferIncident.Type.OVER_CAPACITY, p, trip, msg, false, actor);
            throw new BusinessException(msg);
        }
    }

    private Pallet lock(Long id) {
        if (id == null) throw new BusinessException("Chưa chọn pallet");
        return palletRepo.findByIdForUpdate(id).orElseThrow(() -> new BusinessException("Không tìm thấy pallet #" + id));
    }
}