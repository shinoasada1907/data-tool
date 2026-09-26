package com.universalimporter.api.importsession;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.Readiness;

import java.util.List;

/** Whether the session can be processed; {@code ready} is true exactly when {@code issues} is empty. */
public record ReadinessDto(boolean ready, List<ProblemItem> issues) {

    public static ReadinessDto from(Readiness readiness) {
        return new ReadinessDto(readiness.ready(), readiness.issues());
    }
}
