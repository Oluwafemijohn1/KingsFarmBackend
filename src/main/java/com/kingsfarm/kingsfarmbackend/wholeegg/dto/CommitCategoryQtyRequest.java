package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Replaces the whole map at once — matches commitWeCrack/commitWeGift in store.tsx, which overwrite rather than merge. */
public record CommitCategoryQtyRequest(
        @NotEmpty @Valid List<CategoryQtyEntry> values
) {
}
