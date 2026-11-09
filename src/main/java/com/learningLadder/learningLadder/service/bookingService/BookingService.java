package com.learningLadder.learningLadder.service.bookingService;

import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.dto.bookingDto.BookingPageResponseDto;
import com.learningLadder.learningLadder.dto.bookingDto.BookingResponseDto;
import com.learningLadder.learningLadder.dto.bookingDto.CreateBookingRequestDto;
import com.learningLadder.learningLadder.dto.bookingDto.PagedQueryBookingRequestDto;
import com.learningLadder.learningLadder.dto.bookingDto.UpdateBookingRequestDto;
import com.learningLadder.learningLadder.entity.booking.Booking;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.studentProfile.StudentProfile;
import com.learningLadder.learningLadder.entity.tutorProfile.TutorProfile;
import com.learningLadder.learningLadder.enums.bookingStatus.BookingStatus;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.notFoundRequestException.NotFoundRequestException;
import com.learningLadder.learningLadder.mappers.bookingMapper.BookingMapper;
import com.learningLadder.learningLadder.repository.bookingRepository.BookingRepository;
import com.learningLadder.learningLadder.repository.bookingRepository.BookingSpecification;
import com.learningLadder.learningLadder.repository.courseRepository.CourseRepository;
import com.learningLadder.learningLadder.repository.studentProfileRepository.StudentProfileRepository;
import com.learningLadder.learningLadder.repository.tutorProfileRepository.TutorProfileRepository;
import com.learningLadder.learningLadder.utils.pdf.BookingPdfGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
    private final StudentProfileRepository studentProfileRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final CourseRepository courseRepository;
    private final BookingMapper bookingMapper;
    private final BookingPdfGenerator bookingPdfGenerator;

    @CacheEvict(cacheNames = {CacheNames.BOOKINGS, CacheNames.BOOKING_SEARCH,
            CacheNames.BOOKING_COUNT, CacheNames.BOOKING_PDF}, allEntries = true)
    public BookingResponseDto addBooking(CreateBookingRequestDto dto, String idempotencyKey, UUID currentUserId) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BadRequestException("The Idempotency-Key header is required (max 100 characters)");
        }

        StudentProfile student = findStudentProfileOfUser(currentUserId);

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
        booking.setIdempotencyKey(idempotencyKey);
        applyPricing(booking, tutor);

        Booking saved;
        try {
            saved = bookingRepository.saveAndFlush(booking);
        } catch (ConflictRequestException ex) {
            throw new ConflictRequestException("This time slot is no longer available. Please choose another time.");
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
    public BookingResponseDto updateBooking(UUID id, UpdateBookingRequestDto dto, UUID currentUserId) {
        Booking booking = findBooking(id);
        StudentProfile student = findStudentProfileOfUser(currentUserId);

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
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictRequestException("This time slot is no longer available. Please choose another time.");
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

    private StudentProfile findStudentProfileOfUser(UUID userId) {
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only students with a student profile can book sessions"));
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

}