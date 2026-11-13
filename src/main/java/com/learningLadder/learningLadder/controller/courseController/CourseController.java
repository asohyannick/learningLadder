package com.learningLadder.learningLadder.controller.courseController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.courseDto.*;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import com.learningLadder.learningLadder.service.courseService.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/${app.api.version}/course")
@Tag(name = "Course Management Endpoints",
        description = "Create, read, update, delete, search, count and export courses")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Validation failed or malformed request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Authenticated but not allowed",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIErrorResponse.class)))
})
@RequiredArgsConstructor
public class CourseController {

    private static final String WRITE_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_DIRECTOR','DEAN','HEAD_OF_DEPARTMENT','PROFESSOR','LECTURER')";

    private final CourseService courseService;
    private final UserRepository userRepository;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Create a course",
            description = "Multipart request. The 'data' part is JSON (Content-Type: application/json); "
                    + "files are optional. Send an Idempotency-Key header so retries never create duplicates.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Course created (or the original returned for a repeated Idempotency-Key)"),
            @ApiResponse(responseCode = "413", description = "Upload too large",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<CourseResponseDto>> createCourse(
            @Parameter(in = ParameterIn.HEADER, description = "Unique key per create action (max 100 chars). Generated if omitted.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestPart("data") CreateCourseRequestDto data,
            @Parameter(description = "Cover image") @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            @Parameter(description = "Trailer video") @RequestPart(value = "promoVideo", required = false) MultipartFile promoVideo,
            @Parameter(description = "Syllabus document (PDF)") @RequestPart(value = "syllabus", required = false) MultipartFile syllabus,
            @Parameter(description = "Lesson files, handouts and other materials") @RequestPart(value = "materials", required = false) List<MultipartFile> materials,
            Authentication authentication,
            HttpServletRequest request) {

        CourseResponseDto created = courseService.createCourse(
                data, idempotencyKey, thumbnail, promoVideo, syllabus, materials, currentUserId(authentication));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APISuccessResponse.created("Course created successfully", created, request.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "List courses (paged)", description = "Newest first. Results are cached.")
    @ApiResponse(responseCode = "200", description = "Page of courses")
    public ResponseEntity<APISuccessResponse<PagedResponseDto<CourseSummaryResponseDto>>> fetchAllCourses(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Courses fetched successfully", courseService.fetchAllCourses(page, size), request.getRequestURI()));
    }

    @GetMapping("/search")
    @Operation(summary = "Search courses",
            description = "Filter by keyword, category, level, status, language, published flag and price range; "
                    + "sort by name, price, createdAt, updatedAt, publishedAt or durationMinutes; paginated.")
    @ApiResponse(responseCode = "200", description = "Page of matching courses")
    public ResponseEntity<APISuccessResponse<PagedResponseDto<CourseSummaryResponseDto>>> searchCourses(
            @Valid @ParameterObject CoursePagedQueryDto query,
            HttpServletRequest request) {

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Courses fetched successfully", courseService.searchCourses(query), request.getRequestURI()));
    }

    @GetMapping("/count")
    @Operation(summary = "Count all courses")
    @ApiResponse(responseCode = "200", description = "Total number of courses")
    public ResponseEntity<APISuccessResponse<Long>> countCourses(HttpServletRequest request) {
        return ResponseEntity.ok(APISuccessResponse.ok(
                "Course count fetched successfully", courseService.countCourses(), request.getRequestURI()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a course by ID", description = "Includes materials and media URLs. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Course found"),
            @ApiResponse(responseCode = "404", description = "Course not found",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<CourseResponseDto>> fetchCourse(
            @Parameter(description = "Course ID") @PathVariable UUID id,
            HttpServletRequest request) {

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Course fetched successfully", courseService.fetchCourse(id), request.getRequestURI()));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Update a course with files",
            description = "Multipart request. The optional 'data' part is JSON (Content-Type: application/json) with "
                    + "the fields to change; omitted fields keep their current value. Each file part is optional: "
                    + "a new thumbnail, promo video or syllabus replaces the current one, materials are added.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Course updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Course not found",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Upload too large",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Wrong file type for a part",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<CourseResponseDto>> updateCourseWithFiles(
            @Parameter(description = "Course ID") @PathVariable UUID id,
            @Valid @RequestPart(value = "data", required = false) UpdateCourseRequestDto data,
            @Parameter(description = "New cover image (replaces the current one)")
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            @Parameter(description = "New trailer video (replaces the current one)")
            @RequestPart(value = "promoVideo", required = false) MultipartFile promoVideo,
            @Parameter(description = "New syllabus PDF (replaces the current one)")
            @RequestPart(value = "syllabus", required = false) MultipartFile syllabus,
            @Parameter(description = "Extra materials (added to the existing ones)")
            @RequestPart(value = "materials", required = false) List<MultipartFile> materials,
            Authentication authentication,
            HttpServletRequest request) {

        CourseResponseDto updated = courseService.updateCourseWithFiles(
                id, data, thumbnail, promoVideo, syllabus, materials, currentUserId(authentication));

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Course updated successfully", updated, request.getRequestURI()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_DIRECTOR')")
    @Operation(summary = "Delete a course", description = "Hard delete. Also removes the course's files from storage.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Course deleted"),
            @ApiResponse(responseCode = "404", description = "Course not found",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Void>> deleteCourse(
            @Parameter(description = "Course ID") @PathVariable UUID id,
            HttpServletRequest request) {

        courseService.deleteCourse(id);
        return ResponseEntity.ok(APISuccessResponse.ok("Course deleted successfully", request.getRequestURI()));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a course as PDF")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF file",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Course not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<byte[]> downloadCoursePdf(
            @Parameter(description = "Course ID") @PathVariable UUID id) {

        CoursePdfDto pdf = courseService.downloadCoursePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        if (authentication.getPrincipal() instanceof User user) {
            return UUID.fromString(String.valueOf(user.getId()));
        }

        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .filter(user -> !user.isAccountDeleted())
                .map(user -> UUID.fromString(String.valueOf(user.getId())))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Unable to resolve the current user. Please log in again."));
    }
}