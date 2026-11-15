package com.crossdock.model;

import com.crossdock.exception.BusinessException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Chuyến xe (lịch xe) nhập hoặc xuất */
@Entity
@Table(name = "trips")
public class Trip {

    public static final long LATE_TOLERANCE_MINUTES = 30;

    public enum Status {
        SCHEDULED("Đã lên lịch"), AT_DOCK("Đang ở cửa"), DEPARTED("Đã rời kho"), CANCELLED("Đã hủy");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 20)
    private String truckPlate;

    @Column(nullable = false, length = 100)
    private String carrier;

    @Column(length = 100)
    private String driverName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Dock.Type direction;

    @ManyToOne(optional = false)
    @JoinColumn(name = "dock_id")
    private Dock dock;

    @Column(nullable = false)
    private LocalDateTime scheduledArrival;

    @Column(nullable = false)
    private LocalDateTime scheduledDeparture;

    private LocalDateTime actualArrival;
    private LocalDateTime actualDeparture;

    /** Chuyến xuất: điểm đến. Chuyến nhập: nơi xuất phát */
    @Column(length = 100)
    private String destination;

    private int capacity;

    @Column(length = 50)
    private String sealNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    @Version
    private Long version;

    protected Trip() { }

    public Trip(String code, String truckPlate, String carrier, String driverName, Dock.Type direction, Dock dock,
                LocalDateTime arrival, LocalDateTime departure, String destination, int capacity) {
        this.code = code;
        this.direction = direction;
        applySchedule(truckPlate, carrier, driverName, dock, arrival, departure, destination, capacity);
    }

    public void reschedule(String truckPlate, String carrier, String driverName, Dock dock,
                           LocalDateTime arrival, LocalDateTime departure, String destination, int capacity) {
        if (status != Status.SCHEDULED) {
            throw new BusinessException("Chỉ sửa được chuyến đang 'Đã lên lịch'");
        }
        applySchedule(truckPlate, carrier, driverName, dock, arrival, departure, destination, capacity);
    }

    private void applySchedule(String truckPlate, String carrier, String driverName, Dock dock,
                               LocalDateTime arrival, LocalDateTime departure, String destination, int capacity) {
        if (dock.getType() != direction) {
            throw new BusinessException("Cửa " + dock.getCode() + " là cửa " + dock.getType().getLabel()
                    + ", không dùng cho chuyến " + direction.getLabel());
        }
        if (dock.getStatus() == Dock.Status.MAINTENANCE) {
            throw new BusinessException("Cửa " + dock.getCode() + " đang bảo trì");
        }
        if (arrival == null || departure == null || !departure.isAfter(arrival)) {
            throw new BusinessException("Giờ rời cửa phải sau giờ đến");
        }
        if (capacity <= 0) {
            throw new BusinessException("Sức chứa phải lớn hơn 0");
        }
        if (direction == Dock.Type.OUTBOUND && (destination == null || destination.isBlank())) {
            throw new BusinessException("Chuyến xuất phải có điểm đến");
        }
        this.truckPlate = truckPlate;
        this.carrier = carrier;
        this.driverName = (driverName == null || driverName.isBlank()) ? null : driverName.trim();
        this.dock = dock;
        this.scheduledArrival = arrival;
        this.scheduledDeparture = departure;
        this.destination = destination == null ? null : destination.trim();
        this.capacity = capacity;
    }

    public void arrive(LocalDateTime now) {
        if (status != Status.SCHEDULED) {
            throw new BusinessException("Chuyến " + code + " không ở trạng thái chờ đến");
        }
        dock.occupy();
        status = Status.AT_DOCK;
        actualArrival = now;
    }

    public void depart(LocalDateTime now, String seal) {
        if (status != Status.AT_DOCK) {
            throw new BusinessException("Chuyến " + code + " chưa vào cửa");
        }
        if (isOutbound() && (seal == null || seal.isBlank())) {
            throw new BusinessException("Chuyến xuất phải có số seal niêm phong");
        }
        dock.release();
        status = Status.DEPARTED;
        actualDeparture = now;
        sealNumber = seal;
    }

    public void cancel() {
        if (status != Status.SCHEDULED) {
            throw new BusinessException("Chỉ hủy được chuyến đang 'Đã lên lịch'");
        }
        status = Status.CANCELLED;
    }

    public boolean isOutbound() {
        return direction == Dock.Type.OUTBOUND;
    }

    public boolean isAcceptingPallets() {
        return isOutbound() && (status == Status.SCHEDULED || status == Status.AT_DOCK);
    }

    public boolean isLateArrival() {
        return actualArrival != null && actualArrival.isAfter(scheduledArrival.plusMinutes(LATE_TOLERANCE_MINUTES));
    }

    /** Tên bên giữ hàng khi pallet nằm trên xe (dùng cho chain of custody) */
    public String getCustodianName() {
        return carrier + " – xe " + truckPlate + (driverName != null ? " (TX " + driverName + ")" : "");
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getTruckPlate() { return truckPlate; }
    public String getCarrier() { return carrier; }
    public String getDriverName() { return driverName; }
    public Dock.Type getDirection() { return direction; }
    public Dock getDock() { return dock; }
    public LocalDateTime getScheduledArrival() { return scheduledArrival; }
    public LocalDateTime getScheduledDeparture() { return scheduledDeparture; }
    public LocalDateTime getActualArrival() { return actualArrival; }
    public LocalDateTime getActualDeparture() { return actualDeparture; }
    public String getDestination() { return destination; }
    public int getCapacity() { return capacity; }
    public String getSealNumber() { return sealNumber; }
    public Status getStatus() { return status; }
}