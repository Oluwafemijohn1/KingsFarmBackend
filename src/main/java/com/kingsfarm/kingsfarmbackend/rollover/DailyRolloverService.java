package com.kingsfarm.kingsfarmbackend.rollover;

import com.kingsfarm.kingsfarmbackend.crackegg.CrackEggService;
import com.kingsfarm.kingsfarmbackend.feedmill.FeedMillService;
import com.kingsfarm.kingsfarmbackend.mortality.MortalityService;
import com.kingsfarm.kingsfarmbackend.production.ProductionService;
import com.kingsfarm.kingsfarmbackend.wholeegg.WholeEggService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

/**
 * At the start of every day, yesterday's closing stock becomes today's
 * opening stock in every module. Bird Stock and Production's own day rows
 * already carry forward lazily; this service rolls the modules that keep a
 * single running row (Whole Egg, Crack Egg, Feed Mill, Mortality) and makes
 * sure today's Production row is created while yesterday's figures are still
 * intact. Triggered by a midnight schedule, on startup, and — as a safety net
 * if the app was down at midnight — before the first request of a new day.
 */
@Service
public class DailyRolloverService {

    private static final Logger log = LoggerFactory.getLogger(DailyRolloverService.class);

    private final DailyRolloverStateRepository stateRepository;
    private final ProductionService productionService;
    private final WholeEggService wholeEggService;
    private final CrackEggService crackEggService;
    private final FeedMillService feedMillService;
    private final MortalityService mortalityService;
    private final TransactionTemplate tx;

    private final com.kingsfarm.kingsfarmbackend.backfill.DayClosureService dayClosureService;

    private volatile LocalDate checkedDate;

    public DailyRolloverService(DailyRolloverStateRepository stateRepository,
                                ProductionService productionService,
                                WholeEggService wholeEggService,
                                CrackEggService crackEggService,
                                FeedMillService feedMillService,
                                MortalityService mortalityService,
                                com.kingsfarm.kingsfarmbackend.backfill.DayClosureService dayClosureService,
                                PlatformTransactionManager transactionManager) {
        this.dayClosureService = dayClosureService;
        this.stateRepository = stateRepository;
        this.productionService = productionService;
        this.wholeEggService = wholeEggService;
        this.crackEggService = crackEggService;
        this.feedMillService = feedMillService;
        this.mortalityService = mortalityService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "5 0 0 * * *")
    public void scheduledRollover() {
        ensureRolled();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        ensureRolled();
    }

    /** Cheap no-op after the first successful call each day. */
    public void ensureRolled() {
        LocalDate today = LocalDate.now();
        if (today.equals(checkedDate)) {
            return;
        }
        synchronized (this) {
            if (today.equals(checkedDate)) {
                return;
            }
            try {
                run(today);
                checkedDate = today;
            } catch (RuntimeException e) {
                log.error("Daily rollover failed — will retry on the next request", e);
            }
        }
    }

    /** After "Reset & Go Live": starts the day counter fresh and lays down a Production row for every day from the start date to today. */
    public void afterReset(LocalDate startDate) {
        synchronized (this) {
            stateRepository.deleteAll();
            stateRepository.save(new DailyRolloverState(LocalDate.now()));
            productionService.ensureDayRows(startDate, LocalDate.now());
            checkedDate = LocalDate.now();
        }
    }

    private void run(LocalDate today) {
        DailyRolloverState state = stateRepository.findById(DailyRolloverState.SINGLETON_ID).orElse(null);
        if (state == null) {
            stateRepository.save(new DailyRolloverState(today));
            return;
        }
        if (!state.getLastDate().isBefore(today)) {
            return;
        }
        LocalDate prev = state.getLastDate();
        // Must happen first: it reads the finished day's Whole Egg / Crack Egg figures before they are reset below.
        productionService.getTodayDayState();
        productionService.snapshotCrackUse(prev);
        tx.executeWithoutResult(status -> {
            wholeEggService.rollOver(prev);
            crackEggService.rollOver(prev);
            feedMillService.rollOver();
            mortalityService.rollOver(prev);
            state.setLastDate(today);
            stateRepository.save(state);
        });
        try {
            dayClosureService.autoCloseFinishedDay(prev);
        } catch (RuntimeException e) {
            log.warn("Automatic day closing failed for {}", prev, e);
        }
        log.info("Daily rollover complete: {} closing stock is now {} opening stock", prev, today);
    }
}
