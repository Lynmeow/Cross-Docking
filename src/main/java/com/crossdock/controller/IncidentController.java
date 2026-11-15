package com.crossdock.controller;

import com.crossdock.model.TransferIncident;
import com.crossdock.service.IncidentService;
import com.crossdock.service.PalletService;
import com.crossdock.service.TripService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/incidents")
public class IncidentController extends BaseController {

    private final IncidentService incidentService;
    private final PalletService palletService;
    private final TripService tripService;

    public IncidentController(IncidentService incidentService, PalletService palletService, TripService tripService) {
        this.incidentService = incidentService;
        this.palletService = palletService;
        this.tripService = tripService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("incidents", incidentService.findAll());
        model.addAttribute("types", TransferIncident.Type.values());
        model.addAttribute("pallets", palletService.findAll(null));
        model.addAttribute("trips", tripService.findAll());
        return "incidents";
    }

    @PostMapping
    public String report(@RequestParam TransferIncident.Type type,
                         @RequestParam(required = false) Long palletId,
                         @RequestParam(required = false) Long tripId,
                         @RequestParam(required = false) String description,
                         @RequestParam(defaultValue = "false") boolean hold,
                         @RequestParam(required = false) String back, RedirectAttributes ra) {
        return exec(ra, back(back, "/incidents"), "Đã ghi nhận ngoại lệ",
                () -> incidentService.report(type, palletId, tripId, description, hold));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('MANAGER')")
    public String resolve(@PathVariable Long id, @RequestParam(required = false) String resolution,
                          @RequestParam(required = false) String back, RedirectAttributes ra) {
        return exec(ra, back(back, "/incidents"), "Đã xử lý ngoại lệ #" + id,
                () -> incidentService.resolve(id, resolution));
    }
}