package com.crossdock.model;

import com.crossdock.exception.BusinessException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Ngoại lệ chuyển tải */
@Entity
@Table(name = "transfer_incidents")
public class TransferIncident {

    public enum Type {
        DAMAGED("Hàng hư hỏng"), MISSING("Thiếu pallet so với ASN"), EXTRA_PALLET("Pallet ngoài ASN"),
        WEIGHT_MISMATCH("Lệch khối lượng so với ASN"), WRONG_DESTINATION("Sai điểm đến"),
        DOUBLE_ASSIGNMENT_ATTEMPT("Cố gán pallet cho 2 chuyến"), OVER_CAPACITY("Vượt sức chứa chuyến"),
        LATE_TRUCK("Xe đến trễ"), HANDOVER_REJECTED("Từ chối nhận bàn giao"), OTHER("Khác");
        private final String label;
        Type(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Status {
        OPEN("Đang mở"), RESOLVED("Đã xử lý");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @ManyToOne
    @JoinColumn(name = "pallet_id")
    private Pallet pallet;

    @ManyToOne
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(length = 1000)
    private String description;

    private boolean holdsPallet;

    @Column(length = 150)
    private String reportedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @Column(length = 150)
    private String resolvedBy;

    @Column(length = 1000)
    private String resolution;

    protected TransferIncident() { }

    public TransferIncident(Type type, Pallet pallet, Trip trip, String description,
                            boolean holdsPallet, String reportedBy, LocalDateTime createdAt) {
        this.type = type;
        this.pallet = pallet;
        this.trip = trip;
        this.description = description;
        this.holdsPallet = holdsPallet;
        this.reportedBy = reportedBy;
        this.createdAt = createdAt;
    }

    public void resolve(String by, String resolution, LocalDateTime now) {
        if (status == Status.RESOLVED) throw new BusinessException("Ngoại lệ #" + id + " đã được xử lý");
        this.status = Status.RESOLVED;
        this.resolvedBy = by;
        this.resolution = resolution;
        this.resolvedAt = now;
    }

    public Long getId() { return id; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public Pallet getPallet() { return pallet; }
    public Trip getTrip() { return trip; }
    public String getDescription() { return description; }
    public boolean isHoldsPallet() { return holdsPallet; }
    public String getReportedBy() { return reportedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public String getResolution() { return resolution; }
}