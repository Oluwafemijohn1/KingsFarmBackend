package com.kingsfarm.kingsfarmbackend.common;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Same reasoning as ModConverter — Spring MVC's default enum binding for @RequestParam/@PathVariable doesn't know about @JsonValue/@JsonCreator. */
@Component
public class CatKeyConverter implements Converter<String, CatKey> {
    @Override
    public CatKey convert(String source) {
        return CatKey.fromWire(source);
    }
}
