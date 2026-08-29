package com.kingsfarm.kingsfarmbackend.common;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class PaymentMethodConverter implements Converter<String, PaymentMethod> {
    @Override
    public PaymentMethod convert(String source) {
        return PaymentMethod.fromWire(source);
    }
}
