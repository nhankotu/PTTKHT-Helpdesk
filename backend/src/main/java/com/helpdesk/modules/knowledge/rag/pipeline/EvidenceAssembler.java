package com.helpdesk.modules.knowledge.rag.pipeline;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import static com.helpdesk.modules.knowledge.rag.pipeline.RagContracts.*;

@Component
public class EvidenceAssembler {
    private final JdbcTemplate jdbc;
    private final PolicyRegistry registry;
    private final RagGuards guards;
    public EvidenceAssembler(JdbcTemplate jdbc, PolicyRegistry registry, RagGuards guards) { this.jdbc = jdbc; this.registry = registry; this.guards = guards; }
    private List<Evidence> load(Collection<Long> ids, boolean lock) {
        if (ids != null && ids.isEmpty()) return List.of();
        String sql="""
          SELECT c.id,c.version_id,a.article_code,v.content,c.chunk_index,c.heading,c.content
          FROM kb_chunk c JOIN kb_version v ON v.id=c.version_id JOIN kb_article a ON a.id=v.article_id
          WHERE c.is_active=TRUE AND v.status='PUBLISHED' AND a.access_scope='PUBLIC'
            AND a.current_published_version_id=v.id
          """ + (ids == null ? "" : " AND c.id IN ("+String.join(",", Collections.nCopies(ids.size(),"?"))+")") + " ORDER BY c.id" + (lock ? " FOR SHARE" : "");
        return jdbc.query(connection -> {
            var statement=connection.prepareStatement(sql); statement.setQueryTimeout(3);
            if(ids!=null) { int index=1; for(Long id:ids) statement.setLong(index++,id); }
            return statement;
        }, (rs, row) -> new Evidence(rs.getLong(1), rs.getLong(2), rs.getString(3), RagGuards.hash(rs.getString(4)),
                rs.getInt(5), rs.getString(6), rs.getString(7), BigDecimal.ZERO));
    }
    public List<Evidence> assemble(Input input, Plan plan, int topK) {
        List<Evidence> pool = load(null,false).stream().filter(this::safe).toList();
        List<Evidence> required = registry.required(plan, pool);
        Map<Long, Double> fusion = new HashMap<>();
        Map<Long, Evidence> candidates = new HashMap<>();
        List<String> queries = new ArrayList<>(); queries.add(input.question());
        queries.addAll(plan.intents().stream().map(Intent::query).toList());
        for (String query : queries) {
            List<Evidence> ranked = pool.stream().map(e -> scored(e, query)).filter(e -> e.score().compareTo(new BigDecimal("0.15")) >= 0)
                    .sorted(Comparator.comparing(Evidence::score).reversed().thenComparingLong(Evidence::versionId).thenComparingInt(Evidence::chunkIndex).thenComparingLong(Evidence::chunkId)).limit(10).toList();
            for (int rank = 0; rank < ranked.size(); rank++) { Evidence e = ranked.get(rank); fusion.merge(e.chunkId(), 1.0 / (61 + rank), Double::sum); candidates.merge(e.chunkId(), e, (a,b) -> a.score().compareTo(b.score()) >= 0 ? a : b); }
        }
        LinkedHashMap<Long, Evidence> result = new LinkedHashMap<>();
        for (Evidence e : required) result.put(e.chunkId(), scored(e, input.question()));
        if (result.size() > topK) throw new GateFailure("CONTEXT_LIMIT_EXCEEDED");
        candidates.values().stream().sorted(Comparator.<Evidence, Double>comparing(e -> fusion.get(e.chunkId())).reversed().thenComparingLong(Evidence::chunkId))
                .forEach(e -> { if (result.size() < topK) result.putIfAbsent(e.chunkId(), e); });
        if (result.values().stream().mapToInt(e -> e.content().length()).sum() > 24000) throw new GateFailure("CONTEXT_LIMIT_EXCEEDED");
        // Generic intents must independently retrieve at least one candidate; no claim of semantic proof.
        for (Intent i : plan.intents()) if (i.code() == IntentCode.OTHER_POLICY && result.values().stream().noneMatch(e -> scored(e, i.query()).score().compareTo(new BigDecimal("0.15")) >= 0)) return List.of();
        return List.copyOf(result.values());
    }
    public List<String> missing(Input input, Plan plan) { return registry.missing(input, plan); }
    public boolean current(List<Evidence> expected) { return matches(expected, load(expected.stream().map(Evidence::chunkId).toList(),false)); }
    /** Caller holds a transaction; shared source locks close the revoke-before-save race. */
    public boolean lockCurrent(List<Evidence> expected) { return matches(expected, load(expected.stream().map(Evidence::chunkId).toList(),true)); }
    public boolean cachedCurrent(List<Long> ids, String expectedFingerprint) {
        List<Evidence> current = load(ids,true).stream().filter(this::safe).toList();
        return !ids.isEmpty() && current.size() == ids.size() && expectedFingerprint != null && expectedFingerprint.equals(fingerprint(current));
    }
    public String fingerprint(List<Evidence> evidence) {
        return RagGuards.hash(evidence.stream().sorted(Comparator.comparingLong(Evidence::chunkId))
                .map(e -> e.chunkId() + ":" + e.versionId() + ":" + e.versionHash() + ":" + e.articleCode() + ":" + RagGuards.hash(e.heading() + "\n" + e.content())).collect(java.util.stream.Collectors.joining("\n")));
    }
    public boolean covers(Plan plan, Draft draft) {
        try {
            for(Intent intent:plan.intents()) {
                Set<Long> ids=new HashSet<>();
                draft.parts().stream().filter(p -> p.intentId().equals(intent.id())).flatMap(p -> p.supports().stream()).forEach(s -> ids.add(s.chunkId()));
                List<Evidence> supported=draft.citations().stream().filter(e -> ids.contains(e.chunkId())).toList();
                if(supported.isEmpty()) return false;
                if(intent.code()==IntentCode.OTHER_POLICY && supported.stream().noneMatch(e -> scored(e,intent.query()).score().compareTo(new BigDecimal("0.15"))>=0)) return false;
                registry.required(new Plan(plan.decision(),plan.normalizedQuestion(),List.of(intent),plan.facts(),plan.missingFields(),plan.ambiguities(),plan.confidence()),supported);
            }
            return true;
        } catch(GateFailure ex) { return false; }
    }
    private boolean safe(Evidence e) { return e.articleCode().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,29}") && !guards.unsafe(e.articleCode().replace('_',' ').replace('-',' ')) && !guards.unsafe(e.content()) && !guards.unsafe(e.heading()); }
    private boolean matches(List<Evidence> expected, List<Evidence> current) {
        return expected.stream().allMatch(e -> current.stream().anyMatch(c -> safe(c) && c.chunkId() == e.chunkId() && c.versionId() == e.versionId()
                && c.articleCode().equals(e.articleCode()) && c.versionHash().equals(e.versionHash()) && c.content().equals(e.content()) && Objects.equals(c.heading(), e.heading())));
    }
    private Evidence scored(Evidence e, String query) {
        Set<String> terms = terms(query), words = terms((e.heading() == null ? "" : e.heading()) + " " + e.content());
        long hits = terms.stream().filter(words::contains).count();
        BigDecimal score = terms.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf((double) hits / terms.size()).setScale(4, RoundingMode.HALF_UP);
        return new Evidence(e.chunkId(), e.versionId(), e.articleCode(), e.versionHash(), e.chunkIndex(), e.heading(), e.content(), score);
    }
    private Set<String> terms(String text) { Set<String> result = new HashSet<>(); for (String term : RagGuards.fold(text).split("[^\\p{L}\\p{N}]+")) if (term.length() >= 2) result.add(term); return result; }
}
