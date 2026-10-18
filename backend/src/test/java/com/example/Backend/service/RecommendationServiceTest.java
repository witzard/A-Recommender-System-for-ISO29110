package com.example.Backend.service;

import com.example.Backend.dto.RecommendRequest;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class RecommendationServiceTest {
    private final FusekiService fuseki = mock(FusekiService.class);
    private final RecommendationService service = new RecommendationService(fuseki);

    private RecommendRequest request(String process, String role, String topic, String situation) {
        RecommendRequest r = new RecommendRequest();
        r.setProcess(process); r.setRole(role); r.setTopic(topic); r.setSituation(situation);
        return r;
    }

    @Test void taskQueryMustBeScopedToSelectedProcess() {
        when(fuseki.executeQuery(anyString())).thenReturn(List.of());
        service.recommend(request("PM", "Project manager", "Project Plan", "APPROVED"));
        verify(fuseki, times(2)).executeQuery(anyString());
        verify(fuseki).executeQuery(contains("?activity iso:isActivityOf iso:PM"));
    }

    @Test void responseIncludesResultsAndCounts() {
        when(fuseki.executeQuery(anyString()))
                .thenReturn(List.of(Map.of("bpID", "PM.BP.01")))
                .thenReturn(List.of(Map.of("taskID", "PM.PT.01.01")));
        Map<String, Object> result = service.recommend(
                request("PM", "Project manager", "Project Plan", "APPROVED"));
        assertEquals(1, ((Map<?, ?>) result.get("counts")).get("basePractices"));
        assertEquals(1, ((Map<?, ?>) result.get("counts")).get("supportingTasks"));
    }

    @Test void rejectsUnsupportedProcess() {
        assertThrows(IllegalArgumentException.class, () ->
                service.recommend(request("QA", "Tester", "Test Case", "APPROVED")));
        verifyNoInteractions(fuseki);
    }

    @Test void rejectsUnsupportedSituation() {
        assertThrows(IllegalArgumentException.class, () ->
                service.recommend(request("PM", "Project manager", "Project Plan", "UNKNOWN")));
        verifyNoInteractions(fuseki);
    }

    @Test void rejectsMissingRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.recommend(null));
        verifyNoInteractions(fuseki);
    }
}
