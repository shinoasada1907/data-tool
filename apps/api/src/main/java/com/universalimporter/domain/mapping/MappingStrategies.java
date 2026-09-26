package com.universalimporter.domain.mapping;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The strategy for each mapping type. */
public final class MappingStrategies {

    private final Map<MappingType, MappingStrategy> strategies = new EnumMap<>(MappingType.class);

    private MappingStrategies(List<MappingStrategy> strategies) {
        strategies.forEach(strategy -> this.strategies.put(strategy.type(), strategy));
    }

    public static MappingStrategies standard() {
        return new MappingStrategies(List.of(new SourceColumnMappingStrategy(), new ConstantMappingStrategy()));
    }

    /** @throws IllegalStateException when no strategy handles {@code type}: a programming error, not bad input */
    public MappingStrategy strategyFor(MappingType type) {
        MappingStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalStateException("No mapping strategy for " + type);
        }
        return strategy;
    }
}
