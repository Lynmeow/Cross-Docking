package com.crossdock.model;

import com.crossdock.exception.BusinessException;
import jakarta.persistence.*;

@Entity
@Table(name = "asn_lines",
        uniqueConstraints = @UniqueConstraint(name = "uk_asn_line_code", columnNames = {"asn_id", "pallet_code"}))
public class AsnLine {

    public enum Status {
        PENDING("Chờ nhận"), RECEIVED("Đã nhận"), DAMAGED("Nhận – hư hỏng"), MISSING("Thiếu");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "asn_id")
    private Asn asn;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "pallet_code", nullable = false, length = 30)
    private String palletCode;

    @Column(nullable = false, length = 200)
    private String contents;

    private double expectedWeightKg;

    private Double actualWeightKg;

    @Column(nullable = false, length = 100)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** ManyToOne (không dùng OneToOne) vì SQL Server không cho nhiều NULL trên cột UNIQUE */
    @ManyToOne
    @JoinColumn(name = "pallet_id")
    private Pallet pallet;

    protected AsnLine() { }

    AsnLine(Asn asn, int lineNo, String palletCode, String contents, double expectedWeightKg, String destination) {
        this.asn = asn;
        this.lineNo = lineNo;
        this.palletCode = palletCode;
        this.contents = contents;
        this.expectedWeightKg = expectedWeightKg;
        this.destination = destination;
    }

    public void markReceived(Pallet pallet, double actualWeightKg, boolean damaged) {
        if (!isPending()) throw new BusinessException("Dòng ASN " + palletCode + " đã được xử lý");
        this.pallet = pallet;
        this.actualWeightKg = actualWeightKg;
        this.status = damaged ? Status.DAMAGED : Status.RECEIVED;
    }

    void markMissing() {
        this.status = Status.MISSING;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public Long getId() { return id; }
    public Asn getAsn() { return asn; }
    public int getLineNo() { return lineNo; }
    public String getPalletCode() { return palletCode; }
    public String getContents() { return contents; }
    public double getExpectedWeightKg() { return expectedWeightKg; }
    public Double getActualWeightKg() { return actualWeightKg; }
    public String getDestination() { return destination; }
    public Status getStatus() { return status; }
    public Pallet getPallet() { return pallet; }
}