package com.learningLadder.learningLadder.controller.timeTableController;

import com.learningLadder.learningLadder.dto.timeTableDto.CreateTimeTableRequestDto;
import com.learningLadder.learningLadder.dto.timeTableDto.TimeTablePagedQueryDto;
import com.learningLadder.learningLadder.dto.timeTableDto.TimeTableResponseDto;
import com.learningLadder.learningLadder.dto.timeTableDto.UpdateTimeTableRequestDto;
import com.learningLadder.learningLadder.dto.userDto.PagedResponseDto;
import com.learningLadder.learningLadder.service.timeTableService.TimeTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/${app.api.version}/time-table")
@RequiredArgsConstructor
@Tag(name = "TimeTable Management Endpoint",
        description = "Create, read, update, delete, search and export timetable entries")
public class TimeTableController {

    private final TimeTableService timeTableService;

    @Operation(
            summary = "Create a timetable entry",
            description = "Creates a timetable entry for a course. The Idempotency-Key header is required "
                    + "(8-100 characters); replaying the same key by the same user returns the original entry "
                    + "instead of creating a duplicate. The creator is notified by email.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Timetable entry created",
                    content = @Content(schema = @Schema(implementation = TimeTableResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed, missing/invalid idempotency key, "
                    + "course not found, or key already used by another user", content = @Content),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TimeTableResponseDto> createTimeTable(
            @Parameter(description = "Unique client-generated key (8-100 chars) to make the request idempotent",
                    required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTimeTableRequestDto data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(timeTableService.createTimeTable(idempotencyKey, data));
    }

    @Operation(
            summary = "List all timetable entries",
            description = "Returns a paginated list ordered by creation date (newest first). "
                    + "Page size is capped at 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of timetable entries retrieved")
    })
    @GetMapping
    public ResponseEntity<PagedResponseDto<TimeTableResponseDto>> fetchAllTimeTables(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(timeTableService.fetchAllTimeTables(page, size));
    }

    @Operation(
            summary = "Search timetable entries",
            description = "Filters by keyword (title or course name), course, status, session mode, day of week, "
                    + "recurrence, date range and start-time range, with sorting and pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching timetable entries retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters", content = @Content)
    })
    @GetMapping("/search")
    public ResponseEntity<PagedResponseDto<TimeTableResponseDto>> searchTimeTables(
            @Valid @ParameterObject TimeTablePagedQueryDto query) {
        return ResponseEntity.ok(timeTableService.searchTimeTables(query));
    }

    @Operation(
            summary = "Download timetable entries as PDF",
            description = "Exports the entries matching the given filters as a PDF. Fails if nothing matches "
                    + "or if more than 5,000 entries match.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generated",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "No matching entries, too many entries, "
                    + "or invalid filters", content = @Content)
    })
    @GetMapping(value = "/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadTimeTables(
            @Valid @ParameterObject TimeTablePagedQueryDto query) {
        byte[] pdf = timeTableService.downloadTimeTables(query);

        String filename = "timetable-" + LocalDate.now() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(pdf.length);

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @Operation(
            summary = "Count timetable entries",
            description = "Returns the total number of timetable entries.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Total count retrieved",
                    content = @Content(schema = @Schema(implementation = Long.class, example = "42")))
    })
    @GetMapping("/count")
    public ResponseEntity<Long> countTimeTables() {
        return ResponseEntity.ok(timeTableService.countTimeTables());
    }

    @Operation(
            summary = "Get a timetable entry by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timetable entry retrieved",
                    content = @Content(schema = @Schema(implementation = TimeTableResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Timetable entry not found or invalid ID",
                    content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<TimeTableResponseDto> fetchTimeTable(
            @Parameter(description = "Timetable entry ID", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(timeTableService.fetchTimeTable(id));
    }

    @Operation(
            summary = "Update a timetable entry",
            description = "Updates an existing entry. Non-recurring entries have day of week and end date cleared. "
                    + "The schedule is re-validated, and the original creator is notified by email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timetable entry updated",
                    content = @Content(schema = @Schema(implementation = TimeTableResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or timetable entry not found",
                    content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<TimeTableResponseDto> updateTimeTable(
            @Parameter(description = "Timetable entry ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateTimeTableRequestDto data) {
        return ResponseEntity.ok(timeTableService.updateTimeTable(id, data));
    }

    @Operation(
            summary = "Delete a timetable entry",
            description = "Deletes the entry and notifies the original creator by email.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Timetable entry deleted"),
            @ApiResponse(responseCode = "400", description = "Timetable entry not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTimeTable(
            @Parameter(description = "Timetable entry ID", required = true) @PathVariable UUID id) {
        timeTableService.deleteTimeTable(id);
        return ResponseEntity.noContent().build();
    }
}