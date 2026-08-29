package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;

public record SaleLineItemResponse(CatKey category, int qty, long price, long revenue) {
}
