package com.helpdesk.modules.knowledge.rag.pipeline;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.modules.knowledge.repository.RagRequestStore;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import static com.helpdesk.modules.knowledge.rag.pipeline.RagContracts.*;

/** Fixed DAG; there are no model retry or self-repair loops. No transaction spans provider calls. */
@Service
public class RagPipeline {
    private final RagGuards guards;
    private final QueryPreprocessor first;
    private final GroundedAnswerer second;
    private final EvidenceAssembler evidence;
    private final RagRequestStore requests;
    public RagPipeline(RagGuards guards, QueryPreprocessor first, GroundedAnswerer second, EvidenceAssembler evidence, RagRequestStore requests) {
        this.guards = guards; this.first = first; this.second = second; this.evidence = evidence; this.requests = requests;
    }
    public Execution execute(Input input, int topK, String suppliedKey) {
        long start = System.nanoTime();
        String key = suppliedKey;
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,100}") || topK < 1 || topK > 10) throw BusinessException.unprocessable(ErrorCode.VALIDATION_ERROR, "Cần Idempotency-Key ổn định từ client và topK hợp lệ.");
        String fingerprint = RagGuards.hash(input.ticketId() + "\n" + topK + "\n" + input.question() + "\n" + input.sources());
        var state = requests.begin(input.actorId(), key, fingerprint);
        if (!state.fresh()) {
            if (state.runId() != null) return new Execution(key, null, state.runId(), "cached", 0);
            throw BusinessException.conflict(ErrorCode.ACTIVE_RULE_CONFLICT, "Lượt AI đang chạy hoặc đã gián đoạn. Key này không được gọi lại model; hãy chuyển nhân viên xử lý.");
        }
        Draft result;
        String modelLabel = "guard";
        List<Evidence> context=List.of();
        try {
            guards.input(input);
            if (!requests.claim(input.actorId(), key, 1)) throw new GateFailure("AI_SERVICE_ERROR");
            modelLabel = first.modelName();
            var rawPlan = first.prepare(input);
            deadline(start);
            Plan plan = guards.plan(input, rawPlan);
            input.trace().plan(plan, input.sources());
            if (plan.decision() == Decision.OUT_OF_SCOPE) result = Draft.stop(Decision.FALLBACK, "OUT_OF_AUTO_SCOPE");
            else if (plan.decision() == Decision.ASK_CLARIFICATION || !plan.missingFields().isEmpty()) result = guards.clarification(plan.missingFields());
            else {
                List<String> missing = evidence.missing(input, plan);
                if (missing != null && !missing.isEmpty()) result = guards.clarification(missing);
                else {
                    List<Evidence> found = evidence.assemble(input, plan, topK);
                    if (found.isEmpty()) result = Draft.stop(Decision.FALLBACK, "NO_RELEVANT_KB");
                    else {
                        deadline(start);
                        if (!evidence.current(found)) throw new GateFailure("OUTDATED_OR_REVOKED_KB");
                        if (!requests.claim(input.actorId(), key, 2)) throw new GateFailure("AI_SERVICE_ERROR");
                        context=found; input.trace().context(found);
                        modelLabel = modelLabel + "/" + second.modelName();
                        result = guards.draft(input, plan, found, second.answer(input, plan, found));
                        if (result.decision() == Decision.ANSWER && !evidence.covers(plan, result)) throw new GateFailure("CITATION_INVALID");
                        deadline(start);
                        if (!evidence.current(found)) throw new GateFailure("OUTDATED_OR_REVOKED_KB");
                    }
                }
            }
        } catch (GateFailure ex) {
            result = "LOW_CONFIDENCE".equals(ex.reason()) ? guards.clarification(List.of()) : Draft.stop(Decision.FALLBACK, ex.reason());
        } catch (Exception ex) {
            // Never log raw provider messages: they can contain sensitive context.
            result = Draft.stop(Decision.FAILED, "AI_SERVICE_ERROR");
        }
        return new Execution(key, result, null, modelLabel, (int) ((System.nanoTime() - start) / 1_000_000), input.trace().audit(), context);
    }
    private void deadline(long start) { if (System.nanoTime() - start > 25_000_000_000L) throw new GateFailure("REQUEST_DEADLINE_EXCEEDED"); }
}
