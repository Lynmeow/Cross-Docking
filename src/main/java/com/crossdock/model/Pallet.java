package com.crossdock.model;

import com.crossdock.exception.BusinessException;
import com.crossdock.util.AppUtils;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "pallets")
public class Pallet {

    public enum Status {
        RECEIVED("Đã nhận"), STAGED("Đang tập kết"), HANDOVER_PENDING("Chờ Outbound xác nhận"),
        READY("Sẵn sàng xuất"), ASSIGNED("Đã gán chuyến"), LOADED("Đã lên xe"),
        SHIPPED("Đã xuất kho"), ON_HOLD("Tạm giữ");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String contents;

    private double weightKg;

    @Column(nullable = false, length = 100)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status statusBeforeHold;

    @ManyToOne(optional = false)
    @JoinColumn(name = "inbound_trip_id")
    private Trip inboundTrip;

    /** Chỉ MỘT khóa ngoại tới chuyến xuất => về cấu trúc không thể thuộc 2 chuyến */
    @ManyToOne
    @JoinColumn(name = "outbound_trip_id")
    private Trip outboundTrip;

    /** Nhân viên đang chịu trách nhiệm (null khi hàng ở trên xe) */
    @ManyToOne
    @JoinColumn(name = "custodian_user_id")
    private AppUser custodianUser;

    @Column(length = 150)
    private String currentCustodian;

    @Column(length = 150)
    private String currentLocation;

    /** Người Outbound được chỉ định nhận bàn giao (bàn giao 2 bước) */
    @ManyToOne
    @JoinColumn(name = "pending_recipient_id")
    private AppUser pendingRecipient;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime shippedAt;

    @Version
    private Long version;

    protected Pallet() { }

    public Pallet(String code, String contents, double weightKg, String destination, Trip inboundTrip,
                  String carrierName, String location, LocalDateTime receivedAt) {
        this.code = code;
        this.contents = contents;
        this.weightKg = weightKg;
        this.destination = destination;
        this.inboundTrip = inboundTrip;
        this.currentCustodian = carrierName;
        this.currentLocation = location;
        this.receivedAt = receivedAt;
        this.status = Status.RECEIVED;
    }

    public void stage() {
        if (status != Status.RECEIVED && status != Status.STAGED) {
            throw new BusinessException("Chỉ pallet 'Đã nhận' hoặc 'Đang tập kết' mới được đưa vào khu tập kết");
        }
        status = Status.STAGED;
    }

    public void requestHandover(AppUser recipient) {
        if (status != Status.STAGED) {
            throw new BusinessException("Pallet " + code + " đang '" + status.getLabel() + "' – chỉ bàn giao pallet đang tập kết");
        }
        pendingRecipient = recipient;
        status = Status.HANDOVER_PENDING;
    }

    public void acceptHandover(AppUser by) {
        ensurePendingFor(by);
        pendingRecipient = null;
        status = Status.READY;
    }

    public void rejectHandover(AppUser by) {
        ensurePendingFor(by);
        pendingRecipient = null;
        status = Status.STAGED;
    }

    private void ensurePendingFor(AppUser user) {
        if (status != Status.HANDOVER_PENDING || pendingRecipient == null) {
            throw new BusinessException("Pallet " + code + " không ở trạng thái chờ bàn giao");
        }
        if (!pendingRecipient.getId().equals(user.getId())) {
            throw new BusinessException("Pallet " + code + " được bàn giao cho " + pendingRecipient.getDisplayName()
                    + ", bạn không thể xác nhận thay");
        }
    }

    public boolean isAssignable() {
        return outboundTrip == null && status == Status.READY;
    }

    /** Bất biến miền: không bao giờ gán pallet cho chuyến thứ hai */
    public void assignTo(Trip trip) {
        if (outboundTrip != null) {
            throw new BusinessException("Pallet " + code + " đã thuộc chuyến " + outboundTrip.getCode());
        }
        if (!isAssignable()) {
            throw new BusinessException("Pallet " + code + " đang '" + status.getLabel()
                    + "' – chỉ pallet 'Sẵn sàng xuất' mới được gán chuyến");
        }
        if (!trip.isAcceptingPallets()) {
            throw new BusinessException("Chuyến " + trip.getCode() + " không nhận pallet");
        }
        outboundTrip = trip;
        status = Status.ASSIGNED;
    }

    public Trip unassign() {
        if (outboundTrip == null) throw new BusinessException("Pallet " + code + " chưa được gán chuyến nào");
        if (status == Status.LOADED || status == Status.SHIPPED) {
            throw new BusinessException("Pallet " + code + " đã lên xe, không thể gỡ khỏi chuyến");
        }
        Trip old = outboundTrip;
        outboundTrip = null;
        if (status == Status.ON_HOLD) statusBeforeHold = Status.READY;
        else status = Status.READY;
        return old;
    }

    public void load() {
        if (status != Status.ASSIGNED) throw new BusinessException("Chỉ pallet 'Đã gán chuyến' mới được xếp lên xe");
        if (outboundTrip.getStatus() != Trip.Status.AT_DOCK) {
            throw new BusinessException("Xe của chuyến " + outboundTrip.getCode() + " chưa vào cửa");
        }
        status = Status.LOADED;
    }

    public void ship(LocalDateTime now) {
        if (status != Status.LOADED) throw new BusinessException("Pallet " + code + " chưa lên xe");
        status = Status.SHIPPED;
        shippedAt = now;
    }

    /** @return true nếu pallet vừa chuyển sang tạm giữ */
    public boolean hold() {
        if (status == Status.LOADED || status == Status.SHIPPED) {
            throw new BusinessException("Pallet " + code + " đã lên xe/xuất kho, không thể tạm giữ trong kho");
        }
        if (status == Status.ON_HOLD) return false;
        if (status == Status.HANDOVER_PENDING) {
            pendingRecipient = null;
            statusBeforeHold = Status.STAGED;
        } else {
            statusBeforeHold = status;
        }
        status = Status.ON_HOLD;
        return true;
    }

    public void release() {
        if (status != Status.ON_HOLD) return;
        status = statusBeforeHold != null ? statusBeforeHold : Status.STAGED;
        statusBeforeHold = null;
    }

    public void moveCustody(AppUser user, String custodianName, String location) {
        this.custodianUser = user;
        this.currentCustodian = custodianName;
        this.currentLocation = location;
    }

    /** Thời gian pallet nằm trong kho (giờ) */
    public double getDwellHours() {
        LocalDateTime end = shippedAt != null ? shippedAt : AppUtils.now();
        return Duration.between(receivedAt, end).toMinutes() / 60.0;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getContents() { return contents; }
    public double getWeightKg() { return weightKg; }
    public String getDestination() { return destination; }
    public Status getStatus() { return status; }
    public Status getStatusBeforeHold() { return statusBeforeHold; }
    public Trip getInboundTrip() { return inboundTrip; }
    public Trip getOutboundTrip() { return outboundTrip; }
    public AppUser getCustodianUser() { return custodianUser; }
    public String getCurrentCustodian() { return currentCustodian; }
    public String getCurrentLocation() { return currentLocation; }
    public AppUser getPendingRecipient() { return pendingRecipient; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public LocalDateTime getShippedAt() { return shippedAt; }
}