package com.learningLadder.learningLadder.controller.jobApplicationController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.jobApplicationDto.CreateJobApplicationRequestDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.JobApplicationPageResponseDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.JobApplicationResponseDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.PagedQueryApplicationRequestDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.UpdateJobApplicationRequestDto;
import com.learningLadder.learningLadder.service.jobApplicationService.JobApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
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
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/${app.api.version}/job-application")
@SecurityRequirement(name = "bearerAuth") // must match the scheme name in your swaggerConfig
@Tag(
        name = "Job Application Management Endpoints",
        description = "Submit, update, delete, search, count and export job applications. "
                + "Read endpoints are cached; write endpoints refresh the cache automatically. "
                + "Successful calls return APISuccessResponse; failures return APIErrorResponse."
)
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Submit a job application",
            description = """
                    Multipart request with two parts:
                    - **data**: the application as JSON (content type application/json)
                    - **profilePic**: optional JPEG/PNG/WEBP image, max 5 MB

                    The profile picture is uploaded to storage in the background; a failed upload
                    never prevents the application from being saved.
                    Send a unique **Idempotency-Key** header per submission: repeating the same key
                    returns the original application instead of creating a duplicate.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application submitted"),
            @ApiResponse(responseCode = "400", description = "Validation failed, invalid image or missing Idempotency-Key",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job listing or file not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Applicant already applied or Idempotency-Key reused",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<JobApplicationResponseDto>> submitJobApplication(
            @Valid @RequestPart("data") CreateJobApplicationRequestDto data,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic,
            @Parameter(in = ParameterIn.HEADER, required = true,
                    description = "Unique key per submission (e.g. a UUID generated by the client)",
                    example = "3f6c1d2e-9a4b-4c1e-8f2a-1b2c3d4e5f60")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        JobApplicationResponseDto application =
                jobApplicationService.submitJobApplication(data, profilePic, idempotencyKey, currentUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                APISuccessResponse.created("Job application submitted successfully", application, currentPath()));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PostAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'IT_ADMIN') or returnObject.body.data().applicantId() == authentication.principal.id")
    @Operation(summary = "Fetch all job applications",
            description = "Paginated list, newest submissions first. Results are cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of applications"),
            @ApiResponse(responseCode = "400", description = "Invalid page or size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<JobApplicationPageResponseDto>> fetchAllJobApplications(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        JobApplicationPageResponseDto result = jobApplicationService.fetchAllJobApplications(page, size);
        String message = result.totalElements() == 0
                ? "No job applications found"
                : "Job applications fetched successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'IT_ADMIN')")
    @Operation(summary = "Count job applications", description = "Total number of job applications. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Total count"),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Long>> countTotalJobApplications() {
        long total = jobApplicationService.countTotalJobApplications();

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Total job applications counted successfully", total, currentPath()));
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'IT_ADMIN')")
    @Operation(
            summary = "Search job applications",
            description = """
                    Filter by job listing, applicant, status, submission date range and keyword
                    (first name, last name or email), with sorting and pagination.
                    sortBy: createdAt, submittedAt, updatedAt, status, lastName, yearsOfExperience.
                    Dates use ISO-8601, e.g. 2026-11-01T00:00:00Z. Results are cached.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching applications"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<JobApplicationPageResponseDto>> searchJobApplications(
            @Valid @ParameterObject @ModelAttribute PagedQueryApplicationRequestDto query) {

        JobApplicationPageResponseDto result = jobApplicationService.searchJobApplications(query);
        String message = result.totalElements() == 0
                ? "No job applications match your search"
                : "Search completed: " + result.totalElements() + " job application(s) found";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Download job applications as PDF",
            description = "Uses the same filters and sorting as /search (page and size are ignored). "
                    + "Exports at most 2,000 rows. The generated PDF is cached. "
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
    public ResponseEntity<byte[]> downloadJobApplications(
            @Valid @ParameterObject @ModelAttribute PagedQueryApplicationRequestDto query) {

        byte[] pdf = jobApplicationService.downloadJobApplications(query);
        String fileName = "job-applications-" + LocalDate.now() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(pdf);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PostAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'IT_ADMIN') or returnObject.body.data().applicantId() == authentication.principal.id")
    @Operation(summary = "Fetch a job application",
            description = "Admins can view any application; applicants only their own. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The application"),
            @ApiResponse(responseCode = "400", description = "Id is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<JobApplicationResponseDto>> fetchJobApplication(
            @Parameter(description = "Job application id") @PathVariable UUID id) {

        JobApplicationResponseDto application = jobApplicationService.fetchJobApplication(id);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Job application fetched successfully", application, currentPath()));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Update a job application",
            description = """
                    Partial update by the applicant while the application is still SUBMITTED.
                    - **data**: JSON with only the fields to change (send {} to change only the picture)
                    - **profilePic**: optional new image; uploaded in the background

                    For supportingDocumentIds: omit to keep, [] to remove all.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed or invalid image",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the owner of this application",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application or file not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Application is no longer editable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<JobApplicationResponseDto>> updateJobApplication(
            @Parameter(description = "Job application id") @PathVariable UUID id,
            @Valid @RequestPart("data") UpdateJobApplicationRequestDto data,
            @RequestPart(value = "profilePic", required = false) MultipartFile profilePic,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        JobApplicationResponseDto application =
                jobApplicationService.updateJobApplication(id, data, profilePic, currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Job application updated successfully", application, currentPath()));
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'IT_ADMIN')")
    @Operation(summary = "Delete a job application",
            description = "Deletes the application; its profile picture is removed from storage in the background.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application deleted"),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Void>> deleteJobApplication(
            @Parameter(description = "Job application id") @PathVariable UUID id) {

        jobApplicationService.deleteJobApplication(id);

        return ResponseEntity.ok(APISuccessResponse.ok("Job application deleted successfully", currentPath()));
    }

    private static String currentPath() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
    }
}