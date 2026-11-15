package com.crossdock.service;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.*;
import com.crossdock.repository.AsnLineRepository;
import com.crossdock.repository.AsnRepository;
import com.crossdock.repository.TripRepository;
import com.crossdock.util.AppUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** ASN & nhận hàng đối chiếu ASN */
@Service
public class AsnService {

    private static final double WEIGHT_TOLERANCE = 0.10; // lệch >10% => ngoại lệ

    private final AsnRepository asnRepo;
    private final AsnLineRepository lineRepo;
    private final TripRepository tripRepo;
    private final PalletService palletService;
    private final IncidentService incidentService;
    private final AccessPolicy policy;
    private final AuditService audit;

    public AsnService(AsnRepository asnRepo, AsnLineRepository lineRepo, TripRepository tripRepo,
                      PalletService palletService, IncidentService incidentService,
                      AccessPolicy policy, AuditService audit) {
        this.asnRepo = asnRepo;
        this.lineRepo = lineRepo;
        this.tripRepo = tripRepo;
        this.palletService = palletService;
        this.incidentService = incidentService;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Optional<Asn> findByTrip(Long tripId) {
        return asnRepo.findByTripId(tripId);
    }

    /** linesText: mỗi dòng "MÃ;Nội dung;Kg;Điểm đến" */
    @Transactional
    public Asn create(Long tripId, String asnNumber, String supplier, String linesText) {
        AppUser actor = policy.requireAny(Role.MANAGER);
        Trip trip = tripRepo.findById(tripId).orElseThrow(() -> new BusinessException("Không tìm thấy chuyến #" + tripId));
        if (trip.isOutbound()) throw new BusinessException("ASN chỉ dành cho chuyến nhập");
        if (trip.getStatus() == Trip.Status.DEPARTED || trip.getStatus() == Trip.Status.CANCELLED) {
            throw new BusinessException("Chuyến đã rời kho hoặc đã hủy");
        }
        if (asnRepo.existsByTripId(tripId)) throw new BusinessException("Chuyến " + trip.getCode() + " đã có ASN");
        String no = AppUtils.requireText(asnNumber, "Số ASN").toUpperCase();
        if (asnRepo.existsByAsnNumber(no)) throw new BusinessException("Số ASN " + no + " đã tồn tại");

        Asn asn = new Asn(no, AppUtils.requireText(supplier, "Nhà cung cấp"), trip, actor.getDisplayName(), AppUtils.now());
        String[] rows = AppUtils.requireText(linesText, "Danh sách pallet").split("\\R");
        for (int i = 0; i < rows.length; i++) {
            String raw = rows[i];
            if (raw.isBlank()) continue;
            String[] f = raw.split(";");
            if (f.length < 4) {
                throw new BusinessException("Dòng " + (i + 1) + " sai định dạng. Đúng: MÃ;Nội dung;Kg;Điểm đến");
            }
            double kg;
            try {
                kg = Double.parseDouble(f[2].trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new BusinessException("Dòng " + (i + 1) + ": khối lượng không hợp lệ");
            }
            if (kg <= 0) throw new BusinessException("Dòng " + (i + 1) + ": khối lượng phải > 0");
            asn.addLine(AppUtils.requireText(f[0], "Mã pallet").toUpperCase(),
                    AppUtils.requireText(f[1], "Nội dung"), kg, AppUtils.requireText(f[3], "Điểm đến"));
        }
        if (asn.getLines().isEmpty()) throw new BusinessException("ASN phải có ít nhất 1 pallet");
        Asn saved = asnRepo.save(asn);
        audit.log(AuditLog.Category.ASN, "CREATE_ASN", no, "ASN cho chuyến " + trip.getCode() + ", NCC "
                + saved.getSupplier() + ", " + saved.getLines().size() + " pallet dự kiến");
        return saved;
    }

    @Transactional
    public Pallet receiveLine(Long tripId, Long lineId, Double actualWeightKg, boolean damaged, String note) {
        AppUser actor = policy.requireAny(Role.INBOUND);
        AsnLine line = lineRepo.findById(lineId).orElseThrow(() -> new BusinessException("Không tìm thấy dòng ASN"));
        Asn asn = line.getAsn();
        if (!asn.getTrip().getId().equals(tripId)) throw new BusinessException("Dòng ASN không thuộc chuyến này");
        asn.ensureNotClosed();
        if (!line.isPending()) throw new BusinessException("Pallet " + line.getPalletCode() + " đã được xử lý");

        double weight = actualWeightKg != null ? actualWeightKg : line.getExpectedWeightKg();
        Pallet p = palletService.createReceived(asn.getTrip(), line.getPalletCode(), line.getContents(), weight,
                line.getDestination(), actor, "Theo ASN " + asn.getAsnNumber());
        line.markReceived(p, weight, damaged);
        asn.startReceiving();

        if (damaged) {
            incidentService.record(TransferIncident.Type.DAMAGED, p, asn.getTrip(),
                    "Hư hỏng khi nhận: " + (note == null || note.isBlank() ? "không ghi chú" : note.trim()), true, actor);
        }
        double diff = Math.abs(weight - line.getExpectedWeightKg()) / line.getExpectedWeightKg();
        if (diff > WEIGHT_TOLERANCE) {
            incidentService.record(TransferIncident.Type.WEIGHT_MISMATCH, p, asn.getTrip(),
                    "Pallet " + p.getCode() + " cân " + weight + " kg, ASN ghi " + line.getExpectedWeightKg() + " kg", false, actor);
        }
        audit.log(AuditLog.Category.ASN, "RECEIVE", p.getCode(),
                "Nhận theo ASN " + asn.getAsnNumber() + ", " + weight + " kg" + (damaged ? ", HƯ HỎNG" : ""));
        return p;
    }

    @Transactional
    public Pallet receiveExtra(Long tripId, String code, String contents, double weightKg, String destination) {
        AppUser actor = policy.requireAny(Role.INBOUND);
        Trip trip = tripRepo.findById(tripId).orElseThrow(() -> new BusinessException("Không tìm thấy chuyến #" + tripId));
        String c = AppUtils.requireText(code, "Mã pallet").toUpperCase();
        Optional<Asn> asn = asnRepo.findByTripId(tripId);
        if (asn.isPresent()) {
            asn.get().ensureNotClosed();
            if (asn.get().getLines().stream().anyMatch(l -> l.getPalletCode().equalsIgnoreCase(c))) {
                throw new BusinessException("Pallet " + c + " có trong ASN – hãy nhận theo dòng ASN");
            }
        }
        Pallet p = palletService.createReceived(trip, c, contents, weightKg, destination, actor,
                asn.isPresent() ? "Ngoài ASN" : "Chuyến không có ASN");
        if (asn.isPresent()) {
            asn.get().startReceiving();
            incidentService.record(TransferIncident.Type.EXTRA_PALLET, p, trip,
                    "Pallet " + c + " không có trong ASN " + asn.get().getAsnNumber(), true, actor);
        }
        audit.log(AuditLog.Category.ASN, "RECEIVE_EXTRA", c, "Nhận pallet " + (asn.isPresent() ? "ngoài ASN" : "không ASN")
                + " từ chuyến " + trip.getCode());
        return p;
    }

    /** Chốt nhận hàng: các dòng chưa nhận => ngoại lệ THIẾU. Trả về số pallet thiếu */
    @Transactional
    public int close(Long tripId) {
        AppUser actor = policy.requireAny(Role.INBOUND);
        Asn asn = asnRepo.findByTripId(tripId).orElseThrow(() -> new BusinessException("Chuyến chưa có ASN"));
        List<AsnLine> missing = asn.close(actor.getDisplayName(), AppUtils.now());
        for (AsnLine l : missing) {
            incidentService.record(TransferIncident.Type.MISSING, null, asn.getTrip(),
                    "Thiếu pallet " + l.getPalletCode() + " (" + l.getContents() + ") so với ASN " + asn.getAsnNumber(),
                    false, actor);
        }
        audit.log(AuditLog.Category.ASN, "CLOSE_ASN", asn.getAsnNumber(), "Chốt nhận " + asn.getReceivedCount() + "/"
                + asn.getLines().size() + " pallet, thiếu " + missing.size());
        return missing.size();
    }
}