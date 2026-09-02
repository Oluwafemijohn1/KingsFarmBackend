package com.kingsfarm.kingsfarmbackend.birdstock.dto;

import com.kingsfarm.kingsfarmbackend.birdstock.Pen;

public record PenResponse(Long id, String name, boolean active) {
    public static PenResponse from(Pen pen) {
        return new PenResponse(pen.getId(), pen.getName(), pen.isActive());
    }
}
