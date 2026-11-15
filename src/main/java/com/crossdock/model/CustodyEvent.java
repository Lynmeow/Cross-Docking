package com.crossdock.model;

import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/** Một mắt xích trong chain of custody. Mỗi sự kiện chứa hash của sự kiện trước. */
@Entity
@Table(name = "custody_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_custody_pallet_seq", columnNames = {"pallet_id", "sequence_no"}))
public class CustodyEvent {

    public static final String GENESIS_HASH = "0".repeat(64);

    public enum Type {
        RECEIVED("Nhận từ xe nhập"), STAGED("Đưa vào khu tập kết"),
        HANDOVER_REQUESTED("Yêu cầu bàn giao cho Outbound"), HANDOVER_ACCEPTED("Outbound xác nhận nhận"),
        HANDOVER_REJECTED("Outbound từ chối nhận"), ASSIGNED("Gán chuyến xuất"), UNASSIGNED("Gỡ khỏi chuyến"),
        REASSIGNED("Chuyển chuyến (quản lý)"), LOADED("Bàn giao lên xe xuất"), DEPARTED("Xe rời kho"),
        HOLD("Tạm giữ do ngoại lệ"), RELEASED("Giải phóng tạm giữ");
        private final String label;
        Type(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pallet_id")
    private Pallet pallet;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(length = 150)
    private String fromCustodian;

    @Column(length = 150)
    private String toCustodian;

    @Column(length = 150)
    private String location;

    @Column(length = 150)
    private String actor;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, length = 64)
    private String hash;

    protected CustodyEvent() { }

    public CustodyEvent(Pallet pallet, int sequenceNo, Type type, String fromCustodian, String toCustodian,
                        String location, String actor, LocalDateTime occurredAt, String note, String previousHash) {
        this.pallet = pallet;
        this.sequenceNo = sequenceNo;
        this.type = type;
        this.fromCustodian = fromCustodian;
        this.toCustodian = toCustodian;
        this.location = location;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.note = note;
        this.previousHash = previousHash;
        this.hash = computeHash();
    }

    public String computeHash() {
        String payload = String.join("|", previousHash, pallet.getCode(), String.valueOf(sequenceNo), type.name(),
                nz(fromCustodian), nz(toCustodian), nz(location), nz(actor), occurredAt.toString(), nz(note));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public Long getId() { return id; }
    public Pallet getPallet() { return pallet; }
    public int getSequenceNo() { return sequenceNo; }
    public Type getType() { return type; }
    public String getFromCustodian() { return fromCustodian; }
    public String getToCustodian() { return toCustodian; }
    public String getLocation() { return location; }
    public String getActor() { return actor; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getNote() { return note; }
    public String getPreviousHash() { return previousHash; }
    public String getHash() { return hash; }
}