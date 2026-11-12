package com.learningLadder.learningLadder.controller.studentProfileController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.studentProfileDto.CreateStudentProfileRequestDto;
import com.learningLadder.learningLadder.dto.studentProfileDto.StudentProfilePagedQueryDto;
import com.learningLadder.learningLadder.dto.studentProfileDto.StudentProfileResponseDto;
import com.learningLadder.learningLadder.dto.studentProfileDto.StudentProfileSummaryResponseDto;
import com.learningLadder.learningLadder.dto.studentProfileDto.UpdateStudentProfileRequestDto;
import com.learningLadder.learningLadder.dto.userDto.PagedResponseDto;
import com.learningLadder.learningLadder.service.studentProfileService.StudentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/${app.api.version}/student-profile")
@Tag(name = "Student Profile Management Endpoints",
        description = "Create, view, update, delete, search and export student profiles")
@SecurityRequirement(name = "bearerAuth")
public class StudentProfileController {

    /** Roles allowed to browse, search and export other students' profiles. Edit this one list to change it. */
    private static final String STAFF =
            "hasAnyRole('SUPER_ADMIN','ADMIN','IT_ADMIN','REGISTRAR','DEPUTY_REGISTRAR',"
                    + "'ADMISSIONS_OFFICER','STUDENT_REGISTRAR')";

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmm")
            .withZone(ZoneOffset.UTC);

    private final StudentProfileService studentProfileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Create your student profile",
            description = "Multipart request: a JSON part named 'data' with the profile details and an optional "
                    + "'profilePicture' file (JPEG, PNG, WebP or GIF, up to 10 MB). The 'Idempotency-Key' header "
                    + "is required: repeating a request with the same key returns the profile created the first "
                    + "time instead of creating a second one. Each student account can only have one profile."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Student profile created",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed, missing or malformed idempotency key, bad picture, "
                            + "or the account already has a profile",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Profile already exists", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "You already have a student profile",
                                              "path": "/api/v1/student-profile",
                                              "timestamp": "2026-11-08T20:50:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Key already used by someone else", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "This idempotency key has already been used",
                                              "path": "/api/v1/student-profile",
                                              "timestamp": "2026-11-08T20:50:00Z"
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not a student", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<StudentProfileResponseDto>> createStudentProfile(
            @Parameter(description = "Unique key for this creation attempt, 8-100 characters. Reuse the same key "
                    + "when retrying the same request.",
                    required = true, example = "3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(description = "Profile details as JSON")
            @Valid @RequestPart("data") CreateStudentProfileRequestDto data,
            @Parameter(description = "Optional profile picture (JPEG, PNG, WebP or GIF, max 10 MB)")
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture,
            HttpServletRequest request) {

        StudentProfileResponseDto created =
                studentProfileService.createStudentProfile(idempotencyKey, data, profilePicture);

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "Your student profile has been created successfully.",
                created,
                request.getRequestURI()));
    }

    @GetMapping
    @PreAuthorize(STAFF)
    @Operation(
            summary = "List student profiles (staff only)",
            description = "Returns a page of profile summaries, newest first. Page size is capped at 100. "
                    + "Use /search to filter or sort. Results are cached for up to 5 minutes."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page of student profiles returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to view student profiles",
                    content = @Content)
    })
    public ResponseEntity<APISuccessResponse<PagedResponseDto<StudentProfileSummaryResponseDto>>> fetchAllStudentProfiles(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        PagedResponseDto<StudentProfileSummaryResponseDto> profiles =
                studentProfileService.fetchAllStudentProfiles(page, size);

        String message = profiles.totalElements() == 0
                ? "No student profiles found."
                : "Student profiles retrieved successfully. Showing " + profiles.numberOfElements()
                  + " of " + profiles.totalElements() + " profile(s).";

        return ResponseEntity.ok(APISuccessResponse.ok(message, profiles, request.getRequestURI()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Get a student profile by ID (staff only)",
            description = "Returns the full profile, including a fresh link to the profile picture. "
                    + "Results are cached for up to 5 minutes."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Student profile returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Student profile not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Not found", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "BAD_REQUEST",
                                      "message": "Student profile not found",
                                      "path": "/api/v1/student-profile/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                      "timestamp": "2026-11-08T20:55:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to view student profiles",
                    content = @Content)
    })
    public ResponseEntity<APISuccessResponse<StudentProfileResponseDto>> fetchStudentProfile(
            @Parameter(description = "ID of the student profile", required = true)
            @PathVariable UUID id,
            HttpServletRequest request) {

        StudentProfileResponseDto profile = studentProfileService.fetchStudentProfile(id);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Student profile retrieved successfully.",
                profile,
                request.getRequestURI()));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Update a student profile",
            description = "Multipart request: an optional JSON part 'data' and an optional 'profilePicture' file. "
                    + "Only the fields you send are changed. A new picture replaces the old one. "
                    + "Allowed for the profile's owner and for admins."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Student profile updated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed, profile not found, bad picture, or not your profile",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Not your profile", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "You can only manage your own student profile",
                                              "path": "/api/v1/student-profile/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                              "timestamp": "2026-11-08T21:00:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Validation failed", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "VALIDATION_FAILED",
                                              "message": "Validation failed",
                                              "path": "/api/v1/student-profile/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                              "timestamp": "2026-11-08T21:00:00Z",
                                              "errors": [
                                                { "field": "phoneNumber", "message": "Phone number is not valid", "rejectedValue": "abc" }
                                              ]
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<StudentProfileResponseDto>> updateStudentProfile(
            @Parameter(description = "ID of the student profile to update", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Fields to change, as JSON")
            @Valid @RequestPart(value = "data", required = false) UpdateStudentProfileRequestDto data,
            @Parameter(description = "Optional new profile picture (JPEG, PNG, WebP or GIF, max 10 MB)")
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture,
            HttpServletRequest request) {

        StudentProfileResponseDto updated = studentProfileService.updateStudentProfile(id, data, profilePicture);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Student profile updated successfully.",
                updated,
                request.getRequestURI()));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a student profile",
            description = "Permanently deletes the profile and its profile picture. "
                    + "Allowed for the profile's owner and for admins. This cannot be undone."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Student profile deleted",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Profile not found or not yours",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Not found", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "BAD_REQUEST",
                                      "message": "Student profile not found",
                                      "path": "/api/v1/student-profile/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                      "timestamp": "2026-11-08T21:05:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<Void>> deleteStudentProfile(
            @Parameter(description = "ID of the student profile to delete", required = true)
            @PathVariable UUID id,
            HttpServletRequest request) {

        studentProfileService.deleteStudentProfile(id);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Student profile deleted successfully.",
                request.getRequestURI()));
    }

    @GetMapping("/count")
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Count all student profiles (staff only)",
            description = "Returns the total number of student profiles. The value is cached for up to 5 minutes "
                    + "and refreshed whenever a profile is created or deleted."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Total returned in the 'data' field",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to view student profiles",
                    content = @Content)
    })
    public ResponseEntity<APISuccessResponse<Long>> countStudentProfiles(HttpServletRequest request) {
        long total = studentProfileService.countStudentProfiles();

        return ResponseEntity.ok(APISuccessResponse.ok(
                total == 1 ? "There is 1 student profile." : "There are " + total + " student profiles.",
                total,
                request.getRequestURI()));
    }

    @GetMapping("/download")
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Download student profiles as a PDF (staff only)",
            description = "Generates a PDF table of the profiles that match the optional filters and sorting "
                    + "(the same options as /search; page and size are ignored). "
                    + "Exports are limited to 5,000 profiles, so narrow the filters for larger sets."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "PDF file",
                    content = @Content(
                            mediaType = "application/pdf",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid filters, nothing matches, or too many profiles to export",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Nothing to export", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "No student profiles match the selected filters",
                                              "path": "/api/v1/student-profile/download",
                                              "timestamp": "2026-11-08T21:10:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Too many rows", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Too many profiles to export (7210). Narrow your filters to at most 5000.",
                                              "path": "/api/v1/student-profile/download",
                                              "timestamp": "2026-11-08T21:10:00Z"
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to export student profiles",
                    content = @Content)
    })
    public ResponseEntity<byte[]> downloadStudentProfiles(
            @ParameterObject @Valid StudentProfilePagedQueryDto query) {

        byte[] pdf = studentProfileService.downloadStudentProfiles(query);
        String fileName = "student-profiles-" + FILE_STAMP.format(Instant.now()) + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .contentLength(pdf.length)
                .body(pdf);
    }

    @GetMapping("/search")
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Search student profiles (staff only)",
            description = "Combine a keyword (matches name, email and student number) with any of: faculty, "
                    + "department, program of study, level, enrollment status and an admission date range. "
                    + "Supports sorting and pagination (page size capped at 100). "
                    + "Results are cached for up to 5 minutes."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page of matching student profiles returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid filter, sort field or date range",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Invalid date range", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "VALIDATION_FAILED",
                                      "message": "Validation failed",
                                      "path": "/api/v1/student-profile/search",
                                      "timestamp": "2026-11-08T21:15:00Z",
                                      "errors": [
                                        { "field": "admissionRangeValid", "message": "admittedFrom cannot be after admittedTo" }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to view student profiles",
                    content = @Content)
    })
    public ResponseEntity<APISuccessResponse<PagedResponseDto<StudentProfileSummaryResponseDto>>> searchStudentProfiles(
            @ParameterObject @Valid StudentProfilePagedQueryDto query,
            HttpServletRequest request) {

        PagedResponseDto<StudentProfileSummaryResponseDto> results =
                studentProfileService.searchStudentProfiles(query);

        String message = results.totalElements() == 0
                ? "No student profiles match your search."
                : "Found " + results.totalElements() + " student profile(s). Showing "
                  + results.numberOfElements() + " on this page.";

        return ResponseEntity.ok(APISuccessResponse.ok(message, results, request.getRequestURI()));
    }
}