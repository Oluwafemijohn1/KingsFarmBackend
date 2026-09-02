package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Same reasoning as every other wire-format Converter in this codebase — Spring MVC's default enum binding for @RequestParam/@PathVariable doesn't know about @JsonValue/@JsonCreator. */
@Component
public class FeedCollectorConverter implements Converter<String, FeedCollector> {
    @Override
    public FeedCollector convert(String source) {
        return FeedCollector.fromWire(source);
    }
}
