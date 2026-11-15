package com.crossdock.controller;

import com.crossdock.model.AppUser;
import com.crossdock.model.Trip;
import com.crossdock.security.CurrentUser;
import com.crossdock.service.PalletService;
import com.crossdock.service.TripService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/outbound")
public class OutboundController extends BaseController {

    private final TripService tripService;
    private final PalletService palletService;
    private final CurrentUser currentUser;

    public OutboundController(TripService tripService, PalletService palletService, CurrentUser currentUser) {
        this.tripService = tripService;
        this.palletService = palletService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String home(Model model) {
        AppUser me = currentUser.require();
        List<Trip> trips = tripService.activeOutbound();
        Map<Long, Long> counts = new HashMap<>();
        for (Trip t : trips) counts.put(t.getId(), palletService.countOnTrip(t.getId()));

        model.addAttribute("pendingForMe", palletService.handoverPendingFor(me.getId()));
        model.addAttribute("ready", palletService.ready());
        model.addAttribute("trips", trips);
        model.addAttribute("counts", counts);
        return "outbound";
    }

    @PostMapping("/handover/accept")
    public String accept(@RequestParam(required = false) List<Long> palletIds, RedirectAttributes ra) {
        return exec(ra, "/outbound", "Đã xác nhận nhận bàn giao", () -> palletService.acceptHandover(palletIds));
    }

    @PostMapping("/handover/{palletId}/reject")
    public String reject(@PathVariable Long palletId, @RequestParam(required = false) String reason,
                         RedirectAttributes ra) {
        return exec(ra, "/outbound", "Đã từ chối nhận bàn giao", () -> palletService.rejectHandover(palletId, reason));
    }
}