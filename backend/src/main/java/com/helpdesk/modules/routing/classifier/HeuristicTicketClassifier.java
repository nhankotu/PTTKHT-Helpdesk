package com.helpdesk.modules.routing.classifier;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Classifier mặc định cho chế độ dev/offline/test khi chưa cấu hình OPENAI_API_KEY.
 */
@Component
public class HeuristicTicketClassifier implements TicketClassifier {

    @Override
    public ClassificationSuggestion classify(String subject, String publicDescription, List<String> allowedCategoryCodes) {
        String text = ((subject != null ? subject : "") + " " + (publicDescription != null ? publicDescription : ""))
                .toLowerCase(Locale.ROOT);

        if (containsAny(text, "thanh toán", "chuyển khoản", "hóa đơn", "hoàn tiền", "payment", "invoice", "billing")) {
            return new ClassificationSuggestion("PAYMENT", new BigDecimal("0.9100"), "heuristic-v1", "Khớp từ khóa thanh toán");
        }
        if (containsAny(text, "đăng nhập", "mật khẩu", "tài khoản", "khóa", "login", "password", "account")) {
            return new ClassificationSuggestion("ACCOUNT", new BigDecimal("0.8800"), "heuristic-v1", "Khớp từ khóa tài khoản");
        }
        if (containsAny(text, "lỗi", "bug", "crash", "500", "không truy cập", "kỹ thuật", "error", "api")) {
            return new ClassificationSuggestion("TECHNICAL", new BigDecimal("0.8600"), "heuristic-v1", "Khớp từ khóa kỹ thuật");
        }
        if (containsAny(text, "đơn hàng", "giao hàng", "vận chuyển", "đổi trả", "order", "shipping")) {
            return new ClassificationSuggestion("ORDER", new BigDecimal("0.8700"), "heuristic-v1", "Khớp từ khóa đơn hàng");
        }
        return new ClassificationSuggestion("OTHER", new BigDecimal("0.4500"), "heuristic-v1", "Nội dung chung, cần Supervisor kiểm tra");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
