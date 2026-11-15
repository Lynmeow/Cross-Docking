package com.crossdock.controller;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.Pallet;
import com.crossdock.model.TransferIncident;
import com.crossdock.service.CustodyService;
import com.crossdock.service.IncidentService;
import com.crossdock.service.PalletService;
import com.crossdock.service.TripService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.Optional;

@Controller
@RequestMapping("/pallets")
public class PalletController extends BaseController {

    private final PalletService palletService;
    private final TripService tripService;
    private final CustodyService custodyService;
    private final IncidentService incidentService;

    public PalletController(PalletService palletService, TripService tripService,
                            CustodyService custodyService, IncidentService incidentService) {
        this.palletService = palletService;
        this.tripService = tripService;
        this.custodyService = custodyService;
        this.incidentService = incidentService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Pallet.Status status, Model model) {
        model.addAttribute("pallets", palletService.findAll(status));
        model.addAttribute("statuses", Pallet.Status.values());
        model.addAttribute("selectedStatus", status);
        return "pallets";
    }

    /** Quét mã vạch / QR (hoặc gõ tay) => mở thẳng trang chi tiết pallet */
    @GetMapping("/scan")
    public String scan(@RequestParam(required = false) String code,
                       @RequestHeader(value = "Referer", required = false) String referer,
                       RedirectAttributes ra) {
        Optional<Pallet> found = palletService.findByCode(code);
        if (found.isPresent()) {
            return "redirect:/pallets/" + found.get().getId();
        }
        ra.addFlashAttribute("error", "Không tìm thấy pallet có mã: " + (code == null ? "" : code.trim()));
        return "redirect:" + refererPath(referer);
    }

    private String refererPath(String referer) {
        try {
            if (referer != null) {
                String path = URI.create(referer).getPath();
                if (path != null && path.startsWith("/") && !path.startsWith("//")) return path;
            }
        } catch (IllegalArgumentException ignored) { }
        return "/pallets";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("pallet", palletService.get(id));
        model.addAttribute("events", custodyService.history(id));
        model.addAttribute("chain", custodyService.verify(id));
        model.addAttribute("outboundTrips", tripService.activeOutbound());
        model.addAttribute("incidents", incidentService.findByPallet(id));
        model.addAttribute("incidentTypes", TransferIncident.Type.values());
        return "pallet-detail";
    }

    @PostMapping("/{id}/stage")
    public String stage(@PathVariable Long id, @RequestParam(required = false) String zone,
                        @RequestParam(required = false) String back, RedirectAttributes ra) {
        return exec(ra, back(back, "/pallets/" + id), "Đã đưa pallet vào khu tập kết",
                () -> palletService.stage(id, zone));
    }

    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam(required = false) Long tripId,
                         @RequestParam(required = false) String back, RedirectAttributes ra) {
        return exec(ra, back(back, "/pallets/" + id), "Đã gán pallet vào chuyến", () -> {
            if (tripId == null) throw new BusinessException("Chưa chọn chuyến xuất");
            palletService.assign(id, tripId);
        });
    }

    @PostMapping("/{id}/load")
    public String load(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes ra) {
        return exec(ra, back(back, "/pallets/" + id), "Đã xếp pallet lên xe", () -> palletService.load(id));
    }

    @PostMapping("/{id}/unassign")
    public String unassign(@PathVariable Long id, @RequestParam(required = false) String reason,
                           @RequestParam(required = false) String back, RedirectAttributes ra) {
        return exec(ra, back(back, "/pallets/" + id), "Đã gỡ pallet khỏi chuyến",
                () -> palletService.unassign(id, reason));
    }

    @PostMapping("/{id}/reassign")
    public String reassign(@PathVariable Long id, @RequestParam(required = false) Long tripId,
                           @RequestParam(required = false) String reason, RedirectAttributes ra) {
        return exec(ra, "/pallets/" + id, "Đã chuyển pallet sang chuyến mới",
                () -> palletService.reassign(id, tripId, reason));
    }
}