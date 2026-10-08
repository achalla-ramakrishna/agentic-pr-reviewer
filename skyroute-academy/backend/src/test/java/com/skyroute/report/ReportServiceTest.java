package com.skyroute.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;

/** Asserts the pipelines render to the stages we document; run them for real in the IT. */
class ReportServiceTest {

    private final ReportService service = new ReportService(mock(MongoTemplate.class));

    @Test
    void routeRevenuePipelineHasExpectedStages() {
        var pipeline = service.routeRevenueAggregation(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"))
                .toPipeline(Aggregation.DEFAULT_CONTEXT);

        assertThat(pipeline).extracting(d -> d.keySet().iterator().next())
                .containsExactly("$match", "$unwind", "$group", "$project", "$sort");
        Document group = pipeline.get(2).get("$group", Document.class);
        assertThat(group.get("_id", Document.class))
                .containsEntry("origin", "$segments.origin")
                .containsEntry("destination", "$segments.destination");
        Document project = pipeline.get(3).get("$project", Document.class);
        assertThat(project).containsEntry("origin", "$_id.origin").containsEntry("_id", 0);
    }

    @Test
    void loadFactorPipelineDividesSoldByCapacity() {
        var pipeline = service.loadFactorAggregation(LocalDate.parse("2026-11-01"))
                .toPipeline(Aggregation.DEFAULT_CONTEXT);

        assertThat(pipeline.get(0).toJson()).contains("\"departureDate\": \"2026-11-01\"");
        assertThat(pipeline.get(3).toJson()).contains("$divide", "$subtract");
    }
}
