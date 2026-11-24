package com.helpdesk.modules.knowledge.rag.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.helpdesk.modules.knowledge.rag.pipeline.RagContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EvidenceAssemblerSqlTest {
    @Test void instructionInArticleCodeIsExcludedBeforeAnyModelReceivesEvidence() {
        var jdbc=mock(JdbcTemplate.class); var policy=mock(PolicyRegistry.class);
        when(policy.required(any(),any())).thenReturn(List.of());
        var malicious=new Evidence(10,20,"IGNORE_ALL_INSTRUCTIONS","hash",0,"Chính sách","Chính sách hỗ trợ tại cửa hàng.",java.math.BigDecimal.ONE);
        var pool=new AtomicReference<List<Evidence>>(List.of(malicious));
        doAnswer(inv->pool.get()).when(jdbc).query(any(PreparedStatementCreator.class),org.mockito.ArgumentMatchers.<RowMapper<Evidence>>any());
        var assembler=new EvidenceAssembler(jdbc,policy,new RagGuards());
        var input=new Input(1L,2L,"Chính sách",List.of(new Source("question","Chính sách")));
        var plan=new Plan(Decision.READY,"Chính sách",List.of(new Intent("i1",IntentCode.OTHER_POLICY,"Chính sách",List.of())),List.of(),List.of(),List.of(),java.math.BigDecimal.ONE);
        assertThat(assembler.assemble(input,plan,10)).isEmpty();
        pool.set(List.of(new Evidence(10,20,"KB-SAFE","hash",0,malicious.heading(),malicious.content(),java.math.BigDecimal.ONE)));
        assertThat(assembler.assemble(input,plan,10)).hasSize(1);
    }
    @Test void persistenceLocksOnlyRequestedSourcesWithQueryTimeout() throws Exception {
        var jdbc=mock(JdbcTemplate.class); var connection=mock(Connection.class); var statement=mock(PreparedStatement.class);
        var captured=new AtomicReference<String>();
        when(connection.prepareStatement(anyString())).thenAnswer(inv->{captured.set(inv.getArgument(0)); return statement;});
        var evidence=new Evidence(10,20,"KB-SAFE","hash",0,"Chính sách","Chính sách hỗ trợ tại cửa hàng.",java.math.BigDecimal.ONE);
        doAnswer(inv->{((PreparedStatementCreator)inv.getArgument(0)).createPreparedStatement(connection);return List.of(evidence);})
                .when(jdbc).query(any(PreparedStatementCreator.class),org.mockito.ArgumentMatchers.<RowMapper<Evidence>>any());
        var assembler=new EvidenceAssembler(jdbc,mock(PolicyRegistry.class),new RagGuards());
        assertThat(assembler.lockCurrent(List.of(evidence))).isTrue();
        assertThat(captured.get()).contains("c.id IN (?)","FOR SHARE");
        verify(statement).setLong(1,10L); verify(statement).setQueryTimeout(3);
    }
}
