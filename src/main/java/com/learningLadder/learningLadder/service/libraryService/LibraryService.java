package com.learningLadder.learningLadder.service.libraryService;

import com.learningLadder.learningLadder.config.backgroundJob.FireAndForgetEmailAsync.FireAndForgetEmailAsync;
import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.dto.libraryLoanDto.*;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.library.Library;
import com.learningLadder.learningLadder.entity.library.LibraryLoan;
import com.learningLadder.learningLadder.entity.studentProfile.StudentProfile;
import com.learningLadder.learningLadder.enums.loanStatus.LoanStatus;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.fileStorageException.FileStorageException;
import com.learningLadder.learningLadder.exception.notFoundRequestException.NotFoundRequestException;
import com.learningLadder.learningLadder.mappers.libraryMapper.LibraryMapper;
import com.learningLadder.learningLadder.repository.courseRepository.CourseRepository;
import com.learningLadder.learningLadder.repository.libraryRepository.LibraryLoanRepository;
import com.learningLadder.learningLadder.repository.libraryRepository.LibraryLoanSpecification;
import com.learningLadder.learningLadder.repository.libraryRepository.LibraryRepository;
import com.learningLadder.learningLadder.repository.libraryRepository.LibrarySpecification;
import com.learningLadder.learningLadder.repository.studentProfileRepository.StudentProfileRepository;
import com.learningLadder.learningLadder.utils.pdf.LibraryLoanPdfGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class LibraryService {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CacheEvict(cacheNames = {
            CacheNames.LIBRARY_BOOK, CacheNames.LIBRARY_BOOKS, CacheNames.LIBRARY_BOOK_SEARCH,
            CacheNames.LIBRARY_BOOK_COUNT, CacheNames.LIBRARY_LOAN, CacheNames.LIBRARY_LOANS,
            CacheNames.LIBRARY_LOAN_SEARCH, CacheNames.LIBRARY_LOAN_COUNT, CacheNames.LIBRARY_LOAN_PDF
    }, allEntries = true)
    public @interface EvictLibraryCaches {
    }

    private static final List<String> ALL_LIBRARY_CACHES = List.of(
            CacheNames.LIBRARY_BOOK, CacheNames.LIBRARY_BOOKS, CacheNames.LIBRARY_BOOK_SEARCH,
            CacheNames.LIBRARY_BOOK_COUNT, CacheNames.LIBRARY_LOAN, CacheNames.LIBRARY_LOANS,
            CacheNames.LIBRARY_LOAN_SEARCH, CacheNames.LIBRARY_LOAN_COUNT, CacheNames.LIBRARY_LOAN_PDF);


    private static final List<LoanStatus> ACTIVE_LOAN_STATUSES = List.of(
            LoanStatus.RESERVED, LoanStatus.BORROWED, LoanStatus.OVERDUE, LoanStatus.RETURN_REQUESTED);
    private static final List<LoanStatus> OUT_WITH_STUDENT = List.of(
            LoanStatus.BORROWED, LoanStatus.OVERDUE, LoanStatus.RETURN_REQUESTED);
    private static final int MAX_ACTIVE_LOANS_PER_STUDENT = 5;
    private static final Duration RESERVATION_HOLD = Duration.ofHours(48);
    private static final int NOTES_MAX_LENGTH = 1000;

    // ---------------------------------------------------------------- limits
    private static final long MAX_COVER_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 2_000;
    private static final DateTimeFormatter NOTE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final LibraryRepository libraryRepository;
    private final LibraryLoanRepository libraryLoanRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CourseRepository courseRepository;
    private final LibraryMapper libraryMapper;
    private final LibraryLoanPdfGenerator libraryLoanPdfGenerator;
    private final MinioFileStorageService minioFileStorageService;
    private final FireAndForgetEmailAsync fireAndForgetEmailAsync;
    private final CacheManager cacheManager;

    private record CoverUpload(String objectKey, byte[] content, String contentType) {
    }

    @EvictLibraryCaches
    public LibraryBookResponseDto addBook(CreateLibraryBookRequestDto dto, MultipartFile coverImage) {
        if (dto.isbn() != null && libraryRepository.existsByIsbn(dto.isbn())) {
            throw new ConflictRequestException("A book with ISBN " + dto.isbn() + " already exists in the catalog");
        }

        CoverUpload cover = prepareCover(coverImage);

        Library book = libraryMapper.toEntity(dto);
        if (dto.courseId() != null) {
            book.setCourse(findCourse(dto.courseId()));
        }
        book.setCoverImage(cover == null ? null : cover.objectKey());

        Library saved;
        try {
            saved = libraryRepository.saveAndFlush(book);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictRequestException("A book with this ISBN already exists in the catalog");
        }

        if (cover != null) {
            uploadCoverInBackground(cover, null);
        }
        log.info("Book {} \"{}\" added with {} copies", saved.getId(), saved.getTitle(), saved.getTotalCopies());
        return libraryMapper.toBookResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.LIBRARY_BOOKS, key = "#page + ':' + #size")
    public LibraryBookPageResponseDto fetchAllBooks(int page, int size) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.ASC, "title"));
        return toBookPage(libraryRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.LIBRARY_BOOK, key = "#id")
    public LibraryBookResponseDto fetchBook(UUID id) {
        return libraryMapper.toBookResponse(findBook(id));
    }

    @EvictLibraryCaches
    public LibraryBookResponseDto updateBook(UUID id, UpdateLibraryBookRequestDto dto, MultipartFile coverImage) {
        Library book = findBook(id);

        if (dto.isbn() != null && !dto.isbn().equals(book.getIsbn())
                && libraryRepository.existsByIsbnAndIdNot(dto.isbn(), id)) {
            throw new ConflictRequestException("Another book already uses ISBN " + dto.isbn());
        }

        CoverUpload cover = prepareCover(coverImage);

        libraryMapper.updateEntity(dto, book);

        if (dto.courseId() != null) {
            book.setCourse(findCourse(dto.courseId()));
        }

        if (dto.totalCopies() != null) {
            int onLoan = book.getTotalCopies() - book.getAvailableCopies();
            if (dto.totalCopies() < onLoan) {
                throw new ConflictRequestException("Total copies cannot be lower than the " + onLoan + " copies currently on loan");
            }
            book.setAvailableCopies(dto.totalCopies() - onLoan);
            book.setTotalCopies(dto.totalCopies());
        }

        String previousCover = null;
        if (cover != null) {
            previousCover = book.getCoverImage();
            book.setCoverImage(cover.objectKey());
        }

        Library saved;
        try {
            saved = libraryRepository.saveAndFlush(book);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictRequestException("Another book already uses this ISBN");
        } catch (ConflictRequestException ex) {
            throw new ConflictRequestException("This book was changed at the same time (e.g. a copy was just borrowed). Please try again.");
        }

        if (cover != null) {
            uploadCoverInBackground(cover, previousCover);
        }
        log.info("Book {} updated", id);
        return libraryMapper.toBookResponse(saved);
    }

    @EvictLibraryCaches
    public void deleteBook(UUID id) {
        Library book = findBook(id);
        if (libraryLoanRepository.existsByBook_Id(id)) {
            throw new ConflictRequestException("This book has loan history and cannot be deleted. "
                    + "Withdraw it instead by updating it with active = false.");
        }
        String cover = book.getCoverImage();
        libraryRepository.delete(book);

        if (cover != null) {
            fireAndForgetEmailAsync.fire("delete book cover " + cover, () -> deleteFromMinio(cover));
        }
        log.info("Book {} \"{}\" deleted", id, book.getTitle());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.LIBRARY_BOOK_SEARCH, key = "#query")
    public LibraryBookPageResponseDto searchBooks(PagedQueryLibraryBookRequestDto query) {
        return toBookPage(libraryRepository.findAll(LibrarySpecification.fromQuery(query), query.toPageable()));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.LIBRARY_BOOK_COUNT, key = "'total'")
    public long countTotalNumberOfBooks() {
        return libraryRepository.count();
    }

    @EvictLibraryCaches
    public LibraryLoanResponseDto borrowBook(BorrowBookRequestDto dto, String idempotencyKey, UUID currentUserId) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BadRequestException("The Idempotency-Key header is required (max 100 characters)");
        }
        StudentProfile student = findStudentProfileOfUser(currentUserId);

        Optional<LibraryLoan> previous = libraryLoanRepository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            LibraryLoan existing = previous.get();
            if (!existing.getStudent().getId().equals(student.getId())) {
                throw new ConflictRequestException("This Idempotency-Key has already been used");
            }
            log.info("Idempotent replay of loan {} for key {}", existing.getId(), idempotencyKey);
            return libraryMapper.toLoanResponse(existing);
        }

        Library book = findBook(dto.bookId());
        if (!book.isActive() || !book.isBorrowable()) {
            throw new ConflictRequestException("\"" + book.getTitle() + "\" cannot be borrowed (reference only or withdrawn)");
        }
        if (book.getAvailableCopies() <= 0) {
            throw new ConflictRequestException("No copies of \"" + book.getTitle() + "\" are available right now");
        }
        if (libraryLoanRepository.existsByStudent_IdAndBook_IdAndStatusIn(
                student.getId(), book.getId(), ACTIVE_LOAN_STATUSES)) {
            throw new ConflictRequestException("You already have this book reserved or borrowed");
        }
        if (libraryLoanRepository.countByStudent_IdAndStatusIn(student.getId(), ACTIVE_LOAN_STATUSES)
                >= MAX_ACTIVE_LOANS_PER_STUDENT) {
            throw new ConflictRequestException("You can have at most " + MAX_ACTIVE_LOANS_PER_STUDENT
                    + " books at a time. Please return a book first.");
        }
        if (libraryLoanRepository.existsByStudent_IdAndFeePaidFalseAndLateFeeGreaterThan(student.getId(), BigDecimal.ZERO)) {
            throw new ConflictRequestException("Please pay your outstanding library fees before borrowing another book");
        }

        book.checkOut();

        LibraryLoan loan = libraryMapper.toEntity(dto);
        loan.setBook(book);
        loan.setStudent(student);
        loan.setIdempotencyKey(idempotencyKey);
        loan.setFeeCurrency(book.getCurrency());
        loan.setReservationExpiresAt(Instant.now().plus(RESERVATION_HOLD));

        LibraryLoan saved;
        try {
            saved = libraryLoanRepository.saveAndFlush(loan);
        } catch (ConflictRequestException | BadRequestException ex) {
            throw new ConflictRequestException("This book was just reserved by someone else, or you already have it. Please try again.");
        }

        log.info("Loan {} ({}) reserved: book {} for student {}",
                saved.getId(), saved.getLoanReference(), book.getId(), student.getId());
        return libraryMapper.toLoanResponse(saved);
    }

    @EvictLibraryCaches
    public LibraryLoanResponseDto issueBook(UUID loanId, IssueBookRequestDto dto, UUID librarianId) {
        LibraryLoan loan = findLoan(loanId);

        if (loan.getStatus() != LoanStatus.RESERVED) {
            throw new ConflictRequestException("Only reserved books can be issued. This loan is " + loan.getStatus());
        }
        Instant now = Instant.now();
        if (loan.getReservationExpiresAt() != null && now.isAfter(loan.getReservationExpiresAt())) {
            throw new ConflictRequestException("This reservation expired on " + loan.getReservationExpiresAt()
                    + ". The student needs to reserve the book again.");
        }

        libraryMapper.applyIssue(dto, loan);
        loan.setBorrowedAt(now);
        if (dto.dueAt() == null) {
            loan.setDueAt(now.plus(Duration.ofDays(loan.getBook().getLoanPeriodDays())));
        }
        loan.setIssuedBy(librarianId);
        loan.setStatus(LoanStatus.BORROWED);
        appendNote(loan, dto.notes());

        log.info("Loan {} issued by librarian {}, due {}", loanId, librarianId, loan.getDueAt());
        return saveLoan(loan);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.LIBRARY_LOANS, key = "#page + ':' + #size")
    public LibraryLoanPageResponseDto fetchAllBorrowedBooks(int page, int size) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        return toLoanPage(libraryLoanRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.LIBRARY_LOAN, key = "#id")
    public LibraryLoanResponseDto fetchBorrowedBook(UUID id) {
        return libraryMapper.toLoanResponse(findLoan(id));
    }

    @EvictLibraryCaches
    public LibraryLoanResponseDto updateBorrowedBook(UUID loanId, IssueBookRequestDto dto, UUID librarianId) {
        LibraryLoan loan = findLoan(loanId);

        if (loan.getStatus() != LoanStatus.BORROWED && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new ConflictRequestException("Only borrowed or overdue loans can be updated. This loan is " + loan.getStatus());
        }

        Instant previousDue = loan.getDueAt();
        libraryMapper.applyIssue(dto, loan);
        appendNote(loan, dto.notes());

        if (dto.dueAt() != null && !dto.dueAt().equals(previousDue)) {
            loan.setDueReminderSent(false);
            loan.setDueReminderSentAt(null);
            if (loan.getStatus() == LoanStatus.OVERDUE && dto.dueAt().isAfter(Instant.now())) {
                loan.setStatus(LoanStatus.BORROWED);
            }
        }

        log.info("Loan {} updated by librarian {}", loanId, librarianId);
        return saveLoan(loan);
    }

    @EvictLibraryCaches
    public LibraryLoanResponseDto renewBorrowedBook(UUID loanId, UUID currentUserId) {
        LibraryLoan loan = findLoan(loanId);
        ensureOwner(loan, findStudentProfileOfUser(currentUserId));

        if (loan.getStatus() == LoanStatus.OVERDUE || loan.isOverdue()) {
            throw new ConflictRequestException("Overdue books cannot be renewed. Please return the book to the library.");
        }
        if (loan.getStatus() != LoanStatus.BORROWED) {
            throw new ConflictRequestException("Only borrowed books can be renewed. This loan is " + loan.getStatus());
        }
        Library book = loan.getBook();
        if (loan.getRenewalCount() >= book.getMaxRenewals()) {
            throw new ConflictRequestException("This loan has already been renewed the maximum of " + book.getMaxRenewals() + " time(s)");
        }

        loan.setDueAt(loan.getDueAt().plus(Duration.ofDays(book.getLoanPeriodDays())));
        loan.setRenewalCount(loan.getRenewalCount() + 1);
        loan.setLastRenewedAt(Instant.now());
        loan.setDueReminderSent(false);
        loan.setDueReminderSentAt(null);

        log.info("Loan {} renewed ({}/{}), new due date {}", loanId, loan.getRenewalCount(), book.getMaxRenewals(), loan.getDueAt());
        return saveLoan(loan);
    }

    @EvictLibraryCaches
    public LibraryLoanResponseDto returnBorrowedBook(UUID loanId, ReturnBookToLibraryRequestDto dto, UUID currentUserId) {
        LibraryLoan loan = findLoan(loanId);
        ensureOwner(loan, findStudentProfileOfUser(currentUserId));

        if (loan.getStatus() != LoanStatus.BORROWED && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new ConflictRequestException("Only borrowed or overdue books can be returned. This loan is " + loan.getStatus());
        }

        libraryMapper.applyReturnRequest(dto, loan);
        loan.setReturnRequestedAt(Instant.now());
        loan.setStatus(LoanStatus.RETURN_REQUESTED);

        log.info("Return requested for loan {}", loanId);
        return saveLoan(loan);
    }

    @EvictLibraryCaches
    public LibraryLoanResponseDto confirmReturnBorrowedBook(UUID loanId, ConfirmBookReturnRequestDto dto, UUID librarianId) {
        LibraryLoan loan = findLoan(loanId);

        if (!OUT_WITH_STUDENT.contains(loan.getStatus())) {
            throw new ConflictRequestException("Only books that are out can be confirmed as returned. This loan is " + loan.getStatus());
        }

        Instant now = Instant.now();
        libraryMapper.applyReturnConfirmation(dto, loan);
        if (loan.getReturnedAt() == null) {
            loan.setReturnedAt(now);
        }
        if (loan.getBorrowedAt() != null && loan.getReturnedAt().isBefore(loan.getBorrowedAt())) {
            throw new ConflictRequestException("Return date cannot be before the borrow date (" + loan.getBorrowedAt() + ")");
        }

        BigDecimal fee;
        if (dto.waiveLateFee()) {
            fee = BigDecimal.ZERO;
        } else if (dto.lateFeeOverride() != null) {
            fee = dto.lateFeeOverride();
        } else {
            fee = calculateLateFee(loan, loan.getReturnedAt());
        }
        applyFee(loan, fee, dto.feePaid(), now);

        loan.setReceivedBy(librarianId);
        loan.setStatus(LoanStatus.RETURNED);
        appendNote(loan, dto.notes());
        putCopyBackOnShelf(loan.getBook());

        log.info("Loan {} returned, confirmed by librarian {}, late fee {} {}",
                loanId, librarianId, loan.getLateFee(), loan.getFeeCurrency());
        return saveLoan(loan);
    }

    @EvictLibraryCaches
    public LibraryLoanResponseDto cancelReservation(UUID loanId, UUID currentUserId, boolean actingAsStaff) {
        LibraryLoan loan = findLoan(loanId);
        if (!actingAsStaff) {
            ensureOwner(loan, findStudentProfileOfUser(currentUserId));
        }
        if (loan.getStatus() != LoanStatus.RESERVED) {
            throw new ConflictRequestException("Only reservations can be cancelled. This loan is " + loan.getStatus());
        }

        loan.setStatus(LoanStatus.CANCELLED);
        appendNote(loan, "Reservation cancelled" + (actingAsStaff ? " by library staff" : " by the student"));
        putCopyBackOnShelf(loan.getBook());

        log.info("Reservation {} cancelled", loanId);
        return saveLoan(loan);
    }

    @EvictLibraryCaches
    public LibraryLoanResponseDto reportBookLost(UUID loanId, ReportBookLostRequestDto dto,
                                                 UUID currentUserId, boolean actingAsStaff) {
        LibraryLoan loan = findLoan(loanId);
        if (!actingAsStaff) {
            ensureOwner(loan, findStudentProfileOfUser(currentUserId));
        }
        if (!OUT_WITH_STUDENT.contains(loan.getStatus())) {
            throw new ConflictRequestException("Only books that are out can be reported lost. This loan is " + loan.getStatus());
        }

        Instant now = Instant.now();
        BigDecimal fee = calculateLateFee(loan, now);
        if (actingAsStaff && dto.replacementFee() != null) {
            fee = fee.add(dto.replacementFee());
        }
        applyFee(loan, fee, false, now);

        loan.setStatus(LoanStatus.LOST);
        appendNote(loan, "Reported lost: " + dto.description());

        Library book = loan.getBook();
        book.setTotalCopies(Math.max(book.getTotalCopies() - 1, book.getAvailableCopies()));

        log.warn("Loan {} reported lost, book {} now has {} copies", loanId, book.getId(), book.getTotalCopies());
        return saveLoan(loan);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.LIBRARY_LOAN_SEARCH, key = "#query")
    public LibraryLoanPageResponseDto searchBorrowedBooks(PagedQueryLibraryLoanRequestDto query) {
        return toLoanPage(libraryLoanRepository.findAll(LibraryLoanSpecification.fromQuery(query), query.toPageable()));
    }

    @EvictLibraryCaches
    public void deleteBorrowedBook(UUID loanId) {
        LibraryLoan loan = findLoan(loanId);
        if (loan.getStatus().isActive()) {
            throw new ConflictRequestException("Active loans cannot be deleted (" + loan.getStatus()
                    + "). Cancel the reservation or confirm the return first.");
        }
        libraryLoanRepository.delete(loan);
        log.info("Loan {} ({}) deleted", loanId, loan.getLoanReference());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.LIBRARY_LOAN_PDF, key = "#query")
    public byte[] downloadBorrowedBooks(PagedQueryLibraryLoanRequestDto query) {
        Sort sort = query.toPageable().getSort();
        Page<LibraryLoan> result = libraryLoanRepository.findAll(
                LibraryLoanSpecification.fromQuery(query),
                PageRequest.of(0, MAX_EXPORT_ROWS, sort));

        List<LibraryLoanResponseDto> rows = libraryMapper.toLoanResponseList(result.getContent());

        log.info("Generating library loans PDF with {} of {} rows", rows.size(), result.getTotalElements());
        return libraryLoanPdfGenerator.generate(rows, result.getTotalElements(), describeLoanFilters(query));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.LIBRARY_LOAN_COUNT, key = "'out'")
    public long countTotalNumberOfBorrowedBooks() {
        return libraryLoanRepository.countByStatusIn(OUT_WITH_STUDENT);
    }

    /** Every hour: BORROWED loans past their due date become OVERDUE. */
    @Scheduled(cron = "0 0 * * * *")
    public void markOverdueLoans() {
        int updated = libraryLoanRepository.markBorrowedAsOverdue(LoanStatus.BORROWED, LoanStatus.OVERDUE, Instant.now());
        if (updated > 0) {
            log.info("Marked {} library loan(s) as OVERDUE", updated);
            evictAllLibraryCaches();
        }
    }

    /** Every 15 minutes: reservations not picked up in time are cancelled and the copy released. */
    @Scheduled(cron = "0 */15 * * * *")
    public void releaseExpiredReservations() {
        List<LibraryLoan> expired = libraryLoanRepository
                .findByStatusAndReservationExpiresAtBefore(LoanStatus.RESERVED, Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        for (LibraryLoan loan : expired) {
            loan.setStatus(LoanStatus.CANCELLED);
            appendNote(loan, "Reservation expired: not picked up within " + RESERVATION_HOLD.toHours() + " hours");
            putCopyBackOnShelf(loan.getBook());
        }
        log.info("Released {} expired library reservation(s)", expired.size());
        evictAllLibraryCaches();
    }

    private BigDecimal calculateLateFee(LibraryLoan loan, Instant returnedAt) {
        Instant dueAt = loan.getDueAt();
        if (dueAt == null) {
            return BigDecimal.ZERO;
        }
        Instant end = (loan.getReturnRequestedAt() != null && loan.getReturnRequestedAt().isBefore(returnedAt))
                ? loan.getReturnRequestedAt()
                : returnedAt;
        if (!end.isAfter(dueAt)) {
            return BigDecimal.ZERO;
        }
        long minutesLate = Duration.between(dueAt, end).toMinutes();
        long daysLate = (minutesLate + 1439) / 1440;
        return loan.getBook().getDailyLateFee().multiply(BigDecimal.valueOf(daysLate));
    }

    private void applyFee(LibraryLoan loan, BigDecimal fee, boolean paidNow, Instant now) {
        BigDecimal amount = fee.setScale(2, RoundingMode.HALF_UP);
        loan.setLateFee(amount);
        boolean nothingOwed = amount.signum() == 0;
        loan.setFeePaid(nothingOwed || paidNow);
        loan.setFeePaidAt(!nothingOwed && paidNow ? now : null);
    }

    private void putCopyBackOnShelf(Library book) {
        if (book.getAvailableCopies() < book.getTotalCopies()) {
            book.checkIn();
        } else {
            log.warn("Book {} already has all {} copies on the shelf; not incrementing", book.getId(), book.getTotalCopies());
        }
    }

    private void appendNote(LibraryLoan loan, String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        String entry = "[" + NOTE_DATE.format(Instant.now()) + "] " + note.trim();
        String combined = loan.getNotes() == null ? entry : loan.getNotes() + "\n" + entry;
        if (combined.length() > NOTES_MAX_LENGTH) {
            combined = combined.substring(combined.length() - NOTES_MAX_LENGTH);
        }
        loan.setNotes(combined);
    }

    private LibraryLoanResponseDto saveLoan(LibraryLoan loan) {
        try {
            return libraryMapper.toLoanResponse(libraryLoanRepository.saveAndFlush(loan));
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictRequestException("This loan was changed by someone else at the same time. Please reload and try again.");
        }
    }

    private void ensureOwner(LibraryLoan loan, StudentProfile student) {
        if (!loan.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own loans");
        }
    }

    private void evictAllLibraryCaches() {
        ALL_LIBRARY_CACHES.forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    private CoverUpload prepareCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_COVER_BYTES) {
            throw new BadRequestException("Cover image must not exceed 5 MB");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : ALLOWED_IMAGE_TYPES.get(contentType.toLowerCase());
        if (extension == null) {
            throw new BadRequestException("Cover image must be a JPEG, PNG or WEBP image");
        }
        try {
            String objectKey = "library/covers/" + UUID.randomUUID() + "." + extension;
            return new CoverUpload(objectKey, file.getBytes(), contentType);
        } catch (IOException ex) {
            throw new BadRequestException("Could not read the cover image");
        }
    }

    /** After commit, on a background thread with retries; a failure never affects the API response. */
    private void uploadCoverInBackground(CoverUpload cover, String previousObjectKey) {
        fireAndForgetEmailAsync.fire("upload book cover " + cover.objectKey(), () -> {
            uploadToMinio(cover);
            log.info("Book cover uploaded to MinIO: {}", cover.objectKey());
            if (previousObjectKey != null) {
                try {
                    deleteFromMinio(previousObjectKey);
                } catch (Exception ex) {
                    log.warn("Could not delete old book cover {}: {}", previousObjectKey, ex.getMessage());
                }
            }
        });
    }

    private void uploadToMinio(CoverUpload cover) {
        try {
            minioFileStorageService.uploadFile(
                    cover.objectKey(),
                    new ByteArrayInputStream(cover.content()),
                    cover.content().length,
                    cover.contentType());
        } catch (Exception ex) {
            throw new FileStorageException("MinIO upload failed for " + cover.objectKey(), ex);
        }
    }

    private void deleteFromMinio(String objectKey) {
        try {
            minioFileStorageService.deleteFile(objectKey);
        } catch (Exception ex) {
            throw new FileStorageException("MinIO delete failed for " + objectKey, ex);
        }
    }

    private Library findBook(UUID id) {
        return libraryRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Book not found with id " + id));
    }

    private LibraryLoan findLoan(UUID id) {
        return libraryLoanRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Loan not found with id " + id));
    }

    private Course findCourse(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Course not found with id " + id));
    }

    private StudentProfile findStudentProfileOfUser(UUID userId) {
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only students with a student profile can borrow books"));
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safeSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }

    private LibraryBookPageResponseDto toBookPage(Page<Library> page) {
        return new LibraryBookPageResponseDto(
                libraryMapper.toBookResponseList(page.getContent()),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private LibraryLoanPageResponseDto toLoanPage(Page<LibraryLoan> page) {
        return new LibraryLoanPageResponseDto(
                libraryMapper.toLoanResponseList(page.getContent()),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private String describeLoanFilters(PagedQueryLibraryLoanRequestDto query) {
        List<String> parts = new ArrayList<>();
        if (query.bookId() != null) parts.add("book " + query.bookId());
        if (query.studentId() != null) parts.add("student " + query.studentId());
        if (query.status() != null) parts.add("status " + query.status());
        if (Boolean.TRUE.equals(query.overdueOnly())) parts.add("overdue only");
        if (Boolean.TRUE.equals(query.unpaidFeesOnly())) parts.add("unpaid fees only");
        if (query.keyword() != null) parts.add("keyword \"" + query.keyword() + "\"");
        if (query.dueFrom() != null) parts.add("due from " + query.dueFrom());
        if (query.dueTo() != null) parts.add("due to " + query.dueTo());
        parts.add("sorted by " + query.sortBy() + " " + query.sortDirection().toUpperCase());
        return String.join("  |  ", parts);
    }
}