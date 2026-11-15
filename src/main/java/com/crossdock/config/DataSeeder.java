package com.crossdock.config;

import com.crossdock.model.*;
import com.crossdock.repository.AppUserRepository;
import com.crossdock.repository.DockRepository;
import com.crossdock.service.*;
import com.crossdock.util.AppUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final AppUserRepository userRepo;
    private final DockRepository dockRepo;
    private final PasswordEncoder encoder;
    private final DockService dockService;
    private final TripService tripService;
    private final AsnService asnService;
    private final PalletService palletService;

    public DataSeeder(AppUserRepository userRepo, DockRepository dockRepo, PasswordEncoder encoder,
                      DockService dockService, TripService tripService, AsnService asnService,
                      PalletService palletService) {
        this.userRepo = userRepo;
        this.dockRepo = dockRepo;
        this.encoder = encoder;
        this.dockService = dockService;
        this.tripService = tripService;
        this.asnService = asnService;
        this.palletService = palletService;
    }

    @Override
    public void run(String... args) {
        if (userRepo.count() == 0) seedUsers();
        if (dockRepo.count() == 0) seedOperations();
    }

    private void seedUsers() {
        LocalDateTime now = AppUtils.now();
        // mustChangePassword = false để demo nhanh; tài khoản do admin tạo sau sẽ bắt buộc đổi
        userRepo.save(new AppUser("admin", encoder.encode("Admin@123"), "Quản trị viên", Role.ADMIN, false, now));
        userRepo.save(new AppUser("quanly", encoder.encode("Quanly@123"), "Trần Quản Lý", Role.MANAGER, false, now));
        userRepo.save(new AppUser("inbound1", encoder.encode("Inbound@123"), "Nguyễn Văn Nhập", Role.INBOUND, false, now));
        userRepo.save(new AppUser("outbound1", encoder.encode("Outbound@123"), "Lê Thị Xuất", Role.OUTBOUND, false, now));
        userRepo.save(new AppUser("outbound2", encoder.encode("Outbound@123"), "Phạm Văn Giao", Role.OUTBOUND, false, now));
    }

    private void seedOperations() {
        LocalDateTime now = AppUtils.now().withSecond(0);
        Trip[] t = new Trip[3];
        Asn[] asn = new Asn[1];

        runAs("quanly", () -> {
            Dock in1 = dockService.create("IN-01", Dock.Type.INBOUND);
            Dock in2 = dockService.create("IN-02", Dock.Type.INBOUND);
            Dock out1 = dockService.create("OUT-01", Dock.Type.OUTBOUND);
            Dock out2 = dockService.create("OUT-02", Dock.Type.OUTBOUND);
            dockService.create("OUT-03", Dock.Type.OUTBOUND);

            t[0] = tripService.schedule("IN-001", "51C-123.45", "Vận tải Phương Nam", "Võ Văn Tài",
                    Dock.Type.INBOUND, in1.getId(), now.minusMinutes(10), now.plusHours(2), "TP.HCM", 40);
            Trip in002 = tripService.schedule("IN-002", "60A-678.90", "Logistics Đồng Nai", "Đỗ Minh Xế",
                    Dock.Type.INBOUND, in2.getId(), now.plusHours(3), now.plusHours(5), "Đồng Nai", 40);
            t[1] = tripService.schedule("OUT-HN-001", "29H-111.22", "Bắc Nam Express", "Hoàng Văn Lái",
                    Dock.Type.OUTBOUND, out1.getId(), now.plusHours(1), now.plusHours(4), "Hà Nội", 20);
            t[2] = tripService.schedule("OUT-DN-001", "43C-333.44", "Miền Trung Cargo", "Ngô Thanh Bánh",
                    Dock.Type.OUTBOUND, out2.getId(), now.plusHours(2), now.plusHours(5), "Đà Nẵng", 10);

            asn[0] = asnService.create(t[0].getId(), "ASN-0001", "Công ty TNHH Điện tử ABC", """
                    PL-0001;Điện thoại di động;320;Hà Nội
                    PL-0002;Linh kiện máy tính;410;Hà Nội
                    PL-0003;Thực phẩm khô;500;Đà Nẵng
                    PL-0004;Mỹ phẩm;210;Đà Nẵng
                    PL-0005;Đồ gia dụng;380;Hà Nội
                    """);
            asnService.create(in002.getId(), "ASN-0002", "Nhà máy May Đồng Nai", """
                    PL-0101;Quần áo;250;Hà Nội
                    PL-0102;Giày dép;300;Đà Nẵng
                    """);
        });

        Long[] p = new Long[3];
        runAs("inbound1", () -> {
            tripService.arrive(t[0].getId());
            List<AsnLine> lines = asn[0].getLines();
            for (int i = 0; i < 3; i++) {
                p[i] = asnService.receiveLine(t[0].getId(), lines.get(i).getId(), null, false, null).getId();
            }
            palletService.stage(p[0], "A1");
            palletService.stage(p[1], "A1");
            palletService.stage(p[2], "B2");
            Long out1 = userRepo.findByUsername("outbound1").orElseThrow().getId();
            palletService.requestHandover(List.of(p[0], p[1]), out1);
        });

        runAs("outbound1", () -> {
            palletService.acceptHandover(List.of(p[0], p[1]));
            palletService.assign(p[0], t[1].getId());
        });
    }

    private void runAs(String username, Runnable action) {
        AppUser u = userRepo.findByUsername(username).orElseThrow();
        var auth = new UsernamePasswordAuthenticationToken(username, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}