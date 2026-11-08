package com.learningLadder.learningLadder.controller.tutorProfileController;
import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.tutorProfileDto.CreateTutorProfileRequestDto;
import com.learningLadder.learningLadder.dto.tutorProfileDto.TutorProfilePagedQueryDto;
import com.learningLadder.learningLadder.dto.tutorProfileDto.TutorProfileResponseDto;
import com.learningLadder.learningLadder.dto.tutorProfileDto.TutorProfileSummaryResponseDto;
import com.learningLadder.learningLadder.dto.tutorProfileDto.UpdateTutorProfileRequestDto;
import com.learningLadder.learningLadder.dto.userDto.PagedResponseDto;
import com.learningLadder.learningLadder.service.tutorProfileService.TutorProfileService;
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
@RequestMapping("api/${app.api.version}/tutor-profile")
@Tag(name = "Tutor Profile Management Endpoints",
        description = "Create, view, update, delete, search and export tutor profiles")
@SecurityRequirement(name = "bearerAuth")
public class TutorProfileController {

    private static final String STAFF =
            "hasAnyRole('SUPER_ADMIN','ADMIN','IT_ADMIN','REGISTRAR','DEPUTY_REGISTRAR',"
                    + "'ADMISSIONS_OFFICER','STUDENT_REGISTRAR')";

    private static final String TUTORS = "hasAnyRole('LECTURER','PROFESSOR','ASSISTANT_LECTURER')";

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmm")
            .withZone(ZoneOffset.UTC);

    private final TutorProfileService tutorProfileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(TUTORS)
    @Operation(
            summary = "Create your tutor profile",
            description = "Multipart request: a JSON part named 'data' with the profile details and an optional "
                    + "'profilePicture' file (JPEG, PNG, WebP or GIF, up to 10 MB). The 'Idempotency-Key' header "
                    + "is required: repeating a request with the same key returns the profile created the first "
                    + "time instead of creating a second one. Each tutor account can only have one profile, and "
                    + "new profiles start as PENDING_APPROVAL until staff approve them."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Tutor profile created",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed, missing or malformed idempotency key, bad picture, "
                            + "wrong account type, or the account already has a profile",
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
                                              "message": "You already have a tutor profile",
                                              "path": "/api/v1/tutor-profile",
                                              "timestamp": "2026-11-08T21:45:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Key already used by someone else", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "This idempotency key has already been used",
                                              "path": "/api/v1/tutor-profile",
                                              "timestamp": "2026-11-08T21:45:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Currency missing", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "VALIDATION_FAILED",
                                              "message": "Validation failed",
                                              "path": "/api/v1/tutor-profile",
                                              "timestamp": "2026-11-08T21:45:00Z",
                                              "errors": [
                                                { "field": "currencyProvidedWithRate", "message": "Currency is required when an hourly rate is set" }
                                              ]
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not a tutor account", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<TutorProfileResponseDto>> createTutorProfile(
            @Parameter(description = "Unique key for this creation attempt, 8-100 characters. Reuse the same key "
                    + "when retrying the same request.",
                    required = true, example = "3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(description = "Profile details as JSON")
            @Valid @RequestPart("data") CreateTutorProfileRequestDto data,
            @Parameter(description = "Optional profile picture (JPEG, PNG, WebP or GIF, max 10 MB)")
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture,
            HttpServletRequest request) {

        TutorProfileResponseDto created =
                tutorProfileService.createTutorProfile(idempotencyKey, data, profilePicture);

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "Your tutor profile has been created successfully and is awaiting approval.",
                created,
                request.getRequestURI()));
    }

    @GetMapping
    @PreAuthorize(STAFF)
    @Operation(
            summary = "List tutor profiles (staff only)",
            description = "Returns a page of profile summaries, newest first. Page size is capped at 100. "
                    + "Use /search to filter or sort. Results are cached for up to 5 minutes."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page of tutor profiles returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to view tutor profiles",
                    content = @Content)
    })
    public ResponseEntity<APISuccessResponse<PagedResponseDto<TutorProfileSummaryResponseDto>>> fetchAllTutorProfiles(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        PagedResponseDto<TutorProfileSummaryResponseDto> profiles =
                tutorProfileService.fetchAllTutorProfiles(page, size);

        String message = profiles.totalElements() == 0
                ? "No tutor profiles found."
                : "Tutor profiles retrieved successfully. Showing " + profiles.numberOfElements()
                  + " of " + profiles.totalElements() + " profile(s).";

        return ResponseEntity.ok(APISuccessResponse.ok(message, profiles, request.getRequestURI()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Get a tutor profile by ID (staff only)",
            description = "Returns the full profile, including a fresh link to the profile picture. "
                    + "Results are cached for up to 5 minutes."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tutor profile returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Tutor profile not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Not found", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "BAD_REQUEST",
                                      "message": "Tutor profile not found",
                                      "path": "/api/v1/tutor-profile/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                      "timestamp": "2026-11-08T21:50:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to view tutor profiles",
                    content = @Content)
    })
    public ResponseEntity<APISuccessResponse<TutorProfileResponseDto>> fetchTutorProfile(
            @Parameter(description = "ID of the tutor profile", required = true)
            @PathVariable UUID id,
            HttpServletRequest request) {

        TutorProfileResponseDto profile = tutorProfileService.fetchTutorProfile(id);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Tutor profile retrieved successfully.",
                profile,
                request.getRequestURI()));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Update a tutor profile",
            description = "Multipart request: an optional JSON part 'data' and an optional 'profilePicture' file. "
                    + "Only the fields you send are changed. A list you send (subjects, languages, certifications, "
                    + "hobbies) replaces the stored list, and an empty list clears it. A new picture replaces the "
                    + "old one. Allowed for the profile's owner and for admins."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tutor profile updated",
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
                                              "message": "You can only manage your own tutor profile",
                                              "path": "/api/v1/tutor-profile/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                              "timestamp": "2026-11-08T21:55:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Rate without currency", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Currency is required when an hourly rate is set",
                                              "path": "/api/v1/tutor-profile/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                              "timestamp": "2026-11-08T21:55:00Z"
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<TutorProfileResponseDto>> updateTutorProfile(
            @Parameter(description = "ID of the tutor profile to update", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Fields to change, as JSON")
            @Valid @RequestPart(value = "data", required = false) UpdateTutorProfileRequestDto data,
            @Parameter(description = "Optional new profile picture (JPEG, PNG, WebP or GIF, max 10 MB)")
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture,
            HttpServletRequest request) {

        TutorProfileResponseDto updated = tutorProfileService.updateTutorProfile(id, data, profilePicture);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Tutor profile updated successfully.",
                updated,
                request.getRequestURI()));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a tutor profile",
            description = "Permanently deletes the profile and its profile picture. "
                    + "Allowed for the profile's owner and for admins. This cannot be undone."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tutor profile deleted",
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
                                      "message": "Tutor profile not found",
                                      "path": "/api/v1/tutor-profile/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                      "timestamp": "2026-11-08T22:00:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<Void>> deleteTutorProfile(
            @Parameter(description = "ID of the tutor profile to delete", required = true)
            @PathVariable UUID id,
            HttpServletRequest request) {

        tutorProfileService.deleteTutorProfile(id);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Tutor profile deleted successfully.",
                request.getRequestURI()));
    }

    @GetMapping("/count")
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Count all tutor profiles (staff only)",
            description = "Returns the total number of tutor profiles. The value is cached for up to 5 minutes "
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
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to view tutor profiles",
                    content = @Content)
    })
    public ResponseEntity<APISuccessResponse<Long>> countTutorProfiles(HttpServletRequest request) {
        long total = tutorProfileService.countTutorProfiles();

        return ResponseEntity.ok(APISuccessResponse.ok(
                total == 1 ? "There is 1 tutor profile." : "There are " + total + " tutor profiles.",
                total,
                request.getRequestURI()));
    }

    @GetMapping("/download")
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Download tutor profiles as a PDF (staff only)",
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
                                              "message": "No tutor profiles match the selected filters",
                                              "path": "/api/v1/tutor-profile/download",
                                              "timestamp": "2026-11-08T22:05:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Too many rows", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Too many profiles to export (7210). Narrow your filters to at most 5000.",
                                              "path": "/api/v1/tutor-profile/download",
                                              "timestamp": "2026-11-08T22:05:00Z"
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to export tutor profiles",
                    content = @Content)
    })
    public ResponseEntity<byte[]> downloadTutorProfiles(
            @ParameterObject @Valid TutorProfilePagedQueryDto query) {

        byte[] pdf = tutorProfileService.downloadTutorProfiles(query);
        String fileName = "tutor-profiles-" + FILE_STAMP.format(Instant.now()) + ".pdf";

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
            summary = "Search tutor profiles (staff only)",
            description = "Combine a keyword (matches name, email and tutor number) with any of: faculty, "
                    + "department, field of specialization, a subject taught, minimum years of experience, "
                    + "status and a joined date range. Supports sorting and pagination (page size capped at 100). "
                    + "Results are cached for up to 5 minutes."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page of matching tutor profiles returned",
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
                                      "path": "/api/v1/tutor-profile/search",
                                      "timestamp": "2026-11-08T22:10:00Z",
                                      "errors": [
                                        { "field": "joinedRangeValid", "message": "joinedFrom cannot be after joinedTo" }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to view tutor profiles",
                    content = @Content)
    })
    public ResponseEntity<APISuccessResponse<PagedResponseDto<TutorProfileSummaryResponseDto>>> searchTutorProfiles(
            @ParameterObject @Valid TutorProfilePagedQueryDto query,
            HttpServletRequest request) {

        PagedResponseDto<TutorProfileSummaryResponseDto> results =
                tutorProfileService.searchTutorProfiles(query);

        String message = results.totalElements() == 0
                ? "No tutor profiles match your search."
                : "Found " + results.totalElements() + " tutor profile(s). Showing "
                  + results.numberOfElements() + " on this page.";

        return ResponseEntity.ok(APISuccessResponse.ok(message, results, request.getRequestURI()));
    }
}