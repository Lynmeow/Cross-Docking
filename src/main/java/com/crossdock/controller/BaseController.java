package com.crossdock.controller;

import com.crossdock.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

public abstract class BaseController {

    /** Chỉ cho phép redirect về đường dẫn nội bộ */
    protected String back(String requested, String fallback) {
        if (requested != null && requested.startsWith("/") && !requested.startsWith("//") && !requested.contains("\\")) {
            return requested;
        }
        return fallback;
    }

    /** Thực thi thao tác, gắn thông báo rồi redirect (pattern Post-Redirect-Get) */
    protected String exec(RedirectAttributes ra, String redirect, String okMessage, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("success", okMessage);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (OptimisticLockingFailureException | PessimisticLockingFailureException e) {
            ra.addFlashAttribute("error", "Dữ liệu vừa được người khác thay đổi, vui lòng tải lại và thử lại.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Dữ liệu vi phạm ràng buộc (có thể trùng mã).");
        }
        return "redirect:" + redirect;
    }
}