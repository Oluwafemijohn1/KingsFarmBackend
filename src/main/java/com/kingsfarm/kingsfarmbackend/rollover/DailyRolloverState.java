package com.kingsfarm.kingsfarmbackend.rollover;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Singleton row remembering the last day the nightly rollover ran for. */
@Entity
@Table(name = "daily_rollover_state")
@Getter
@Setter
@NoArgsConstructor
public class DailyRolloverState {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "last_date", nullable = false)
    private LocalDate lastDate;

    public DailyRolloverState(LocalDate lastDate) {
        this.lastDate = lastDate;
    }
}
