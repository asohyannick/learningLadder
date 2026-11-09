package com.learningLadder.learningLadder.controller.bookingController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.bookingDto.BookingPageResponseDto;
import com.learningLadder.learningLadder.dto.bookingDto.BookingResponseDto;
import com.learningLadder.learningLadder.dto.bookingDto.CreateBookingRequestDto;
import com.learningLadder.learningLadder.dto.bookingDto.PagedQueryBookingRequestDto;
import com.learningLadder.learningLadder.dto.bookingDto.UpdateBookingRequestDto;
import com.learningLadder.learningLadder.enums.bookingStatus.BookingStatus;
import com.learningLadder.learningLadder.service.bookingService.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/${app.api.version}/booking")
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "Booking Management Endpoints",
        description = "Students book online or in-person sessions with tutors. "
                + "Staff can list, search, count, export and delete bookings. "
                + "Read endpoints are cached; write endpoints refresh the cache automatically. "
                + "Successful calls return APISuccessResponse; failures return APIErrorResponse."
)
public class BookingController {

    private static final String STAFF =
            "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'IT_ADMIN', 'ACADEMIC_DIRECTOR')";

    private final BookingService bookingService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Book a session with a tutor",
            description = """
                    Creates a PENDING booking that the tutor must confirm.
                    - **sessionMode**: online or in person; in-person sessions need a location name or address
                    - Times are ISO-8601 instants, e.g. 2026-11-12T14:00:00Z; timeZone is an IANA zone, e.g. Africa/Douala
                    - A session lasts between 15 minutes and 8 hours
                    - Neither the tutor nor the student may already have an active booking at that time

                    Send a unique **Idempotency-Key** header per booking: repeating the same key
                    returns the original booking instead of creating a duplicate.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booking created"),
            @ApiResponse(responseCode = "400", description = "Validation failed or missing Idempotency-Key",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a student or no student profile",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Tutor or course not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Time slot taken or Idempotency-Key reused",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<BookingResponseDto>> addBooking(
            @Valid @RequestBody CreateBookingRequestDto request,
            @Parameter(in = ParameterIn.HEADER, required = true,
                    description = "Unique key per booking (e.g. a UUID generated by the client)",
                    example = "8b1f2c3d-4e5f-4a6b-9c7d-0e1f2a3b4c5d")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        BookingResponseDto booking = bookingService.addBooking(request, idempotencyKey, currentUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "Booking " + booking.bookingReference() + " created successfully. Waiting for the tutor to confirm.",
                booking, currentPath()));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Fetch all bookings",
            description = "Paginated list, most recent sessions first. Results are cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of bookings"),
            @ApiResponse(responseCode = "400", description = "Invalid page or size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<BookingPageResponseDto>> fetchBookings(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        BookingPageResponseDto result = bookingService.fetchBookings(page, size);
        String message = result.totalElements() == 0
                ? "No bookings found"
                : "Bookings fetched successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Count bookings", description = "Total number of bookings. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Total count"),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Long>> countTotalNumberOfBookings() {
        long total = bookingService.countTotalNumberOfBookings();

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Total bookings counted successfully", total, currentPath()));
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Search bookings",
            description = """
                    Filter by tutor, student, course, status, session mode, start-time range and keyword
                    (topic or booking reference), with sorting and pagination.
                    sortBy: scheduledStart, createdAt, updatedAt, status, durationMinutes, price.
                    Dates use ISO-8601, e.g. 2026-11-10T00:00:00Z. Results are cached.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching bookings"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<BookingPageResponseDto>> searchBookings(
            @Valid @ParameterObject @ModelAttribute PagedQueryBookingRequestDto query) {

        BookingPageResponseDto result = bookingService.searchBookings(query);
        String message = result.totalElements() == 0
                ? "No bookings match your search"
                : "Search completed: " + result.totalElements() + " booking(s) found";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Download bookings as PDF",
            description = "Uses the same filters and sorting as /search (page and size are ignored). "
                    + "Exports at most 2,000 rows. Meeting links and passcodes are not included. "
                    + "On success the body is the PDF file; on failure it is an APIErrorResponse JSON.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF report",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<byte[]> downloadBookings(
            @Valid @ParameterObject @ModelAttribute PagedQueryBookingRequestDto query) {

        byte[] pdf = bookingService.downloadBookings(query);
        String fileName = "bookings-" + LocalDate.now() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(pdf);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF + " or @bookingAccess.isParticipant(#id, authentication.principal.id)")
    @Operation(summary = "Fetch a booking",
            description = "Staff can view any booking; students and tutors only bookings they take part in. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The booking"),
            @ApiResponse(responseCode = "400", description = "Id is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<BookingResponseDto>> fetchBooking(
            @Parameter(description = "Booking id") @PathVariable UUID id) {

        BookingResponseDto booking = bookingService.fetchBooking(id);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Booking fetched successfully", booking, currentPath()));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Update or reschedule a booking",
            description = """
                    Partial update by the student who made the booking, while it is PENDING or CONFIRMED.
                    Send only the fields to change. To reschedule, send BOTH scheduledStart and scheduledEnd.
                    Rescheduling a CONFIRMED booking sets it back to PENDING so the tutor confirms the new time.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the student who made this booking",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking or course not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Booking no longer editable, slot taken or edited concurrently",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<BookingResponseDto>> updateBooking(
            @Parameter(description = "Booking id") @PathVariable UUID id,
            @Valid @RequestBody UpdateBookingRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        BookingResponseDto booking = bookingService.updateBooking(id, request, currentUserId);

        String message;
        if (request.isReschedule() && booking.status() == BookingStatus.PENDING) {
            message = "Booking rescheduled successfully. The tutor needs to confirm the new time.";
        } else if (request.isReschedule()) {
            message = "Booking rescheduled successfully";
        } else {
            message = "Booking updated successfully";
        }

        return ResponseEntity.ok(APISuccessResponse.ok(message, booking, currentPath()));
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Delete a booking",
            description = "Permanently deletes a booking. Students and tutors should cancel instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking deleted"),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Booking not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Void>> deleteBooking(
            @Parameter(description = "Booking id") @PathVariable UUID id) {

        bookingService.deleteBooking(id);

        return ResponseEntity.ok(APISuccessResponse.ok("Booking deleted successfully", currentPath()));
    }

    private static String currentPath() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
    }
}