package com.learningLadder.learningLadder.controller.reviewController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.reviewDto.CreateReviewRequestDto;
import com.learningLadder.learningLadder.dto.reviewDto.ReviewPagedQueryDto;
import com.learningLadder.learningLadder.dto.reviewDto.ReviewResponseDto;
import com.learningLadder.learningLadder.dto.reviewDto.UpdateReviewRequestDto;
import com.learningLadder.learningLadder.dto.userDto.PagedResponseDto;
import com.learningLadder.learningLadder.service.reviewService.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/${app.api.version}/review")
@RequiredArgsConstructor
@Tag(name = "Review Management Endpoints",
        description = "Create, read, update, delete, moderate, search and export course reviews"
)
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(
            summary = "Add a course review",
            description = "Students only. Sent as multipart/form-data: the review JSON in the 'data' part and the "
                    + "reviewer's profile picture in the 'profilePicture' part (required). The Idempotency-Key "
                    + "header (8-100 characters) makes the request safe to retry. A student can review a course "
                    + "once. New reviews start as PENDING until a moderator approves them.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Review created"),
            @ApiResponse(responseCode = "400", description = "Validation failed, missing profile picture, course "
                    + "not found, duplicate review or idempotency key already used",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Only students can review a course",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<APISuccessResponse<ReviewResponseDto>> addReview(
            @Parameter(description = "Unique client-generated key (8-100 chars) to make the request idempotent",
                    required = true, example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(description = "Review details as JSON", required = true)
            @Valid @RequestPart("data") CreateReviewRequestDto data,
            @Parameter(description = "Reviewer's profile picture (image file)", required = true)
            @RequestPart("profilePicture") MultipartFile profilePicture,
            HttpServletRequest request) {
        ReviewResponseDto review = reviewService.addReview(idempotencyKey, data, profilePicture);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APISuccessResponse.created("Review submitted successfully and is awaiting approval",
                        review, request.getRequestURI()));
    }

    @Operation(
            summary = "List all reviews",
            description = "Returns a paginated list ordered by creation date (newest first). "
                    + "Page size is capped at 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of reviews retrieved"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<APISuccessResponse<PagedResponseDto<ReviewResponseDto>>> fetchAllReviews(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok(APISuccessResponse.ok("Reviews retrieved successfully",
                reviewService.fetchAllReviews(page, size), request.getRequestURI()));
    }

    @Operation(
            summary = "Search reviews",
            description = "Filter by keyword, course, student, rating (exact, minimum, maximum), status, verified "
                    + "enrollment, tutor reply and date range. Sort by createdAt, updatedAt, rating or "
                    + "helpfulCount, with pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching reviews retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    @GetMapping("/search")
    public ResponseEntity<APISuccessResponse<PagedResponseDto<ReviewResponseDto>>> searchReviews(
            @Valid @ParameterObject ReviewPagedQueryDto query,
            HttpServletRequest request) {
        return ResponseEntity.ok(APISuccessResponse.ok("Reviews retrieved successfully",
                reviewService.searchReviews(query), request.getRequestURI()));
    }

    @Operation(
            summary = "Download reviews as PDF",
            description = "Exports the reviews matching the given filters as a PDF file (returned as a binary "
                    + "attachment, not wrapped in the JSON envelope). Fails if nothing matches or if more than "
                    + "5,000 reviews match.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generated",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "No matching reviews, too many reviews, "
                    + "or invalid filters",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Moderator role required",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    @GetMapping(value = "/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReviews(
            @Valid @ParameterObject ReviewPagedQueryDto query) {
        byte[] pdf = reviewService.downloadReviews(query);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("reviews-" + LocalDate.now() + ".pdf").build());
        headers.setContentLength(pdf.length);

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @Operation(
            summary = "Count reviews",
            description = "Returns the total number of reviews.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Total count retrieved"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    @GetMapping("/count")
    public ResponseEntity<APISuccessResponse<Long>> countReviews(HttpServletRequest request) {
        return ResponseEntity.ok(APISuccessResponse.ok("Review count retrieved successfully",
                reviewService.countReviews(), request.getRequestURI()));
    }

    @Operation(summary = "Get a review by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review retrieved"),
            @ApiResponse(responseCode = "400", description = "Review not found or invalid ID",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<APISuccessResponse<ReviewResponseDto>> fetchReview(
            @Parameter(description = "Review ID", required = true) @PathVariable UUID id,
            HttpServletRequest request) {
        return ResponseEntity.ok(APISuccessResponse.ok("Review retrieved successfully",
                reviewService.fetchReview(id), request.getRequestURI()));
    }

    @Operation(
            summary = "Update a review",
            description = "Partial update: only the fields sent (rating, title, comment) are changed. "
                    + "Allowed for the review's author or a moderator.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed or review not found",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the review's author or a moderator",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    public ResponseEntity<APISuccessResponse<ReviewResponseDto>> updateReview(
            @Parameter(description = "Review ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateReviewRequestDto data,
            HttpServletRequest request) {
        return ResponseEntity.ok(APISuccessResponse.ok("Review updated successfully",
                reviewService.updateReview(id, data), request.getRequestURI()));
    }

    @Operation(
            summary = "Delete a review",
            description = "Allowed for the review's author or a moderator. Returns 200 with a confirmation "
                    + "message instead of 204 so the client receives the success envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review deleted"),
            @ApiResponse(responseCode = "400", description = "Review not found",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the review's author or a moderator",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<APISuccessResponse<Void>> deleteReview(
            @Parameter(description = "Review ID", required = true) @PathVariable UUID id,
            HttpServletRequest request) {
        reviewService.deleteReview(id);
        return ResponseEntity.ok(APISuccessResponse.ok("Review deleted successfully", request.getRequestURI()));
    }

    @Operation(
            summary = "Approve a review",
            description = "Moderators only. Marks the review as APPROVED and emails the student.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review approved"),
            @ApiResponse(responseCode = "400", description = "Review not found or already approved",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Moderator role required",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    @PatchMapping("/{id}/approve")
    public ResponseEntity<APISuccessResponse<ReviewResponseDto>> approveReview(
            @Parameter(description = "Review ID", required = true) @PathVariable UUID id,
            HttpServletRequest request) {
        return ResponseEntity.ok(APISuccessResponse.ok("Review approved successfully",
                reviewService.approveReview(id), request.getRequestURI()));
    }

    @Operation(
            summary = "Disapprove a review",
            description = "Moderators only. Marks the review as REJECTED and emails the student, "
                    + "including the optional reason (max 500 characters).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review disapproved"),
            @ApiResponse(responseCode = "400", description = "Review not found, already disapproved "
                    + "or reason too long",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Moderator role required",
                    content = @Content(schema = @Schema(implementation = APIErrorResponse.class)))
    })
    @PatchMapping("/{id}/disapprove")
    public ResponseEntity<APISuccessResponse<ReviewResponseDto>> disapproveReview(
            @Parameter(description = "Review ID", required = true) @PathVariable UUID id,
            @Parameter(description = "Optional reason shown to the student", example = "Contains inappropriate language")
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {
        return ResponseEntity.ok(APISuccessResponse.ok("Review disapproved successfully",
                reviewService.disapproveReview(id, reason), request.getRequestURI()));
    }
}