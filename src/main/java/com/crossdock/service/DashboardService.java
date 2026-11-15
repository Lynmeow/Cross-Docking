package com.crossdock.service;

import com.crossdock.model.*;
import com.crossdock.repository.DockRepository;
import com.crossdock.repository.PalletRepository;
import com.crossdock.repository.TransferIncidentRepository;
import com.crossdock.repository.TripRepository;
import com.crossdock.util.AppUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** KPI cho Quản lý kho */
@Service
public class DashboardService {

    public static final int DWELL_LIMIT_HOURS = 24;

    private final DockRepository dockRepo;
    private final TripRepository tripRepo;
    private final PalletRepository palletRepo;
    private final TransferIncidentRepository incidentRepo;

    public DashboardService(DockRepository dockRepo, TripRepository tripRepo,
                            PalletRepository palletRepo, TransferIncidentRepository incidentRepo) {
        this.dockRepo = dockRepo;
        this.tripRepo = tripRepo;
        this.palletRepo = palletRepo;
        this.incidentRepo = incidentRepo;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> m = new HashMap<>();
        LocalDateTime now = AppUtils.now();

        long dockTotal = dockRepo.count();
        long dockOccupied = dockRepo.countByStatus(Dock.Status.OCCUPIED);
        m.put("dockTotal", dockTotal);
        m.put("dockAvailable", dockRepo.countByStatus(Dock.Status.AVAILABLE));
        m.put("dockUtilization", dockTotal == 0 ? 0.0 : dockOccupied * 100.0 / dockTotal);

        m.put("tripsAtDock", tripRepo.findByStatusOrderByScheduledArrivalAsc(Trip.Status.AT_DOCK));
        m.put("tripsScheduled", tripRepo.countByStatus(Trip.Status.SCHEDULED));

        List<Trip> arrived = tripRepo.findByActualArrivalIsNotNull();
        long onTime = arrived.stream().filter(t -> !t.isLateArrival()).count();
        m.put("arrivedCount", arrived.size());
        m.put("onTimeRate", arrived.isEmpty() ? 100.0 : onTime * 100.0 / arrived.size());

        List<Pallet> shipped = palletRepo.findByStatusOrderByIdDesc(Pallet.Status.SHIPPED);
        m.put("shippedCount", shipped.size());
        m.put("avgDwell", shipped.stream().mapToDouble(Pallet::getDwellHours).average().orElse(0));

        List<Pallet> inWarehouse = palletRepo.findByStatusNotOrderByReceivedAtAsc(Pallet.Status.SHIPPED);
        m.put("inWarehouseCount", inWarehouse.size());
        m.put("overdue", inWarehouse.stream()
                .filter(p -> p.getReceivedAt().isBefore(now.minusHours(DWELL_LIMIT_HOURS))).toList());
        m.put("dwellLimit", DWELL_LIMIT_HOURS);

        Map<Pallet.Status, Long> byStatus = new LinkedHashMap<>();
        for (Pallet.Status s : Pallet.Status.values()) byStatus.put(s, palletRepo.countByStatus(s));
        m.put("palletsByStatus", byStatus);

        m.put("openIncidents", incidentRepo.countByStatus(TransferIncident.Status.OPEN));
        m.put("blockedDoubleAssign", incidentRepo.countByType(TransferIncident.Type.DOUBLE_ASSIGNMENT_ATTEMPT));
        m.put("recentIncidents", incidentRepo.findTop5ByOrderByIdDesc());
        return m;
    }
}