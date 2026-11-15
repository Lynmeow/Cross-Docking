package com.crossdock.service;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.*;
import com.crossdock.repository.AsnRepository;
import com.crossdock.repository.DockRepository;
import com.crossdock.repository.PalletRepository;
import com.crossdock.repository.TripRepository;
import com.crossdock.util.AppUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Điều phối lịch xe & cửa */
@Service
public class TripService {

    private static final Set<Trip.Status> ACTIVE = EnumSet.of(Trip.Status.SCHEDULED, Trip.Status.AT_DOCK);

    private final TripRepository tripRepo;
    private final DockRepository dockRepo;
    private final PalletRepository palletRepo;
    private final AsnRepository asnRepo;
    private final CustodyService custodyService;
    private final IncidentService incidentService;
    private final AccessPolicy policy;
    private final AuditService audit;

    public TripService(TripRepository tripRepo, DockRepository dockRepo, PalletRepository palletRepo,
                       AsnRepository asnRepo, CustodyService custodyService, IncidentService incidentService,
                       AccessPolicy policy, AuditService audit) {
        this.tripRepo = tripRepo;
        this.dockRepo = dockRepo;
        this.palletRepo = palletRepo;
        this.asnRepo = asnRepo;
        this.custodyService = custodyService;
        this.incidentService = incidentService;
        this.policy = policy;
        this.audit = audit;
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public List<Trip> findAll() {
        return tripRepo.findAllByOrderByScheduledArrivalDesc();
    }

    @Transactional(readOnly = true)
    public Trip get(Long id) {
        return tripRepo.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy chuyến #" + id));
    }

    @Transactional(readOnly = true)
    public List<Trip> activeOutbound() {
        return tripRepo.findByDirectionAndStatusInOrderByScheduledArrivalAsc(Dock.Type.OUTBOUND, ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Trip> activeInbound() {
        return tripRepo.findByDirectionAndStatusInOrderByScheduledArrivalAsc(Dock.Type.INBOUND, ACTIVE);
    }

    // ---------- Nghiệp vụ ----------

    @Transactional
    public Trip schedule(String code, String truckPlate, String carrier, String driverName, Dock.Type direction,
                         Long dockId, LocalDateTime arrival, LocalDateTime departure, String destination, int capacity) {
        policy.requireAny(Role.MANAGER);
        String c = AppUtils.requireText(code, "Mã chuyến").toUpperCase();
        if (tripRepo.existsByCode(c)) throw new BusinessException("Mã chuyến " + c + " đã tồn tại");
        if (direction == null || dockId == null || arrival == null || departure == null) {
            throw new BusinessException("Thiếu thông tin hướng / cửa / thời gian");
        }
        Dock dock = lockDock(dockId);
        ensureNoConflict(dock, arrival, departure, null);
        Trip trip = tripRepo.save(new Trip(c, AppUtils.requireText(truckPlate, "Biển số xe").toUpperCase(),
                AppUtils.requireText(carrier, "Nhà vận chuyển"), driverName, direction, dock,
                arrival, departure, destination, capacity));
        audit.log(AuditLog.Category.TRIP, "CREATE_TRIP", c, "Chuyến " + direction.getLabel() + ": " + describe(trip));
        return trip;
    }

    @Transactional
    public void reschedule(Long id, String truckPlate, String carrier, String driverName, Long dockId,
                           LocalDateTime arrival, LocalDateTime departure, String destination, int capacity) {
        policy.requireAny(Role.MANAGER);
        Trip trip = get(id);
        if (dockId == null || arrival == null || departure == null) {
            throw new BusinessException("Thiếu thông tin cửa / thời gian");
        }
        String before = describe(trip);
        long assigned = trip.isOutbound() ? palletRepo.countByOutboundTripId(id) : 0;
        if (assigned > 0 && capacity < assigned) {
            throw new BusinessException("Sức chứa mới nhỏ hơn số pallet đã gán (" + assigned + ")");
        }
        if (assigned > 0 && destination != null && !destination.trim().equalsIgnoreCase(trip.getDestination())) {
            throw new BusinessException("Không đổi điểm đến khi chuyến đã có pallet được gán");
        }
        Dock dock = lockDock(dockId);
        ensureNoConflict(dock, arrival, departure, trip.getId());
        trip.reschedule(AppUtils.requireText(truckPlate, "Biển số xe").toUpperCase(),
                AppUtils.requireText(carrier, "Nhà vận chuyển"), driverName, dock,
                arrival, departure, destination, capacity);
        String after = describe(trip);
        if (before.equals(after)) throw new BusinessException("Không có thay đổi nào");
        audit.log(AuditLog.Category.TRIP, "UPDATE_TRIP", trip.getCode(), "TRƯỚC: " + before + " | SAU: " + after);
    }

    @Transactional
    public void arrive(Long id) {
        Trip trip = get(id);
        AppUser actor = policy.requireForTrip(trip);
        trip.arrive(AppUtils.now());
        if (trip.isLateArrival()) {
            long late = Duration.between(trip.getScheduledArrival(), trip.getActualArrival()).toMinutes();
            incidentService.record(TransferIncident.Type.LATE_TRUCK, null, trip,
                    "Xe " + trip.getTruckPlate() + " đến trễ " + late + " phút so với lịch", false, actor);
        }
        audit.log(AuditLog.Category.TRIP, "ARRIVE", trip.getCode(),
                "Xe " + trip.getTruckPlate() + " vào cửa " + trip.getDock().getCode());
    }

    @Transactional
    public void depart(Long id, String sealNumber) {
        Trip trip = get(id);
        AppUser actor = policy.requireForTrip(trip);
        if (trip.getStatus() != Trip.Status.AT_DOCK) {
            throw new BusinessException("Chuyến " + trip.getCode() + " chưa vào cửa");
        }
        LocalDateTime now = AppUtils.now();
        if (trip.isOutbound()) {
            List<Pallet> pallets = palletRepo.findByOutboundTripIdOrderByCode(id);
            List<String> notLoaded = pallets.stream()
                    .filter(p -> p.getStatus() != Pallet.Status.LOADED).map(Pallet::getCode).toList();
            if (!notLoaded.isEmpty()) {
                throw new BusinessException("Không thể cho xe rời: còn pallet chưa lên xe " + notLoaded
                        + ". Hãy xếp lên xe hoặc nhờ quản lý gỡ khỏi chuyến.");
            }
            String seal = AppUtils.requireText(sealNumber, "Số seal").toUpperCase();
            for (Pallet p : pallets) {
                p.ship(now);
                custodyService.record(p, CustodyEvent.Type.DEPARTED, null, trip.getCustodianName(),
                        "Trên xe " + trip.getTruckPlate() + " → " + trip.getDestination(), actor, "Seal " + seal);
            }
            trip.depart(now, seal);
            audit.log(AuditLog.Category.TRIP, "DEPART", trip.getCode(),
                    "Xe xuất rời kho, seal " + seal + ", " + pallets.size() + " pallet");
        } else {
            asnRepo.findByTripId(id).ifPresent(asn -> {
                if (asn.getStatus() != Asn.Status.CLOSED) {
                    throw new BusinessException("Phải chốt nhận hàng ASN " + asn.getAsnNumber() + " trước khi cho xe nhập rời");
                }
            });
            trip.depart(now, null);
            audit.log(AuditLog.Category.TRIP, "DEPART", trip.getCode(), "Xe nhập rời cửa " + trip.getDock().getCode());
        }
    }

    @Transactional
    public void cancel(Long id, String reason) {
        AppUser actor = policy.requireAny(Role.MANAGER);
        String r = AppUtils.requireText(reason, "Lý do hủy");
        Trip trip = get(id);
        trip.cancel();
        for (Pallet p : palletRepo.findByOutboundTripIdOrderByCode(id)) {
            p.unassign();
            custodyService.record(p, CustodyEvent.Type.UNASSIGNED, p.getCustodianUser(), p.getCurrentCustodian(),
                    p.getCurrentLocation(), actor, "Chuyến " + trip.getCode() + " bị hủy: " + r);
        }
        audit.log(AuditLog.Category.TRIP, "CANCEL_TRIP", trip.getCode(), "Lý do: " + r);
    }

    public void logPrint(Trip trip) {
        audit.log(AuditLog.Category.TRIP, "PRINT_DOCUMENT", trip.getCode(),
                trip.isOutbound() ? "In biên bản giao hàng xuất kho" : "In biên bản nhận hàng");
    }

    // ---------- Hỗ trợ ----------

    private Dock lockDock(Long dockId) {
        return dockRepo.findByIdForUpdate(dockId).orElseThrow(() -> new BusinessException("Không tìm thấy cửa #" + dockId));
    }

    private void ensureNoConflict(Dock dock, LocalDateTime arrival, LocalDateTime departure, Long excludeTripId) {
        List<Trip> conflicts = tripRepo.findOverlapping(dock.getId(), arrival, departure, ACTIVE).stream()
                .filter(t -> !Objects.equals(t.getId(), excludeTripId)).toList();
        if (!conflicts.isEmpty()) {
            Trip other = conflicts.get(0);
            throw new BusinessException("Xung đột lịch: cửa " + dock.getCode() + " đã được đặt cho chuyến "
                    + other.getCode() + " (" + AppUtils.fmt(other.getScheduledArrival()) + " – "
                    + AppUtils.fmt(other.getScheduledDeparture()) + ")");
        }
    }

    private String describe(Trip t) {
        return "xe " + t.getTruckPlate() + ", nhà xe " + t.getCarrier() + ", tài xế " + AppUtils.nz(t.getDriverName())
                + ", cửa " + t.getDock().getCode() + ", " + AppUtils.fmt(t.getScheduledArrival()) + " → "
                + AppUtils.fmt(t.getScheduledDeparture()) + ", điểm " + AppUtils.nz(t.getDestination())
                + ", sức chứa " + t.getCapacity();
    }

        public void logSchedulePrint(LocalDate date, int count) {
        audit.log(AuditLog.Category.TRIP, "PRINT_SCHEDULE", date.toString(),
                "In lịch xe ngày " + date.getDayOfMonth() + "/" + date.getMonthValue() + "/" + date.getYear()
                        + " (" + count + " chuyến)");
    }
}