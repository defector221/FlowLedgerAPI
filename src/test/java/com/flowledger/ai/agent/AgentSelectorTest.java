package com.flowledger.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentSelectorTest {
    @Test
    void fromAliasesMapLegacyCodes() {
        assertEquals(AiAgentType.CFO, AiAgentType.from("FINANCE"));
        assertEquals(AiAgentType.ACCOUNTANT, AiAgentType.from("ACCOUNTING"));
        assertEquals(AiAgentType.INVENTORY_PLANNER, AiAgentType.from("INVENTORY"));
        assertEquals(AiAgentType.PROCUREMENT, AiAgentType.from("PURCHASE"));
        assertEquals(AiAgentType.GST_EXPERT, AiAgentType.from("GST"));
        assertEquals(AiAgentType.SALES_COACH, AiAgentType.from("SALES"));
        assertEquals(AiAgentType.ASK, AiAgentType.from(""));
        assertEquals(AiAgentType.ASK, AiAgentType.from(null));
    }

    @Test
    void catalogIncludesAskAndCollections() {
        List<String> codes =
                java.util.Arrays.stream(AiAgentType.values()).map(Enum::name).toList();
        assertTrue(codes.contains("ASK"));
        assertTrue(codes.contains("COLLECTIONS"));
        assertTrue(codes.contains("CFO"));
    }
}
