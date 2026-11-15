package com.crossdock.service;

import com.crossdock.model.AppUser;
import com.crossdock.model.CustodyEvent;
import com.crossdock.model.Pallet;
import com.crossdock.repository.CustodyEventRepository;
import com.crossdock.util.AppUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Ghi nhận & kiểm chứng chain of custody */
@Service
public class CustodyService {

    public record ChainVerification(boolean valid, int eventCount, Integer brokenAtSequence, String message) { }

    private final CustodyEventRepository repo;

    public CustodyService(CustodyEventRepository repo) {
        this.repo = repo;
    }

    /**
     * @param toUser   nhân viên nhận trách nhiệm (null nếu là nhà xe)
     * @param toName   tên bên nhận hiển thị
     * @param actor    tài khoản thực hiện thao tác
     */
    @Transactional
    public CustodyEvent record(Pallet pallet, CustodyEvent.Type type, AppUser toUser, String toName,
                               String location, AppUser actor, String note) {
        Optional<CustodyEvent> last = repo.findTopByPalletIdOrderBySequenceNoDesc(pallet.getId());
        int seq = last.map(e -> e.getSequenceNo() + 1).orElse(1);
        String prevHash = last.map(CustodyEvent::getHash).orElse(CustodyEvent.GENESIS_HASH);

        CustodyEvent event = new CustodyEvent(pallet, seq, type, pallet.getCurrentCustodian(), toName,
                location, actor.getDisplayName(), AppUtils.now(), AppUtils.truncate(note, 500), prevHash);
        pallet.moveCustody(toUser, toName, location);
        return repo.save(event);
    }

    @Transactional(readOnly = true)
    public List<CustodyEvent> history(Long palletId) {
        return repo.findByPalletIdOrderBySequenceNoAsc(palletId);
    }

    @Transactional(readOnly = true)
    public ChainVerification verify(Long palletId) {
        List<CustodyEvent> events = repo.findByPalletIdOrderBySequenceNoAsc(palletId);
        String expectedPrev = CustodyEvent.GENESIS_HASH;
        int expectedSeq = 1;
        for (CustodyEvent e : events) {
            if (e.getSequenceNo() != expectedSeq) {
                return new ChainVerification(false, events.size(), expectedSeq,
                        "Thiếu sự kiện #" + expectedSeq + " trong chuỗi giám sát");
            }
            if (!e.getPreviousHash().equals(expectedPrev)) {
                return new ChainVerification(false, events.size(), e.getSequenceNo(),
                        "Liên kết hash bị đứt tại sự kiện #" + e.getSequenceNo());
            }
            if (!e.computeHash().equals(e.getHash())) {
                return new ChainVerification(false, events.size(), e.getSequenceNo(),
                        "Nội dung sự kiện #" + e.getSequenceNo() + " đã bị sửa đổi");
            }
            expectedPrev = e.getHash();
            expectedSeq++;
        }
        return new ChainVerification(true, events.size(), null,
                "Chuỗi giám sát toàn vẹn (" + events.size() + " sự kiện)");
    }
}