package com.kingsfarm.kingsfarmbackend.common;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Without this, Spring MVC's default enum conversion for @RequestParam /
 * @PathVariable falls back to Enum.valueOf() against the constant name
 * (e.g. "WHOLE_EGG"), which rejects the frontend's wire form ("whole-egg")
 * that @JsonValue/@JsonCreator on Mod only cover for JSON request bodies.
 * This registers the same wire-string mapping for query/path parameters.
 */
@Component
public class ModConverter implements Converter<String, Mod> {
    @Override
    public Mod convert(String source) {
        return Mod.fromWire(source);
    }
}
