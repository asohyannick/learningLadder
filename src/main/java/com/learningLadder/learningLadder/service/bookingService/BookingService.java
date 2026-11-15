package com.learningLadder.learningLadder.service.bookingService;

import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.dto.bookingDto.*;
import com.learningLadder.learningLadder.entity.booking.Booking;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.tutorProfile.TutorProfile;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.bookingStatus.BookingStatus;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import com.learningLadder.learningLadder.enums.userRole.UserRole;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.notFoundRequestException.NotFoundRequestException;
import com.learningLadder.learningLadder.mappers.bookingMapper.BookingMapper;
import com.learningLadder.learningLadder.repository.bookingRepository.BookingRepository;
import com.learningLadder.learningLadder.repository.bookingRepository.BookingSpecification;
import com.learningLadder.learningLadder.repository.courseRepository.CourseRepository;
import com.learningLadder.learningLadder.repository.tutorProfileRepository.TutorProfileRepository;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import com.learningLadder.learningLadder.utils.pdf.BookingPdfGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);
    private static final UUID NO_BOOKING = new UUID(0L, 0L);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 2_000;

    private final BookingRepository bookingRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final CourseRepository courseRepository;
    private final BookingMapper bookingMapper;
    private final BookingPdfGenerator bookingPdfGenerator;
    private final UserRepository userRepository;

    @CacheEvict(cacheNames = {CacheNames.BOOKINGS, CacheNames.BOOKING_SEARCH,
            CacheNames.BOOKING_COUNT, CacheNames.BOOKING_PDF}, allEntries = true)
    public BookingResponseDto addBooking(CreateBookingRequestDto dto, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BadRequestException("The Idempotency-Key header is required (max 100 characters)");
        }

        User student = currentStudent();

        Optional<Booking> previous = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            Booking existing = previous.get();
            if (!existing.getStudent().getId().equals(student.getId())) {
                throw new ConflictRequestException("This Idempotency-Key has already been used");
            }
            log.info("Idempotent replay of booking {} for key {}", existing.getId(), idempotencyKey);
            return bookingMapper.toResponse(existing);
        }

        TutorProfile tutor = findTutor(dto.tutorId());
        ensureLocationProvided(dto.sessionMode(), dto.locationName(), dto.addressLine());
        ensureSlotIsFree(tutor.getId(), student.getId(), dto.scheduledStart(), dto.scheduledEnd(), NO_BOOKING);

        Booking booking = bookingMapper.toEntity(dto);
        booking.setStudent(student);
        booking.setTutor(tutor);
        if (dto.courseId() != null) {
            booking.setCourse(findCourse(dto.courseId()));
        }
        booking.setIdempotencyKey(idempotencyKey.trim());
        applyPricing(booking, tutor);

        Booking saved;
        try {
            saved = bookingRepository.saveAndFlush(booking);
        } catch (BadRequestException ex) {
            throw translateIntegrityError(ex);
        }

        log.info("Booking {} ({}) created by student {} with tutor {}",
                saved.getId(), saved.getBookingReference(), student.getId(), tutor.getId());
        return bookingMapper.toResponse(saved);
    }


    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.BOOKINGS, key = "#page + ':' + #size")
    public BookingPageResponseDto fetchBookings(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "scheduledStart"));

        return toPageResponse(bookingRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.BOOKING, key = "#id")
    public BookingResponseDto fetchBooking(UUID id) {
        return bookingMapper.toResponse(findBooking(id));
    }

    @Caching(
            put = @CachePut(cacheNames = CacheNames.BOOKING, key = "#id"),
            evict = @CacheEvict(cacheNames = {CacheNames.BOOKINGS, CacheNames.BOOKING_SEARCH,
                    CacheNames.BOOKING_PDF}, allEntries = true)
    )
    public BookingResponseDto updateBooking(UUID id, UpdateBookingRequestDto dto) {
        Booking booking = findBooking(id);
        User student = currentStudent();

        if (!booking.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own bookings");
        }
        if (!booking.getStatus().isActive()) {
            throw new ConflictRequestException("This booking can no longer be changed because it is " + booking.getStatus());
        }

        bookingMapper.updateEntity(dto, booking);

        if (dto.courseId() != null) {
            booking.setCourse(findCourse(dto.courseId()));
        }

        if (dto.isReschedule()) {
            ensureSlotIsFree(booking.getTutor().getId(), student.getId(),
                    dto.scheduledStart(), dto.scheduledEnd(), booking.getId());

            booking.setScheduledStart(dto.scheduledStart());
            booking.setScheduledEnd(dto.scheduledEnd());
            booking.setRescheduleCount(booking.getRescheduleCount() + 1);
            booking.setLastRescheduledAt(Instant.now());
            booking.setReminderSent(false);
            booking.setReminderSentAt(null);

            if (booking.getStatus() == BookingStatus.CONFIRMED) {
                booking.setStatus(BookingStatus.PENDING);
                booking.setConfirmedAt(null);
            }
        }

        ensureLocationProvided(booking.getSessionMode(), booking.getLocationName(), booking.getAddressLine());

        Booking saved;
        try {
            saved = bookingRepository.saveAndFlush(booking);
        } catch (BadRequestException ex) {
            throw translateIntegrityError(ex);
        }

        log.info("Booking {} updated by student {}{}", id, student.getId(), dto.isReschedule() ? " (rescheduled)" : "");
        return bookingMapper.toResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BOOKING, key = "#id"),
            @CacheEvict(cacheNames = {CacheNames.BOOKINGS, CacheNames.BOOKING_SEARCH,
                    CacheNames.BOOKING_COUNT, CacheNames.BOOKING_PDF}, allEntries = true)
    })
    public void deleteBooking(UUID id) {
        Booking booking = findBooking(id);
        bookingRepository.delete(booking);
        log.info("Booking {} ({}) deleted", id, booking.getBookingReference());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.BOOKING_COUNT, key = "'total'")
    public long countTotalNumberOfBookings() {
        return bookingRepository.count();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.BOOKING_SEARCH, key = "#query")
    public BookingPageResponseDto searchBookings(PagedQueryBookingRequestDto query) {
        Page<Booking> result = bookingRepository.findAll(BookingSpecification.fromQuery(query), query.toPageable());
        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.BOOKING_PDF, key = "#query")
    public byte[] downloadBookings(PagedQueryBookingRequestDto query) {
        Sort sort = query.toPageable().getSort();
        Page<Booking> result = bookingRepository.findAll(
                BookingSpecification.fromQuery(query),
                PageRequest.of(0, MAX_EXPORT_ROWS, sort));

        List<BookingResponseDto> rows = bookingMapper.toResponseList(result.getContent());

        log.info("Generating bookings PDF with {} of {} rows", rows.size(), result.getTotalElements());
        return bookingPdfGenerator.generate(rows, result.getTotalElements(), describeFilters(query));
    }

    /** The booking's tutor confirms it. Online and hybrid sessions need a meeting link. */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BOOKING, key = "#id"),
            @CacheEvict(cacheNames = {CacheNames.BOOKINGS, CacheNames.BOOKING_SEARCH,
                    CacheNames.BOOKING_PDF}, allEntries = true)
    })
    public BookingResponseDto approveBooking(UUID id, ApproveBookingRequestDto dto) {
        Booking booking = findBooking(id);
        User tutorUser = currentUser();
        requireAssignedTutor(booking, tutorUser);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictRequestException("Only pending bookings can be approved. This booking is "
                    + booking.getStatus());
        }
        if (!booking.getScheduledStart().isAfter(Instant.now())) {
            throw new ConflictRequestException("This session has already started and can no longer be approved");
        }

        if (dto != null) {
            if (dto.meetingLink() != null) booking.setMeetingLink(dto.meetingLink());
            if (dto.meetingId() != null) booking.setMeetingId(dto.meetingId());
            if (dto.meetingPasscode() != null) booking.setMeetingPasscode(dto.meetingPasscode());
            if (dto.tutorNotes() != null) booking.setTutorNotes(dto.tutorNotes());
        }
        if (requiresMeetingLink(booking.getSessionMode()) && isBlank(booking.getMeetingLink())) {
            throw new BadRequestException("Add a meeting link to approve an " + booking.getSessionMode()
                    + " session");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(Instant.now());
        booking.setRejectedAt(null);
        booking.setRejectionReason(null);

        Booking saved = saveBooking(booking);
        log.info("Booking {} ({}) approved by tutor user {}", id, saved.getBookingReference(), tutorUser.getId());
        return bookingMapper.toResponse(saved);
    }

    /** The booking's tutor declines it, with a reason for the student. */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.BOOKING, key = "#id"),
            @CacheEvict(cacheNames = {CacheNames.BOOKINGS, CacheNames.BOOKING_SEARCH,
                    CacheNames.BOOKING_PDF}, allEntries = true)
    })
    public BookingResponseDto disapproveBooking(UUID id, DisApproveBookingRequestDto dto) {
        Booking booking = findBooking(id);
        User tutorUser = currentUser();
        requireAssignedTutor(booking, tutorUser);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictRequestException("Only pending bookings can be declined. This booking is "
                    + booking.getStatus());
        }

        booking.setStatus(BookingStatus.REJECTED);
        booking.setRejectedAt(Instant.now());
        booking.setRejectionReason(dto.reason());

        Booking saved = saveBooking(booking);
        log.info("Booking {} ({}) declined by tutor user {}", id, saved.getBookingReference(), tutorUser.getId());
        return bookingMapper.toResponse(saved);
    }

    private void ensureSlotIsFree(UUID tutorId, UUID studentId, Instant start, Instant end, UUID excludeBookingId) {
        if (bookingRepository.existsTutorOverlap(tutorId, start, end, ACTIVE_STATUSES, excludeBookingId)) {
            throw new ConflictRequestException("The tutor already has a booking at this time. Please choose another time.");
        }
        if (bookingRepository.existsStudentOverlap(studentId, start, end, ACTIVE_STATUSES, excludeBookingId)) {
            throw new ConflictRequestException("You already have another booking at this time.");
        }
    }

    private void ensureLocationProvided(SessionMode mode, String locationName, String addressLine) {
        if (requiresLocation(mode) && isBlank(locationName) && isBlank(addressLine)) {
            throw new BadRequestException("A location name or address is required for in-person sessions");
        }
    }

    private boolean requiresLocation(SessionMode mode) {
        if (mode == null) {
            return false;
        }
        String name = mode.name();
        return name.equals("OFFLINE") || name.equals("IN_PERSON") || name.equals("PHYSICAL");
    }

    private void applyPricing(Booking booking, TutorProfile tutor) {
        BigDecimal price = null;
        booking.setPrice(price);
    }

    private Booking findBooking(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Booking not found with id " + id));
    }

    private TutorProfile findTutor(UUID id) {
        return tutorProfileRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Tutor not found with id " + id));
    }

    private Course findCourse(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Course not found with id " + id));
    }

    private BookingPageResponseDto toPageResponse(Page<Booking> page) {
        return new BookingPageResponseDto(
                bookingMapper.toResponseList(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private String describeFilters(PagedQueryBookingRequestDto query) {
        List<String> parts = new ArrayList<>();
        if (query.tutorId() != null) parts.add("tutor " + query.tutorId());
        if (query.studentId() != null) parts.add("student " + query.studentId());
        if (query.courseId() != null) parts.add("course " + query.courseId());
        if (query.status() != null) parts.add("status " + query.status());
        if (query.sessionMode() != null) parts.add("mode " + query.sessionMode());
        if (query.keyword() != null) parts.add("keyword \"" + query.keyword() + "\"");
        if (query.startFrom() != null) parts.add("from " + query.startFrom());
        if (query.startTo() != null) parts.add("to " + query.startTo());
        parts.add("sorted by " + query.sortBy() + " " + query.sortDirection().toUpperCase());
        return String.join("  |  ", parts);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private User currentStudent() {
        User user = currentUser();
        if (user.getRole() != UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can book sessions");
        }
        return user;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Your account was not found. Please log in again."));
    }

    /**
     * Turns database rule violations into clear API errors:
     * - 23P01 exclusion violation: the tutor got another booking at the same time (race)  -> 409
     * - 23505 unique violation:    same Idempotency-Key or booking reference                -> 409
     * - anything else (e.g. a missing foreign key) is a server problem: rethrow so it is logged as one
     */
    private RuntimeException translateIntegrityError(BadRequestException ex) {
        String sqlState = null;
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql) {
                sqlState = sql.getSQLState();
                break;
            }
        }
        if ("23P01".equals(sqlState)) {
            return new ConflictRequestException("This time slot is no longer available. Please choose another time.");
        }
        if ("23505".equals(sqlState)) {
            return new ConflictRequestException("This booking was already submitted. Please refresh and try again.");
        }
        log.error("Unexpected database constraint violation while saving a booking", ex);
        return ex;
    }

    /** Only the tutor the booking was made with may approve or decline it. */
    private void requireAssignedTutor(Booking booking, User user) {
        TutorProfile tutor = booking.getTutor();
        boolean isAssignedTutor = tutor != null && tutor.getUser() != null
                && tutor.getUser().getId().equals(user.getId());
        if (!isAssignedTutor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the tutor of this booking can approve or decline it");
        }
    }

    private boolean requiresMeetingLink(SessionMode mode) {
        return mode != null && (mode.name().equals("ONLINE") || mode.name().equals("HYBRID"));
    }

    /** Saves and turns database and concurrency errors into clear 409 responses. */
    private Booking saveBooking(Booking booking) {
        try {
            return bookingRepository.saveAndFlush(booking);
        } catch (BadRequestException ex) {
            throw translateIntegrityError(ex);
        } catch (ConflictRequestException ex) {
            throw new ConflictRequestException("This booking was changed at the same time. Please reload and try again.");
        }
    }
}