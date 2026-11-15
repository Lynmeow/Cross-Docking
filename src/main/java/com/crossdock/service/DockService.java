package com.crossdock.service;

import com.crossdock.exception.BusinessException;
import com.crossdock.model.AuditLog;
import com.crossdock.model.Dock;
import com.crossdock.model.Role;
import com.crossdock.repository.DockRepository;
import com.crossdock.util.AppUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DockService {

    private final DockRepository dockRepo;
    private final AccessPolicy policy;
    private final AuditService audit;

    public DockService(DockRepository dockRepo, AccessPolicy policy, AuditService audit) {
        this.dockRepo = dockRepo;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Dock> findAll() {
        return dockRepo.findAllByOrderByCodeAsc();
    }

    @Transactional
    public Dock create(String code, Dock.Type type) {
        policy.requireAny(Role.MANAGER);
        String c = AppUtils.requireText(code, "Mã cửa").toUpperCase();
        if (type == null) throw new BusinessException("Phải chọn loại cửa");
        if (dockRepo.existsByCode(c)) throw new BusinessException("Mã cửa " + c + " đã tồn tại");
        Dock dock = dockRepo.save(new Dock(c, type));
        audit.log(AuditLog.Category.DOCK, "CREATE_DOCK", c, "Thêm cửa " + type.getLabel());
        return dock;
    }

    @Transactional
    public void toggleMaintenance(Long id) {
        policy.requireAny(Role.MANAGER);
        Dock dock = dockRepo.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy cửa #" + id));
        Dock.Status before = dock.getStatus();
        dock.toggleMaintenance();
        audit.log(AuditLog.Category.DOCK, "UPDATE_DOCK", dock.getCode(),
                "Trạng thái: " + before.getLabel() + " → " + dock.getStatus().getLabel());
    }
}