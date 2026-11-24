package com.helpdesk.modules.knowledge.rag.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import static com.helpdesk.modules.knowledge.rag.pipeline.RagContracts.*;

/** Model JSON is untrusted. These structural checks do not prove semantic entailment. */
@Component
public class RagGuards {
    private static final Set<String> FIELDS = Set.of("orderValue", "membershipTier", "bulkyItem", "received", "manufacturerFaultConfirmed", "returnApproved", "returnReason", "purchaseChannel", "elapsedDays", "giftOrder");
    private static final Map<String, String> QUESTIONS = Map.ofEntries(
            Map.entry("orderValue", "Giá trị đơn hàng của bạn là bao nhiêu?"), Map.entry("membershipTier", "Bạn thuộc hạng thành viên nào?"),
            Map.entry("bulkyItem", "Đơn hàng có sản phẩm cồng kềnh không?"), Map.entry("received", "Bạn đã nhận hàng chưa?"),
            Map.entry("manufacturerFaultConfirmed", "Cửa hàng đã xác nhận sản phẩm lỗi do nhà sản xuất chưa?"),
            Map.entry("returnApproved", "Cửa hàng đã xác nhận đơn thuộc diện đổi trả chưa?"), Map.entry("returnReason", "Bạn muốn đổi trả vì lý do gì?"),
            Map.entry("purchaseChannel", "Bạn mua trực tuyến hay tại cửa hàng?"), Map.entry("elapsedDays", "Bạn mua và nhận hàng vào ngày nào?"),
            Map.entry("giftOrder", "Đây có phải đơn mua để tặng người khác không?"));
    private static final Pattern NUMBERS = Pattern.compile("[0-9]+(?:[.,][0-9]+)*");
    public static String fold(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}|\\p{Cf}", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public boolean unsafe(String text) {
        String s = fold(text).replaceAll("\\s+", " ");
        return List.of("ignore previous instructions", "ignore all instructions", "reveal system prompt", "show system prompt",
                "bo qua huong dan", "tiet lo prompt", "tiet lo system prompt", "ghi chu noi bo", "internal note",
                "api key", "mat khau admin", "system_instruction", "<script", "javascript:").stream().anyMatch(s::contains);
    }
    public void input(Input in) {
        if (in.question() == null || in.question().isBlank() || in.question().length() > 8000 || in.sources() == null || in.sources().isEmpty()
                || in.sources().stream().mapToInt(s -> s.text().length()).sum() > 16000) fail("INPUT_LIMIT_EXCEEDED");
        if (unsafe(in.question()) || in.sources().stream().anyMatch(s -> unsafe(s.text()))) fail("OUT_OF_AUTO_SCOPE");
    }
    public Plan plan(Input in, JsonNode root) {
        String error = "QUERY_PLAN_INVALID";
        object(root, error, "decision", "normalizedQuestion", "intents", "facts", "missingFields", "ambiguities", "confidence");
        Decision decision = enumeration(root, "decision", Set.of("READY", "ASK_CLARIFICATION", "OUT_OF_SCOPE"), error);
        String normalized = text(root, "normalizedQuestion", 8000, error);
        String original = String.join("\n", in.sources().stream().map(Source::text).toList());
        checkRewrite(original, normalized, error);
        if (!numbers(normalized).containsAll(numbers(in.question()))) fail(error);
        if (hasNegation(in.question()) && !hasNegation(normalized)) fail(error);
        var facts = new ArrayList<Fact>();
        var factIds = new HashSet<String>();
        for (JsonNode f : array(root, "facts", 20, error)) {
            object(f, error, "id", "field", "value", "sourceId", "start", "end");
            String id = text(f, "id", 40, error), field = text(f, "field", 40, error), value = text(f, "value", 500, error);
            if (!id.matches("f(?:[1-9]|1[0-9]|20)") || !FIELDS.contains(field) || !factIds.add(id) || !validFact(field, value)) fail(error);
            String sourceId = text(f, "sourceId", 80, error);
            Source source = in.sources().stream().filter(s -> s.id().equals(sourceId)).findFirst().orElseThrow(() -> new GateFailure(error));
            int start = integer(f, "start", error), end = integer(f, "end", error);
            if (start < 0 || end <= start || end > source.text().length() || !source.text().substring(start, end).equals(value)) fail(error);
            facts.add(new Fact(id, field, value, sourceId, start, end));
        }
        var intents = new ArrayList<Intent>();
        var ids = new HashSet<String>();
        for (JsonNode i : array(root, "intents", 3, error)) {
            object(i, error, "id", "code", "query", "factRefs");
            String id = text(i, "id", 40, error), code = text(i, "code", 40, error), query = text(i, "query", 1000, error);
            if (!id.matches("i[1-3]") || !ids.add(id)) fail(error);
            IntentCode intentCode;
            try { intentCode = IntentCode.valueOf(code); } catch (IllegalArgumentException ex) { throw new GateFailure(error); }
            checkRewrite(original, query, error);
            List<String> refs = strings(i, "factRefs", 20, 40, error);
            if (!factIds.containsAll(refs)) fail(error);
            String referenced = facts.stream().filter(f -> refs.contains(f.id())).map(Fact::value).collect(java.util.stream.Collectors.joining(" "));
            if (!numericClaims(referenced).containsAll(numericClaims(query))) fail(error);
            intents.add(new Intent(id, intentCode, query, refs));
        }
        List<String> missing = missing(root, error), ambiguities = strings(root, "ambiguities", 3, 250, error);
        if (unsafe(String.join(" ", ambiguities))) fail(error);
        BigDecimal confidence = confidence(root, error);
        if (decision == Decision.READY && (intents.isEmpty() || !ambiguities.isEmpty())) fail(error);
        if (decision == Decision.READY && confidence.compareTo(new BigDecimal("0.8")) < 0) fail("LOW_CONFIDENCE");
        return new Plan(decision, normalized, List.copyOf(intents), List.copyOf(facts), missing, ambiguities, confidence);
    }
    public Draft clarification(List<String> fields) {
        String text = fields.isEmpty() ? "Bạn vui lòng mô tả rõ yêu cầu và các thông tin liên quan để nhân viên hỗ trợ chính xác hơn."
                : String.join(" ", fields.stream().distinct().map(QUESTIONS::get).toList());
        return new Draft(Decision.ASK_CLARIFICATION, text, List.of(), BigDecimal.ZERO, "QUERY_NEEDS_CLARIFICATION");
    }
    public Draft draft(Input in, Plan plan, List<Evidence> evidence, JsonNode root) {
        String error = "RAG_OUTPUT_INVALID";
        object(root, error, "decision", "answerParts", "missingFields", "reasonCode", "confidence");
        Decision decision = enumeration(root, "decision", Set.of("ANSWER", "ASK_CLARIFICATION", "FALLBACK"), error);
        List<String> missing = missing(root, error);
        text(root, "reasonCode", 60, error); // Model reason is not trusted for stored enum codes.
        BigDecimal confidence = confidence(root, error);
        JsonNode parts = array(root, "answerParts", 8, error);
        if (decision != Decision.ANSWER) {
            if (!parts.isEmpty()) fail(error);
            return decision == Decision.ASK_CLARIFICATION ? clarification(missing) : Draft.stop(Decision.FALLBACK, "NO_RELEVANT_KB");
        }
        if (parts.isEmpty() || !missing.isEmpty() || confidence.compareTo(new BigDecimal("0.8")) < 0) fail(error);
        Set<String> expected = new HashSet<>(plan.intents().stream().map(Intent::id).toList());
        Set<String> covered = new HashSet<>();
        Map<Long, Evidence> citations = new LinkedHashMap<>();
        List<AnswerPart> validatedParts = new ArrayList<>();
        for (JsonNode part : parts) {
            object(part, error, "intentId", "text", "supports");
            String intent = text(part, "intentId", 40, error), answer = text(part, "text", 4000, error);
            if (!expected.contains(intent) || unsafe(answer) || answer.contains("<") || answer.contains(">") || Pattern.compile("(?i)https?://|!\\[|\\]\\(").matcher(answer).find()) fail(error);
            covered.add(intent);
            List<String> quotes = new ArrayList<>();
            List<Support> validatedSupports = new ArrayList<>();
            JsonNode supports = array(part, "supports", 10, error);
            if (supports.isEmpty()) fail(error);
            for (JsonNode support : supports) {
                object(support, error, "chunkId", "versionId", "quote");
                long chunk = positiveLong(support, "chunkId", error), version = positiveLong(support, "versionId", error);
                Evidence source = evidence.stream().filter(e -> e.chunkId() == chunk && e.versionId() == version).findFirst().orElseThrow(() -> new GateFailure("CITATION_INVALID"));
                String quote = text(support, "quote", 6000, error);
                if (quote.length() < 16 || !source.content().contains(quote) || unsafe(quote)) fail("CITATION_INVALID");
                quotes.add(quote); citations.put(chunk, source);
                validatedSupports.add(new Support(chunk, version, quote));
            }
            if (!numbers(String.join(" ", quotes)).containsAll(numbers(answer))) fail(error);
            // v1 is deliberately extractive. The model cannot turn a conditional policy into an individual decision.
            if (!answer.equals(String.join("\n", quotes))) fail(error);
            String folded = fold(answer);
            if (List.of("da huy don", "da hoan tien", "da chuyen tien", "da cap nhat don", "toi da thuc hien").stream().anyMatch(folded::contains)) fail(error);
            validatedParts.add(new AnswerPart(intent, List.copyOf(validatedSupports)));
        }
        String answer = "Chính sách tham khảo — nhân viên cần đối chiếu điều kiện với khách:\n\n" + citations.values().stream()
                .map(e -> (e.heading() == null ? "" : e.heading()+"\n") + policyBody(e.content())).collect(java.util.stream.Collectors.joining("\n\n"));
        if (!covered.equals(expected) || answer.length() > 16000 || answer.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60000) fail(error);
        return new Draft(Decision.ANSWER, answer, List.copyOf(citations.values()), confidence, null, List.copyOf(validatedParts));
    }
    public static String policyBody(String content) {
        int start = content.startsWith("#") && content.contains("\n") ? content.indexOf('\n')+1 : 0;
        int end = content.lastIndexOf("\nNguồn:");
        return content.substring(start, end > start ? end : content.length()).trim()
                .replaceAll("\\[([^\\]]+)]\\(https?://[^)]+\\)", "$1").replaceAll("https?://\\S+", "");
    }
    private boolean validFact(String field, String value) {
        String v=fold(value).trim();
        return switch (field) {
            case "orderValue" -> v.matches("[0-9]+(?:[.,][0-9]+)*\\s*(?:trieu|nghin|ngan|k|dong|vnd|d)");
            case "elapsedDays" -> v.matches("[0-9]+\\s*(?:ngay|days?)");
            case "membershipTier" -> v.matches("smem|svip|snew|smember|s-member|khach vang lai|khach le");
            case "received" -> v.matches(".*(?:da nhan|chua nhan|khong nhan).*?");
            case "bulkyItem" -> v.contains("cong kenh") || v.matches(".*[0-9]+\\s*(kg|cm).*?");
            case "manufacturerFaultConfirmed" -> v.contains("loi") && (v.contains("xac nhan") || v.contains("nha san xuat"));
            case "returnApproved" -> v.contains("doi tra") && (v.contains("xac nhan") || v.contains("duyet") || v.contains("thuoc dien"));
            case "purchaseChannel" -> v.matches("online|truc tuyen|mua tu xa|tai cua hang|cua hang");
            case "giftOrder" -> v.contains("tang") || v.contains("qua");
            case "returnReason" -> !unsafe(value) && value.length() >= 4;
            default -> false;
        };
    }
    private Set<String> numericClaims(String value) {
        var pattern=Pattern.compile("([0-9]+(?:[.,][0-9]+)*)\\s*(ngay|days?|thang|nam|gio|phut|trieu|nghin|ngan|dong|vnd|kg|cm|k|d|%)?");
        Set<String> claims=new HashSet<>(); var matcher=pattern.matcher(fold(value));
        while(matcher.find()) {
            String unit=matcher.group(2)==null?"":matcher.group(2);
            unit=switch(unit) { case "ngan","nghin","k"->"k"; case "dong","d","vnd"->"vnd"; case "day","days"->"ngay"; default->unit; };
            claims.add(matcher.group(1)+"|"+unit);
        }
        return claims;
    }
    private void checkRewrite(String original, String candidate, String error) {
        if (unsafe(candidate) || !numericClaims(original).containsAll(numericClaims(candidate))) fail(error);
        String before = fold(original), after = fold(candidate);
        for (String condition : List.of("da xac nhan", "du dieu kien", "mien phi", "loi nha san xuat", "chua nhan", "da nhan"))
            if (after.contains(condition) && !before.contains(condition)) fail(error);
    }
    private boolean hasNegation(String s) { return Pattern.compile("\\b(chua|khong)\\b").matcher(fold(s)).find(); }
    private Set<String> numbers(String value) { Set<String> result = new HashSet<>(); NUMBERS.matcher(value).results().forEach(m -> result.add(m.group())); return result; }
    private List<String> missing(JsonNode n, String error) { List<String> fields = strings(n, "missingFields", 10, 40, error); if (!FIELDS.containsAll(fields)) fail(error); return fields; }
    private Decision enumeration(JsonNode n, String name, Set<String> allowed, String error) { String value = text(n, name, 30, error); if (!allowed.contains(value)) fail(error); return Decision.valueOf(value); }
    private void object(JsonNode n, String error, String... fields) { if (n == null || !n.isObject() || n.size() != fields.length) fail(error); for (String f : fields) if (!n.has(f)) fail(error); }
    private String text(JsonNode n, String key, int max, String error) { JsonNode v = n.get(key); if (v == null || !v.isTextual() || v.asText().isBlank() || v.asText().length() > max) fail(error); return v.asText(); }
    private JsonNode array(JsonNode n, String key, int max, String error) { JsonNode v = n.get(key); if (v == null || !v.isArray() || v.size() > max) fail(error); return v; }
    private List<String> strings(JsonNode n, String key, int max, int length, String error) { List<String> result = new ArrayList<>(); for (JsonNode v : array(n, key, max, error)) { if (!v.isTextual() || v.asText().isBlank() || v.asText().length() > length) fail(error); result.add(v.asText()); } return List.copyOf(result); }
    private int integer(JsonNode n, String key, String error) { JsonNode v = n.get(key); if (v == null || !v.isIntegralNumber() || !v.canConvertToInt()) fail(error); return v.intValue(); }
    private long positiveLong(JsonNode n, String key, String error) { JsonNode v = n.get(key); if (v == null || !v.isIntegralNumber() || !v.canConvertToLong() || v.longValue() <= 0) fail(error); return v.longValue(); }
    private BigDecimal confidence(JsonNode n, String error) { JsonNode v = n.get("confidence"); if (v == null || !v.isNumber() || !Double.isFinite(v.doubleValue()) || v.decimalValue().compareTo(BigDecimal.ZERO) < 0 || v.decimalValue().compareTo(BigDecimal.ONE) > 0) fail(error); return v.decimalValue(); }
    private void fail(String reason) { throw new GateFailure(reason); }
}
