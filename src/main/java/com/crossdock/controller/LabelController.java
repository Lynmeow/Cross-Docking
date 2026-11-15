package com.crossdock.controller;

import com.crossdock.model.Pallet;
import com.crossdock.model.Trip;
import com.crossdock.service.PalletService;
import com.crossdock.service.TripService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;

/** Trang in tem pallet (mã vạch / QR) – dành cho NV Inbound và Quản lý kho */
@Controller
@RequestMapping("/labels")
public class LabelController {

    private final PalletService palletService;
    private final TripService tripService;

    public LabelController(PalletService palletService, TripService tripService) {
        this.palletService = palletService;
        this.tripService = tripService;
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "all") String mode,
                       @RequestParam(required = false) Long tripId,
                       @RequestParam(required = false) String q,
                       Model model) {
        List<Trip> trips = tripService.findAll();
        List<Trip> inboundTrips = trips.stream()
                .filter(t -> !t.isOutbound() && t.getStatus() != Trip.Status.CANCELLED).toList();
        List<Trip> outboundTrips = trips.stream()
                .filter(t -> t.isOutbound() && t.getStatus() != Trip.Status.CANCELLED).toList();

        List<Pallet> pallets;
        if ("inbound".equals(mode)) {
            if (tripId == null && !inboundTrips.isEmpty()) tripId = inboundTrips.get(0).getId();
            pallets = tripId == null ? List.of() : palletService.fromInbound(tripId);
        } else if ("outbound".equals(mode)) {
            if (tripId == null && !outboundTrips.isEmpty()) tripId = outboundTrips.get(0).getId();
            pallets = tripId == null ? List.of() : palletService.onTrip(tripId);
        } else {
            mode = "all";
            tripId = null;
            pallets = palletService.findAll(null).stream()
                    .filter(p -> p.getStatus() != Pallet.Status.SHIPPED).toList();
        }

        if (q != null && !q.isBlank()) {
            String k = q.trim().toLowerCase(Locale.ROOT);
            pallets = pallets.stream()
                    .filter(p -> contains(p.getCode(), k) || contains(p.getContents(), k) || contains(p.getDestination(), k))
                    .toList();
        }

        model.addAttribute("pallets", pallets);
        model.addAttribute("mode", mode);
        model.addAttribute("tripId", tripId);
        model.addAttribute("q", q);
        model.addAttribute("inboundTrips", inboundTrips);
        model.addAttribute("outboundTrips", outboundTrips);
        return "labels";
    }

    private static boolean contains(String s, String k) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(k);
    }
}