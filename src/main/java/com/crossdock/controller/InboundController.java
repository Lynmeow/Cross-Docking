package com.crossdock.controller;

import com.crossdock.model.Asn;
import com.crossdock.model.Pallet;
import com.crossdock.model.Role;
import com.crossdock.model.Trip;
import com.crossdock.service.AsnService;
import com.crossdock.service.PalletService;
import com.crossdock.service.TripService;
import com.crossdock.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/inbound")
public class InboundController extends BaseController {

    private final TripService tripService;
    private final PalletService palletService;
    private final AsnService asnService;
    private final UserService userService;

    public InboundController(TripService tripService, PalletService palletService,
                             AsnService asnService, UserService userService) {
        this.tripService = tripService;
        this.palletService = palletService;
        this.asnService = asnService;
        this.userService = userService;
    }

    @GetMapping
    public String home(Model model) {
        List<Trip> trips = tripService.activeInbound();
        Map<Long, Asn> asns = new HashMap<>();
        for (Trip t : trips) {
            asnService.findByTrip(t.getId()).ifPresent(a -> asns.put(t.getId(), a));
        }
        model.addAttribute("trips", trips);
        model.addAttribute("asns", asns);
        model.addAttribute("received", palletService.findAll(Pallet.Status.RECEIVED));
        model.addAttribute("staged", palletService.findAll(Pallet.Status.STAGED));
        model.addAttribute("pending", palletService.findAll(Pallet.Status.HANDOVER_PENDING));
        model.addAttribute("outboundStaff", userService.activeByRole(Role.OUTBOUND));
        return "inbound";
    }

    @PostMapping("/handover")
    public String handover(@RequestParam(required = false) List<Long> palletIds,
                           @RequestParam(required = false) Long recipientId, RedirectAttributes ra) {
        return exec(ra, "/inbound", "Đã gửi yêu cầu bàn giao – chờ Outbound xác nhận",
                () -> palletService.requestHandover(palletIds, recipientId));
    }
}