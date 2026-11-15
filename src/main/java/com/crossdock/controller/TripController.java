package com.crossdock.controller;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.Dock;
import com.crossdock.model.Pallet;
import com.crossdock.model.Trip;
import com.crossdock.service.*;
import com.crossdock.util.AppUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/trips")
public class TripController extends BaseController {

    private final TripService tripService;
    private final DockService dockService;
    private final PalletService palletService;
    private final AsnService asnService;
    private final IncidentService incidentService;

    public TripController(TripService tripService, DockService dockService, PalletService palletService,
                          AsnService asnService, IncidentService incidentService) {
        this.tripService = tripService;
        this.dockService = dockService;
        this.palletService = palletService;
        this.asnService = asnService;
        this.incidentService = incidentService;
    }

    // ---------- Quản lý kho: lịch xe ----------

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public String list(Model model) {
        model.addAttribute("trips", tripService.findAll());
        model.addAttribute("docks", dockService.findAll());
        model.addAttribute("directions", Dock.Type.values());
        return "trips";
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public String create(@RequestParam String code, @RequestParam String truckPlate, @RequestParam String carrier,
                         @RequestParam(required = false) String driverName,
                         @RequestParam Dock.Type direction, @RequestParam Long dockId,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrival,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime departure,
                         @RequestParam(required = false) String destination,
                         @RequestParam(defaultValue = "20") int capacity, RedirectAttributes ra) {
        return exec(ra, "/trips", "Đã lên lịch chuyến " + code.trim().toUpperCase(),
                () -> tripService.schedule(code, truckPlate, carrier, driverName, direction, dockId,
                        arrival, departure, destination, capacity));
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasRole('MANAGER')")
    public String edit(@PathVariable Long id, @RequestParam String truckPlate, @RequestParam String carrier,
                       @RequestParam(required = false) String driverName, @RequestParam Long dockId,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrival,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime departure,
                       @RequestParam(required = false) String destination,
                       @RequestParam(defaultValue = "20") int capacity, RedirectAttributes ra) {
        return exec(ra, "/trips/" + id, "Đã cập nhật lịch chuyến",
                () -> tripService.reschedule(id, truckPlate, carrier, driverName, dockId,
                        arrival, departure, destination, capacity));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('MANAGER')")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        return exec(ra, "/trips/" + id, "Đã hủy chuyến", () -> tripService.cancel(id, reason));
    }

    // ---------- Chi tiết & điều phối ----------

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Trip trip = tripService.get(id);
        model.addAttribute("trip", trip);
        model.addAttribute("docks", dockService.findAll());
        model.addAttribute("incidents", incidentService.findByTrip(id));
        if (trip.isOutbound()) {
            model.addAttribute("pallets", palletService.onTrip(id));
            model.addAttribute("available", palletService.readyFor(trip));
        } else {
            model.addAttribute("pallets", palletService.fromInbound(id));
            model.addAttribute("asn", asnService.findByTrip(id).orElse(null));
        }
        return "trip-detail";
    }

    @PostMapping("/{id}/arrive")
    public String arrive(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes ra) {
        return exec(ra, back(back, "/trips/" + id), "Xe đã vào cửa", () -> tripService.arrive(id));
    }

    @PostMapping("/{id}/depart")
    public String depart(@PathVariable Long id, @RequestParam(required = false) String sealNumber,
                         RedirectAttributes ra) {
        return exec(ra, "/trips/" + id, "Xe đã rời kho", () -> tripService.depart(id, sealNumber));
    }

    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam(required = false) Long palletId, RedirectAttributes ra) {
        return exec(ra, "/trips/" + id, "Đã gán pallet vào chuyến", () -> {
            if (palletId == null) throw new BusinessException("Chưa chọn pallet");
            palletService.assign(palletId, id);
        });
    }

    // ---------- ASN / nhận hàng ----------

    @PostMapping("/{id}/asn")
    @PreAuthorize("hasRole('MANAGER')")
    public String createAsn(@PathVariable Long id, @RequestParam String asnNumber, @RequestParam String supplier,
                            @RequestParam String linesText, RedirectAttributes ra) {
        return exec(ra, "/trips/" + id, "Đã tạo ASN", () -> asnService.create(id, asnNumber, supplier, linesText));
    }

    @PostMapping("/{id}/asn/lines/{lineId}/receive")
    public String receiveLine(@PathVariable Long id, @PathVariable Long lineId,
                              @RequestParam(required = false) Double actualWeightKg,
                              @RequestParam(defaultValue = "false") boolean damaged,
                              @RequestParam(required = false) String note, RedirectAttributes ra) {
        return exec(ra, "/trips/" + id, "Đã nhận pallet",
                () -> asnService.receiveLine(id, lineId, actualWeightKg, damaged, note));
    }

    @PostMapping("/{id}/receive-extra")
    public String receiveExtra(@PathVariable Long id, @RequestParam String code, @RequestParam String contents,
                               @RequestParam double weightKg, @RequestParam String destination, RedirectAttributes ra) {
        return exec(ra, "/trips/" + id, "Đã nhận pallet " + code.trim().toUpperCase(),
                () -> asnService.receiveExtra(id, code, contents, weightKg, destination));
    }

    @PostMapping("/{id}/asn/close")
    public String closeAsn(@PathVariable Long id, RedirectAttributes ra) {
        return exec(ra, "/trips/" + id, "Đã chốt nhận hàng ASN", () -> asnService.close(id));
    }

    // ---------- Biên bản in ----------

    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        Trip trip = tripService.get(id);
        List<Pallet> pallets = trip.isOutbound() ? palletService.onTrip(id) : palletService.fromInbound(id);
        model.addAttribute("trip", trip);
        model.addAttribute("pallets", pallets);
        model.addAttribute("totalWeight", pallets.stream().mapToDouble(Pallet::getWeightKg).sum());
        model.addAttribute("asn", trip.isOutbound() ? null : asnService.findByTrip(id).orElse(null));
        model.addAttribute("incidents", incidentService.findByTrip(id));
        model.addAttribute("printedAt", AppUtils.now());
        tripService.logPrint(trip);
        return "trip-print";
    }

        // ---------- In lịch xe theo ngày ----------

    @GetMapping("/schedule-print")
    @PreAuthorize("hasRole('MANAGER')")
    public String printSchedule(@RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                Model model) {
        LocalDate day = date != null ? date : LocalDate.now();
        List<Trip> trips = tripService.findAll().stream()
                .filter(t -> t.getScheduledArrival().toLocalDate().equals(day))
                .filter(t -> t.getStatus() != Trip.Status.CANCELLED)
                .sorted(Comparator.comparing(Trip::getScheduledArrival))
                .toList();
        model.addAttribute("trips", trips);
        model.addAttribute("date", day);
        model.addAttribute("printedAt", AppUtils.now());
        tripService.logSchedulePrint(day, trips.size());
        return "trip-schedule-print";
    }
}