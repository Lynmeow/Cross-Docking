package com.crossdock.model;

import com.crossdock.exception.BusinessException;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ASN – Advance Shipping Notice: danh sách pallet dự kiến trên 1 chuyến nhập */
@Entity
@Table(name = "asns")
public class Asn {

    public enum Status {
        OPEN("Chờ nhận hàng"), RECEIVING("Đang nhận hàng"), CLOSED("Đã chốt");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String asnNumber;

    @Column(nullable = false, length = 150)
    private String supplier;

    @OneToOne(optional = false)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 150)
    private String createdBy;

    private LocalDateTime closedAt;

    @Column(length = 150)
    private String closedBy;

    @OneToMany(mappedBy = "asn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<AsnLine> lines = new ArrayList<>();

    @Version
    private Long version;

    protected Asn() { }

    public Asn(String asnNumber, String supplier, Trip trip, String createdBy, LocalDateTime createdAt) {
        this.asnNumber = asnNumber;
        this.supplier = supplier;
        this.trip = trip;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public AsnLine addLine(String palletCode, String contents, double weightKg, String destination) {
        if (status != Status.OPEN) throw new BusinessException("Chỉ thêm dòng khi ASN chưa bắt đầu nhận");
        boolean dup = lines.stream().anyMatch(l -> l.getPalletCode().equalsIgnoreCase(palletCode));
        if (dup) throw new BusinessException("Mã pallet " + palletCode + " bị lặp trong ASN");
        AsnLine line = new AsnLine(this, lines.size() + 1, palletCode, contents, weightKg, destination);
        lines.add(line);
        return line;
    }

    public void ensureNotClosed() {
        if (status == Status.CLOSED) throw new BusinessException("ASN " + asnNumber + " đã chốt nhận hàng");
    }

    public void startReceiving() {
        if (status == Status.OPEN) status = Status.RECEIVING;
    }

    /** Chốt nhận: mọi dòng chưa nhận => THIẾU. Trả về danh sách dòng thiếu */
    public List<AsnLine> close(String by, LocalDateTime now) {
        ensureNotClosed();
        List<AsnLine> missing = lines.stream().filter(AsnLine::isPending).toList();
        missing.forEach(AsnLine::markMissing);
        status = Status.CLOSED;
        closedBy = by;
        closedAt = now;
        return missing;
    }

    public long getReceivedCount() {
        return lines.stream().filter(l -> l.getStatus() == AsnLine.Status.RECEIVED
                || l.getStatus() == AsnLine.Status.DAMAGED).count();
    }

    public Long getId() { return id; }
    public String getAsnNumber() { return asnNumber; }
    public String getSupplier() { return supplier; }
    public Trip getTrip() { return trip; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public String getClosedBy() { return closedBy; }
    public List<AsnLine> getLines() { return lines; }
}