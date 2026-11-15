package com.crossdock.controller;

import com.crossdock.model.Dock;
import com.crossdock.service.DockService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/docks")
public class DockController extends BaseController {

    private final DockService dockService;

    public DockController(DockService dockService) {
        this.dockService = dockService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("docks", dockService.findAll());
        model.addAttribute("types", Dock.Type.values());
        return "docks";
    }

    @PostMapping
    public String create(@RequestParam String code, @RequestParam Dock.Type type, RedirectAttributes ra) {
        return exec(ra, "/docks", "Đã thêm cửa " + code.trim().toUpperCase(), () -> dockService.create(code, type));
    }

    @PostMapping("/{id}/maintenance")
    public String toggleMaintenance(@PathVariable Long id, RedirectAttributes ra) {
        return exec(ra, "/docks", "Đã cập nhật trạng thái cửa", () -> dockService.toggleMaintenance(id));
    }
}