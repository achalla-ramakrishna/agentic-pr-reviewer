package com.codewalnut.prreviewer.dto;

import com.codewalnut.prreviewer.domain.FindingSource;
import java.util.List;
import java.util.Map;

public record MetricsResponse(
        long totalReviews,
        long totalFindings,
        List<ReviewCountByDay> reviewsOverTime,
        PrecisionStats overallPrecision,
        Map<FindingSource, PrecisionStats> precisionBySource,
        List<CategoryCount> topCategories) {}
