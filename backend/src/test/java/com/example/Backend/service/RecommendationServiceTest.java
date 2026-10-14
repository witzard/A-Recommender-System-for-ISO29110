package com.example.Backend.service;

import com.example.Backend.dto.RecommendRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    @Test
    void supportingTaskQueryIsScopedToSelectedProcess() {
        FusekiService fusekiService = mock(FusekiService.class);
        when(fusekiService.executeQuery(anyString())).thenReturn(List.of());
        RecommendationService service = new RecommendationService(fusekiService);

        RecommendRequest request = new RecommendRequest();
        request.setProcess("PM");
        request.setRole("Project manager");
        request.setTopic("Project Plan");
        request.setSituation("NOT_APPROVED");

        service.recommend(request);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(fusekiService, times(2)).executeQuery(queryCaptor.capture());

        String basePracticeQuery = queryCaptor.getAllValues().get(0);
        String taskQuery = queryCaptor.getAllValues().get(1);
        assertTrue(basePracticeQuery.contains("LCASE(STR(?wpName)) = LCASE(\"Project Plan\")"),
                "Base-practice retrieval should match the selected ontology label exactly");
        assertTrue(taskQuery.contains("iso:PM iso:hasActivity ?activity"),
                "Task retrieval should use the selected process");
        assertTrue(taskQuery.contains("?activity iso:hasTask ?task"),
                "Task retrieval should follow the ontology's activity-task relationship");
        assertTrue(taskQuery.contains("LCASE(STR(?wpName)) = LCASE(\"Project Plan\")"),
                "Task retrieval should match the selected ontology label exactly");
    }

    @Test
    void unsupportedProcessIsRejectedBeforeQueryExecution() {
        FusekiService fusekiService = mock(FusekiService.class);
        RecommendationService service = new RecommendationService(fusekiService);

        RecommendRequest request = new RecommendRequest();
        request.setProcess("UNKNOWN");
        request.setRole("Project manager");
        request.setTopic("Project Plan");
        request.setSituation("NOT_APPROVED");

        assertThrows(IllegalArgumentException.class, () -> service.recommend(request));
        verify(fusekiService, org.mockito.Mockito.never()).executeQuery(anyString());
    }
}
