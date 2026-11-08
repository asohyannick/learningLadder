package com.learningLadder.learningLadder.service.timeTableService;

import com.learningLadder.learningLadder.config.backgroundJob.FireAndForgetEmailAsync.FireAndForgetEmailAsync;
import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.config.resendConfig.emailService.EmailService;
import com.learningLadder.learningLadder.config.resendConfig.resendEmailTemplateAsync.ResendEmailTemplateAsync;
import com.learningLadder.learningLadder.config.resendConfig.resendEmailTemplateAsync.ResendEmailTemplateAsync.EmailMessage;
import com.learningLadder.learningLadder.dto.timeTableDto.CreateTimeTableRequestDto;
import com.learningLadder.learningLadder.dto.timeTableDto.TimeTablePagedQueryDto;
import com.learningLadder.learningLadder.dto.timeTableDto.TimeTableResponseDto;
import com.learningLadder.learningLadder.dto.timeTableDto.UpdateTimeTableRequestDto;
import com.learningLadder.learningLadder.dto.userDto.PagedResponseDto;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.timeTable.TimeTable;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.mappers.timeTableMapper.TimeTableMapper;
import com.learningLadder.learningLadder.repository.courseRepository.CourseRepository;
import com.learningLadder.learningLadder.repository.timeTableRepository.TimeTableRepository;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TimeTableService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 5_000;
    private static final int MIN_KEY_LENGTH = 8;
    private static final int MAX_KEY_LENGTH = 100;

    private final TimeTableRepository timeTableRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final TimeTableMapper timeTableMapper;
    private final TimeTablePdfGenerator pdfGenerator;
    private final EmailService emailService;
    private final ResendEmailTemplateAsync resendEmailTemplateAsync;
    private final FireAndForgetEmailAsync fireAndForgetEmailAsync;

    @CacheEvict(cacheNames = CacheNames.TIMETABLE, allEntries = true)
    public TimeTableResponseDto createTimeTable(String idempotencyKey, CreateTimeTableRequestDto data) {
        String key = validateIdempotencyKey(idempotencyKey);
        User actor = currentUser();

        Optional<TimeTable> replay = timeTableRepository.findByIdempotencyKey(key);
        if (replay.isPresent()) {
            TimeTable previous = replay.get();
            if (!Objects.equals(actor.getId(), previous.getCreatedBy())) {
                throw new BadRequestException("This idempotency key has already been used");
            }
            return timeTableMapper.toResponse(previous);
        }

        Course course = courseRepository.findById(data.courseId())
                .orElseThrow(() -> new BadRequestException("Course not found"));

        TimeTable entity = timeTableMapper.toEntity(data);
        entity.setCourse(course);
        entity.setIdempotencyKey(key);
        entity.setCreatedBy(actor.getId());

        TimeTable saved = timeTableRepository.saveAndFlush(entity);
        TimeTableResponseDto response = timeTableMapper.toResponse(saved);

        notifyOwner(actor.getId(), "timetable-created", response, resendEmailTemplateAsync::timeTableCreatedEmail);
        return response;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.TIMETABLE, key = "'all:' + #page + ':' + #size")
    public PagedResponseDto<TimeTableResponseDto> fetchAllTimeTables(int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));

        return toPaged(timeTableRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.TIMETABLE, key = "'id:' + #id")
    public TimeTableResponseDto fetchTimeTable(UUID id) {
        return timeTableMapper.toResponse(findOrThrow(id));
    }

    @CacheEvict(cacheNames = CacheNames.TIMETABLE, allEntries = true)
    public TimeTableResponseDto updateTimeTable(UUID id, UpdateTimeTableRequestDto data) {
        TimeTable entity = findOrThrow(id);

        timeTableMapper.updateEntity(data, entity);
        clearBlankText(entity);
        if (!entity.isRecurring()) {
            entity.setDayOfWeek(null);
            entity.setEndDate(null);
        }
        validateSchedule(entity);

        TimeTable saved = timeTableRepository.saveAndFlush(entity);
        TimeTableResponseDto response = timeTableMapper.toResponse(saved);

        notifyOwner(saved.getCreatedBy(), "timetable-updated", response, resendEmailTemplateAsync::timeTableUpdatedEmail);
        return response;
    }

    @CacheEvict(cacheNames = CacheNames.TIMETABLE, allEntries = true)
    public void deleteTimeTable(UUID id) {
        TimeTable entity = findOrThrow(id);

        TimeTableResponseDto snapshot = timeTableMapper.toResponse(entity);
        UUID ownerId = entity.getCreatedBy();

        timeTableRepository.delete(entity);

        notifyOwner(ownerId, "timetable-deleted", snapshot, resendEmailTemplateAsync::timeTableDeletedEmail);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.TIMETABLE, key = "'count'")
    public long countTimeTables() {
        return timeTableRepository.count();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.TIMETABLE, key = "'search:' + #query")
    public PagedResponseDto<TimeTableResponseDto> searchTimeTables(TimeTablePagedQueryDto query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), sortFor(query));

        Page<TimeTable> result = timeTableRepository.findAll(buildSpecification(query), pageable);
        return toPaged(result);
    }

    @Transactional(readOnly = true)
    public byte[] downloadTimeTables(TimeTablePagedQueryDto query) {
        Specification<TimeTable> specification = buildSpecification(query);

        long total = timeTableRepository.count(specification);
        if (total == 0) {
            throw new BadRequestException("No timetable entries match the selected filters");
        }
        if (total > MAX_EXPORT_ROWS) {
            throw new BadRequestException("Too many timetable entries to export (" + total
                    + "). Narrow your filters to at most " + MAX_EXPORT_ROWS + ".");
        }

        List<TimeTableResponseDto> rows = timeTableRepository.findAll(specification, sortFor(query)).stream()
                .map(timeTableMapper::toResponse)
                .toList();

        return pdfGenerator.generate(rows, describeFilters(query));
    }

    private void notifyOwner(UUID ownerId, String event, TimeTableResponseDto snapshot,
                             BiFunction<String, TimeTableResponseDto, EmailMessage> composer) {
        if (ownerId == null) {
            log.debug("No owner recorded for timetable {}, skipping {} email", snapshot.id(), event);
            return;
        }
        try {
            Optional<User> owner = userRepository.findById(ownerId);
            if (owner.isEmpty()) {
                log.warn("Owner of timetable {} no longer exists, skipping {} email", snapshot.id(), event);
                return;
            }

            String to = owner.get().getEmail();
            EmailMessage message = composer.apply(owner.get().getFirstName(), snapshot);

            fireAndForgetEmailAsync.fire(event + " " + snapshot.id(),
                    () -> emailService.send(List.of(to), message.subject(), message.html(), message.text()));
        } catch (Exception ex) {
            log.warn("Could not queue {} email for timetable {}: {}", event, snapshot.id(), ex.getMessage());
        }
    }

    private Specification<TimeTable> buildSpecification(TimeTablePagedQueryDto q) {
        List<Specification<TimeTable>> filters = new ArrayList<>();

        if (q.keyword() != null) {
            String pattern = "%" + escapeLike(q.keyword().toLowerCase(Locale.ROOT)) + "%";
            filters.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.<String>get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("course").<String>get("name")), pattern, '\\')));
        }
        if (q.courseId() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("course").get("id"), q.courseId()));
        }
        if (q.status() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), q.status()));
        }
        if (q.sessionMode() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("sessionMode"), q.sessionMode()));
        }
        if (q.dayOfWeek() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("dayOfWeek"), q.dayOfWeek()));
        }
        if (q.recurring() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("recurring"), q.recurring()));
        }
        if (q.fromDate() != null) {
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(
                    cb.coalesce(root.<LocalDate>get("endDate"), root.<LocalDate>get("startDate")),
                    q.fromDate()));
        }
        if (q.toDate() != null) {
            filters.add((root, query, cb) -> cb.lessThanOrEqualTo(root.<LocalDate>get("startDate"), q.toDate()));
        }
        if (q.startTimeFrom() != null) {
            filters.add((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.<java.time.LocalTime>get("startTime"), q.startTimeFrom()));
        }
        if (q.startTimeTo() != null) {
            filters.add((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.<java.time.LocalTime>get("startTime"), q.startTimeTo()));
        }

        return Specification.allOf(filters);
    }

    private Sort sortFor(TimeTablePagedQueryDto q) {
        Sort.Direction direction = "desc".equals(q.sortDirection()) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, q.sortBy());
        if ("startDate".equals(q.sortBy())) {
            sort = sort.and(Sort.by(direction, "startTime"));
        }
        return sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private List<String> describeFilters(TimeTablePagedQueryDto q) {
        List<String> filters = new ArrayList<>();
        if (q.keyword() != null) filters.add("keyword \"" + q.keyword() + "\"");
        if (q.courseId() != null) filters.add("course " + q.courseId());
        if (q.status() != null) filters.add("status " + q.status());
        if (q.sessionMode() != null) filters.add("format " + q.sessionMode());
        if (q.dayOfWeek() != null) filters.add("day " + q.dayOfWeek());
        if (q.recurring() != null) filters.add(q.recurring() ? "recurring only" : "one-off only");
        if (q.fromDate() != null) filters.add("from " + q.fromDate());
        if (q.toDate() != null) filters.add("to " + q.toDate());
        if (q.startTimeFrom() != null) filters.add("starting after " + q.startTimeFrom());
        if (q.startTimeTo() != null) filters.add("starting before " + q.startTimeTo());
        return filters;
    }

    private PagedResponseDto<TimeTableResponseDto> toPaged(Page<TimeTable> page) {
        return PagedResponseDto.from(page, timeTableMapper::toResponse);
    }

    private String validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("The Idempotency-Key header is required");
        }
        String trimmed = key.trim();
        if (trimmed.length() < MIN_KEY_LENGTH || trimmed.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("The Idempotency-Key must be between "
                    + MIN_KEY_LENGTH + " and " + MAX_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    private TimeTable findOrThrow(UUID id) {
        return timeTableRepository.findById(id).orElseThrow(() -> new BadRequestException("Time table entry not found"));
    }

    private void clearBlankText(TimeTable entity) {
        entity.setDescription(blankToNull(entity.getDescription()));
        entity.setNotes(blankToNull(entity.getNotes()));
        entity.setLocation(blankToNull(entity.getLocation()));
        entity.setMeetingUrl(blankToNull(entity.getMeetingUrl()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void validateSchedule(TimeTable t) {
        if (!t.getEndTime().isAfter(t.getStartTime())) {
            throw new BadRequestException("End time must be after start time");
        }
        if (t.getEndDate() != null && t.getEndDate().isBefore(t.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }
        if (t.isRecurring()) {
            if (t.getDayOfWeek() == null || t.getEndDate() == null) {
                throw new BadRequestException("Day of week and end date are required when the entry is recurring");
            }
            if (t.getStartDate().getDayOfWeek() != t.getDayOfWeek()) {
                throw new BadRequestException("Start date must fall on the selected day of week");
            }
        }
        if (t.getSessionMode() != SessionMode.IN_PERSON && t.getMeetingUrl() == null) {
            throw new BadRequestException("Meeting URL is required for online and hybrid sessions");
        }
        if (t.getSessionMode() != SessionMode.ONLINE && t.getLocation() == null) {
            throw new BadRequestException("Location is required for in-person and hybrid sessions");
        }
        try {
            ZoneId.of(t.getTimezone());
        } catch (DateTimeException ex) {
            throw new BadRequestException("Timezone must be a valid IANA zone id, e.g. Africa/Douala");
        }
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BadRequestException("Authentication is required");
        }
        String name = authentication.getName();

        Optional<User> user;
        try {
            user = userRepository.findById(UUID.fromString(name));
        } catch (BadRequestException notAUuid) {
            user = userRepository.findByEmail(name);
        }
        return user.orElseThrow(() -> new BadRequestException("User account not found"));
    }
}