package com.crossdock.model;

import com.crossdock.exception.BusinessException;
import jakarta.persistence.*;

/** Cửa nhập / cửa xuất của kho */
@Entity
@Table(name = "docks")
public class Dock {

    public enum Type {
        INBOUND("Nhập"), OUTBOUND("Xuất");
        private final String label;
        Type(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Status {
        AVAILABLE("Trống"), OCCUPIED("Đang có xe"), MAINTENANCE("Bảo trì");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.AVAILABLE;

    /** Optimistic locking: 2 xe không thể cùng chiếm 1 cửa */
    @Version
    private Long version;

    protected Dock() { }

    public Dock(String code, Type type) {
        this.code = code;
        this.type = type;
    }

    public void occupy() {
        if (status != Status.AVAILABLE) {
            throw new BusinessException("Cửa " + code + " hiện không trống (" + status.getLabel() + ")");
        }
        status = Status.OCCUPIED;
    }

    public void release() {
        if (status == Status.OCCUPIED) status = Status.AVAILABLE;
    }

    public void toggleMaintenance() {
        if (status == Status.OCCUPIED) {
            throw new BusinessException("Cửa " + code + " đang có xe, không thể chuyển sang bảo trì");
        }
        status = (status == Status.MAINTENANCE) ? Status.AVAILABLE : Status.MAINTENANCE;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public Long getVersion() { return version; }
}