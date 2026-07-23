package com.flowledger.ai.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.entity.AiWorkflowDraft;
import com.flowledger.ai.repository.AiWorkflowDraftRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WorkflowDraftServiceTest {
    @Mock
    AiWorkflowDraftRepository drafts;

    @Mock
    WorkflowSuggestionService textSuggestions;

    private WorkflowDraftService service;
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(orgId, userId);
        AiProperties props = new AiProperties();
        props.setWorkflowBuilderEnabled(true);
        service = new WorkflowDraftService(props, drafts, textSuggestions);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void suggestAndCreatePersistsDraft() {
        when(drafts.save(any())).thenAnswer(inv -> {
            AiWorkflowDraft draft = inv.getArgument(0);
            draft.setId(UUID.randomUUID());
            return draft;
        });
        AiDtos.WorkflowDraftResponse res =
                service.suggestAndCreate(new AiDtos.WorkflowNlSuggestRequest("Approve purchase orders over 50k"));
        assertEquals("DRAFT", res.status());
        ArgumentCaptor<AiWorkflowDraft> cap = ArgumentCaptor.forClass(AiWorkflowDraft.class);
        verify(drafts).save(cap.capture());
        assertEquals(orgId, cap.getValue().getOrganizationId());
    }

    @Test
    void builderDisabledThrows() {
        AiProperties props = new AiProperties();
        props.setWorkflowBuilderEnabled(false);
        WorkflowDraftService disabled = new WorkflowDraftService(props, drafts, textSuggestions);
        assertThrows(ResponseStatusException.class, () -> disabled.list());
    }
}
