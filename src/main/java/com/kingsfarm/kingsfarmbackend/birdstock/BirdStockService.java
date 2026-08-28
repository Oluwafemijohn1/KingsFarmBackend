package com.kingsfarm.kingsfarmbackend.birdstock;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.birdstock.dto.CreatePenRequest;
import com.kingsfarm.kingsfarmbackend.birdstock.dto.UpdateBirdPenRecordRequest;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.ConflictException;
import com.kingsfarm.kingsfarmbackend.common.exception.ForbiddenException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.openingstock.OpeningStockLockService;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Backs BirdStockView. A pen's row for "today" is found-or-created on read
 * (see {@link #getTodayRecords}), with Opening carried forward from
 * yesterday's Closing the moment a new day's row is first touched — matching
 * the frontend's "Opening Stock auto-filled from yesterday's Closing Stock"
 * copy exactly. Editing is only ever allowed on today's row; once the date
 * rolls over, the row is permanently locked (same principle as Opening
 * Stock, applied to the whole record — see BACKEND_PLAN.md's Phase 2 note on
 * collapsing the frontend's save/unlock toggle into this simpler rule).
 */
@Service
public class BirdStockService {

    private final PenRepository penRepository;
    private final BirdPenRecordRepository recordRepository;
    private final OpeningStockLockService lockService;

    public BirdStockService(PenRepository penRepository, BirdPenRecordRepository recordRepository, OpeningStockLockService lockService) {
        this.penRepository = penRepository;
        this.recordRepository = recordRepository;
        this.lockService = lockService;
    }

    @Transactional(readOnly = true)
    public List<Pen> listPens(boolean includeInactive) {
        return includeInactive ? penRepository.findAllByOrderByNameAsc() : penRepository.findAllByActiveTrueOrderByNameAsc();
    }

    @Audited(module = Mod.BIRD_STOCK, action = "Add Pen", type = LogType.AUDIT,
            detail = "'New pen: ' + #request.name() + ' (opening ' + #request.opening() + ')'")
    @Transactional
    public Pen addPen(CreatePenRequest request, String username) {
        if (penRepository.existsByName(request.name())) {
            throw new ConflictException("A pen with that name already exists.");
        }
        Pen pen = penRepository.save(Pen.builder().name(request.name()).active(true).createdBy(username).build());

        // The manager's hand-typed initial value — the one-and-only free entry
        // before Opening Stock locking kicks in for this pen (see class javadoc).
        BirdPenRecord record = BirdPenRecord.builder()
                .pen(pen)
                .entryDate(LocalDate.now())
                .opening(request.opening())
                .enteredBy(username)
                .build();
        recordRepository.save(record);
        return pen;
    }

    @Audited(module = Mod.BIRD_STOCK, action = "Remove Pen", type = LogType.AUDIT, detail = "'Pen #' + #penId + ' deactivated'")
    @Transactional
    public void deactivatePen(Long penId) {
        Pen pen = penRepository.findById(penId).orElseThrow(() -> new NotFoundException("Pen not found."));
        pen.setActive(false);
        penRepository.save(pen);
    }

    /** Finds-or-creates every active pen's row for today, carrying Opening forward from yesterday's Closing on first touch. */
    @Transactional
    public List<BirdPenRecord> getTodayRecords() {
        LocalDate today = LocalDate.now();
        return listPens(false).stream()
                .map(pen -> findOrCreateTodayRecord(pen, today))
                .toList();
    }

    private BirdPenRecord findOrCreateTodayRecord(Pen pen, LocalDate today) {
        return recordRepository.findByPenAndEntryDate(pen, today)
                .orElseGet(() -> {
                    int carriedOpening = recordRepository.findFirstByPenAndEntryDateLessThanOrderByEntryDateDesc(pen, today)
                            .map(BirdPenRecord::closing)
                            .orElse(0);
                    BirdPenRecord created = BirdPenRecord.builder()
                            .pen(pen)
                            .entryDate(today)
                            .opening(carriedOpening)
                            .build();
                    return recordRepository.save(created);
                });
    }

    @Audited(module = Mod.BIRD_STOCK, action = "Save Record", detail = "'Pen #' + #penId + ' updated for ' + T(java.time.LocalDate).now()")
    @Transactional
    public BirdPenRecord updateRecord(Long penId, UpdateBirdPenRecordRequest request, String username) {
        Pen pen = penRepository.findById(penId).orElseThrow(() -> new NotFoundException("Pen not found."));
        LocalDate today = LocalDate.now();
        BirdPenRecord record = findOrCreateTodayRecord(pen, today);

        if (!record.getEntryDate().isEqual(today)) {
            throw new ForbiddenException("This record was saved on a previous day and can no longer be edited.");
        }

        if (request.opening() != null && request.opening() != record.getOpening()) {
            if (lockService.isLocked(Mod.BIRD_STOCK, pen.getName())) {
                throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
            }
            record.setOpening(request.opening());
        }
        if (request.mortality() != null) {
            record.setMortality(request.mortality());
        }
        if (request.birdSales() != null) {
            record.setBirdSales(request.birdSales());
        }
        if (request.restocking() != null) {
            record.setRestocking(request.restocking());
        }
        if (request.remarks() != null) {
            record.setRemarks(request.remarks());
        }

        if (record.getMortality() > record.getOpening()) {
            throw new BadRequestException("Mortality (" + record.getMortality() + ") exceeds Opening Stock (" + record.getOpening() + ").");
        }
        if (record.closing() < 0) {
            throw new BadRequestException("Closing Stock cannot be negative.");
        }

        if (record.getEnteredBy() == null) {
            record.setEnteredBy(username);
        } else {
            record.setUpdatedBy(username);
        }
        return recordRepository.save(record);
    }

    /** The manager's self-service "Done — Lock" action once they've finished correcting an approved-unlocked Opening Stock field. */
    @Transactional
    public void lockOpening(Long penId) {
        Pen pen = penRepository.findById(penId).orElseThrow(() -> new NotFoundException("Pen not found."));
        lockService.lock(Mod.BIRD_STOCK, pen.getName());
    }

    @Transactional(readOnly = true)
    public boolean isEditable(BirdPenRecord record) {
        return record.getEntryDate().isEqual(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public boolean isOpeningLocked(Pen pen) {
        return lockService.isLocked(Mod.BIRD_STOCK, pen.getName());
    }

    @Transactional(readOnly = true)
    public Page<BirdPenRecord> history(Long penId, Pageable pageable) {
        if (penId != null) {
            Pen pen = penRepository.findById(penId).orElseThrow(() -> new NotFoundException("Pen not found."));
            return recordRepository.findAllByPenOrderByEntryDateDesc(pen, pageable);
        }
        return recordRepository.findAllByOrderByEntryDateDesc(pageable);
    }

    /** Sum of every active pen's Closing today — what Production reads for its Bird Stock → Production auto-transfer (BACKEND_PLAN.md §6). */
    @Transactional(readOnly = true)
    public int totalClosingToday() {
        return getTodayRecords().stream().mapToInt(BirdPenRecord::closing).sum();
    }
}
