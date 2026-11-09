package com.learningLadder.learningLadder.controller.jobListingController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.courseDto.PagedResponseDto;
import com.learningLadder.learningLadder.dto.jobListingDto.CreateJobListingRequestDto;
import com.learningLadder.learningLadder.dto.jobListingDto.JobListingPagedQueryDto;
import com.learningLadder.learningLadder.dto.jobListingDto.JobListingPdfDto;
import com.learningLadder.learningLadder.dto.jobListingDto.JobListingResponseDto;
import com.learningLadder.learningLadder.dto.jobListingDto.JobListingSummaryResponseDto;
import com.learningLadder.learningLadder.dto.jobListingDto.UpdateJobListingRequestDto;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.service.jobListingService.JobListingService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/${app.api.version}/job-listing")
@Tag(name = "Job Listing Management Endpoints",
        description = "Create, view, update, delete, search and export job listings")
@SecurityRequirement(name = "bearerAuth")
public class JobListingController {

    private static final String MANAGERS = "hasAnyRole('SUPER_ADMIN','ADMIN','IT_ADMIN')";

    private final JobListingService jobListingService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGERS)
    @Operation(
            summary = "Create a job listing",
            description = "Multipart request: a JSON part named 'data' with the listing details and an optional "
                    + "'photo' image file. New listings start as DRAFT. The photo is uploaded in the background "
                    + "after the listing is saved, so it may appear a moment after this call returns."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Job listing created as a draft",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed, salary range or dates inconsistent, or the photo is not an "
                            + "allowed image type",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Validation failed", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "VALIDATION_FAILED",
                                              "message": "Validation failed",
                                              "path": "/api/v1/job-listing",
                                              "timestamp": "2026-11-09T09:30:00Z",
                                              "errors": [
                                                { "field": "title", "message": "Title is required", "rejectedValue": "" }
                                              ]
                                            }
                                            """),
                                    @ExampleObject(name = "Salary range invalid", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Minimum salary cannot exceed maximum salary",
                                              "path": "/api/v1/job-listing",
                                              "timestamp": "2026-11-09T09:30:00Z"
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to manage job listings",
                    content = @Content),
            @ApiResponse(
                    responseCode = "413",
                    description = "Photo is too large",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Photo too large", value = """
                                    {
                                      "success": false,
                                      "status": 413,
                                      "error": "Payload Too Large",
                                      "errorCode": "PAYLOAD_TOO_LARGE",
                                      "message": "Photo is too large",
                                      "path": "/api/v1/job-listing",
                                      "timestamp": "2026-11-09T09:30:00Z"
                                    }
                                    """)))
    })
    public ResponseEntity<APISuccessResponse<JobListingResponseDto>> createJobListing(
            @Parameter(description = "Listing details as JSON")
            @Valid @RequestPart("data") CreateJobListingRequestDto data,
            @Parameter(description = "Optional job photo (an allowed image type, within the image size limit)")
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @Parameter(hidden = true) Authentication authentication,
            HttpServletRequest request) {

        JobListingResponseDto created =
                jobListingService.createJobListing(data, photo, currentUserId(authentication));

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "Job listing created successfully as a draft.",
                created,
                request.getRequestURI()));
    }

    @GetMapping
    @Operation(
            summary = "List job listings",
            description = "Returns a page of listing summaries, newest first. Page size is capped at 100. "
                    + "Use /search to filter or sort. Results are cached and refreshed whenever a listing "
                    + "is created, updated or deleted."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page of job listings returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<PagedResponseDto<JobListingSummaryResponseDto>>> fetchAllJobListings(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        PagedResponseDto<JobListingSummaryResponseDto> listings =
                jobListingService.fetchAllJobListings(page, size);

        String message = listings.totalElements() == 0
                ? "No job listings found."
                : "Job listings retrieved successfully. Showing " + listings.totalElements()
                  + " of " + listings.totalElements() + " listing(s).";

        return ResponseEntity.ok(APISuccessResponse.ok(message, listings, request.getRequestURI()));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a job listing by ID",
            description = "Returns the full listing. Results are cached and refreshed whenever a listing "
                    + "is created, updated or deleted."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Job listing returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Job listing not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Not found", value = """
                                    {
                                      "success": false,
                                      "status": 404,
                                      "error": "Not Found",
                                      "errorCode": "NOT_FOUND",
                                      "message": "Job listing not found",
                                      "path": "/api/v1/job-listing/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                      "timestamp": "2026-11-09T09:35:00Z"
                                    }
                                    """)))
    })
    public ResponseEntity<APISuccessResponse<JobListingResponseDto>> fetchJobListing(
            @Parameter(description = "ID of the job listing", required = true)
            @PathVariable UUID id,
            HttpServletRequest request) {

        JobListingResponseDto listing = jobListingService.fetchJobListing(id);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Job listing retrieved successfully.",
                listing,
                request.getRequestURI()));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGERS)
    @Operation(
            summary = "Update a job listing",
            description = "Multipart request: an optional JSON part 'data' and an optional 'photo' image file. "
                    + "Only the fields you send are changed. The salary range and dates are checked against "
                    + "the listing as it will look after the update. A new photo is uploaded in the background "
                    + "and replaces the old one."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Job listing updated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed, salary range or dates inconsistent, or the photo is not an "
                            + "allowed image type",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Start date before deadline", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "BAD_REQUEST",
                                      "message": "Expected start date cannot be before the application deadline",
                                      "path": "/api/v1/job-listing/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                      "timestamp": "2026-11-09T09:40:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to manage job listings",
                    content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Job listing not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Not found", value = """
                                    {
                                      "success": false,
                                      "status": 404,
                                      "error": "Not Found",
                                      "errorCode": "NOT_FOUND",
                                      "message": "Job listing not found",
                                      "path": "/api/v1/job-listing/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                      "timestamp": "2026-11-09T09:40:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "413", description = "Photo is too large", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<JobListingResponseDto>> updateJobListing(
            @Parameter(description = "ID of the job listing to update", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Fields to change, as JSON")
            @Valid @RequestPart(value = "data", required = false) UpdateJobListingRequestDto data,
            @Parameter(description = "Optional new job photo (an allowed image type, within the image size limit)")
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            HttpServletRequest request) {

        JobListingResponseDto updated = jobListingService.updateJobListing(id, data, photo);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Job listing updated successfully.",
                updated,
                request.getRequestURI()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGERS)
    @Operation(
            summary = "Delete a job listing",
            description = "Permanently deletes the listing. Its photo is removed from storage once the "
                    + "deletion is committed. This cannot be undone."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Job listing deleted",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not allowed to manage job listings",
                    content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Job listing not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Not found", value = """
                                    {
                                      "success": false,
                                      "status": 404,
                                      "error": "Not Found",
                                      "errorCode": "NOT_FOUND",
                                      "message": "Job listing not found",
                                      "path": "/api/v1/job-listing/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                      "timestamp": "2026-11-09T09:45:00Z"
                                    }
                                    """)))
    })
    public ResponseEntity<APISuccessResponse<Void>> deleteJobListing(
            @Parameter(description = "ID of the job listing to delete", required = true)
            @PathVariable UUID id,
            HttpServletRequest request) {

        jobListingService.deleteJobListing(id);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Job listing deleted successfully.",
                request.getRequestURI()));
    }

    @GetMapping("/count")
    @Operation(
            summary = "Count all job listings",
            description = "Returns the total number of job listings. The value is cached and refreshed "
                    + "whenever a listing is created, updated or deleted."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Total returned in the 'data' field",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<Long>> countJobListings(HttpServletRequest request) {
        long total = jobListingService.countToNumberOfJobListings();

        return ResponseEntity.ok(APISuccessResponse.ok(
                total == 1 ? "There is 1 job listing." : "There are " + total + " job listings.",
                total,
                request.getRequestURI()));
    }

    @GetMapping("/search")
    @Operation(
            summary = "Search job listings",
            description = "Combine the available filters with sorting and pagination (page size capped at 100). "
                    + "Results are cached and refreshed whenever a listing is created, updated or deleted."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page of matching job listings returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid filter, sort field or range",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Invalid sort field", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "VALIDATION_FAILED",
                                      "message": "Validation failed",
                                      "path": "/api/v1/job-listing/search",
                                      "timestamp": "2026-11-09T09:50:00Z",
                                      "errors": [
                                        { "field": "sortBy", "message": "Invalid sort field", "rejectedValue": "salary" }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<PagedResponseDto<JobListingSummaryResponseDto>>> searchJobListings(
            @ParameterObject @Valid JobListingPagedQueryDto query,
            HttpServletRequest request) {

        PagedResponseDto<JobListingSummaryResponseDto> results = jobListingService.searchJobListings(query);

        String message = results.totalElements() == 0
                ? "No job listings match your search."
                : "Found " + results.totalElements() + " job listing(s). Showing "
                  + results.totalElements() + " on this page.";

        return ResponseEntity.ok(APISuccessResponse.ok(message, results, request.getRequestURI()));
    }

    @GetMapping("/download")
    @Operation(
            summary = "Download job listings as a PDF",
            description = "Generates a PDF of the listings that match the optional filters and sorting "
                    + "(the same options as /search; page and size are ignored). "
                    + "The PDF holds at most the first 200 matches and states how many were found in total, "
                    + "so narrow the filters for larger sets."
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
                    description = "Invalid filters or the PDF could not be generated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "PDF failed", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "BAD_REQUEST",
                                      "message": "Failed to generate job listings PDF",
                                      "path": "/api/v1/job-listing/download",
                                      "timestamp": "2026-11-09T09:55:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
    })
    public ResponseEntity<byte[]> downloadJobListings(
            @ParameterObject @Valid JobListingPagedQueryDto query) {

        JobListingPdfDto pdf = jobListingService.downloadJobListingsPdf(query);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .contentLength(pdf.content().length)
                .body(pdf.content());
    }


    private UUID currentUserId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (BadRequestException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Could not identify the signed-in user");
        }
    }
}