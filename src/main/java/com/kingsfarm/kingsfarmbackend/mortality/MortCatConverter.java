package com.kingsfarm.kingsfarmbackend.mortality;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Same reasoning as ModConverter/CatKeyConverter — Spring MVC's default enum binding for @RequestParam/@PathVariable doesn't know about @JsonValue/@JsonCreator. */
@Component
public class MortCatConverter implements Converter<String, MortCat> {
    @Override
    public MortCat convert(String source) {
        return MortCat.fromWire(source);
    }
}
