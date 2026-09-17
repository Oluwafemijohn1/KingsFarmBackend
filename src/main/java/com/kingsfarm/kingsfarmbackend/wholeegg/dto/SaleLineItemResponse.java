package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;

public record SaleLineItemResponse(CatKey category, double qty, long price, double revenue) {
}
