package com.learningLadder.learningLadder.controller.LibraryController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.libraryLoanDto.*;
import com.learningLadder.learningLadder.service.libraryService.LibraryService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/${app.api.version}/library")
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "Library Management Endpoints",
        description = """
                Catalog: browse, search and manage books (/books).
                Borrowing (/loans): reserve -> issue -> [renew] -> return request -> confirm return,
                plus cancel, report lost, search, count and PDF export.
                Read endpoints are cached; write endpoints refresh the cache automatically.
                Successful calls return APISuccessResponse; failures return APIErrorResponse."""
)
public class LibraryController {

    private static final String STAFF = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'IT_ADMIN')";
    private static final Set<String> STAFF_AUTHORITIES = Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_IT_ADMIN");

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final LibraryService libraryService;

    @PostMapping(value = "/books", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Add a book to the catalog",
            description = """
                    Multipart request with two parts:
                    - **data**: the book as JSON (content type application/json)
                    - **coverImage**: optional JPEG/PNG/WEBP image, max 5 MB, uploaded in the background

                    Defaults: 1 copy, 14-day loan period, 2 renewals, no late fee. ISBN dashes are removed
                    and its check digit is verified.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Book added"),
            @ApiResponse(responseCode = "400", description = "Validation failed or invalid image",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Course not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ISBN already in the catalog",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryBookResponseDto>> addBook(
            @Valid @RequestPart("data") CreateLibraryBookRequestDto data,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage) {

        LibraryBookResponseDto book = libraryService.addBook(data, coverImage);

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "\"" + book.title() + "\" added to the catalog with " + book.totalCopies() + " cop"
                        + (book.totalCopies() == 1 ? "y" : "ies"),
                book, currentPath()));
    }

    @GetMapping(value = "/books", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch all books", description = "Paginated catalog sorted by title. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of books"),
            @ApiResponse(responseCode = "400", description = "Invalid page or size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryBookPageResponseDto>> fetchAllBooks(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        LibraryBookPageResponseDto result = libraryService.fetchAllBooks(page, size);
        String message = result.totalElements() == 0
                ? "The catalog is empty"
                : "Books fetched successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/books/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Search the catalog",
            description = """
                    Filter by keyword (title, author or ISBN), category, language, course,
                    availability (availableOnly=true) and publication year range.
                    Withdrawn books are hidden unless active=false is passed.
                    sortBy: title, author, publicationYear, createdAt, availableCopies, category. Cached.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching books"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryBookPageResponseDto>> searchBooks(
            @Valid @ParameterObject @ModelAttribute PagedQueryLibraryBookRequestDto query) {

        LibraryBookPageResponseDto result = libraryService.searchBooks(query);
        String message = result.totalElements() == 0
                ? "No books match your search"
                : "Search completed: " + result.totalElements() + " book(s) found";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/books/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Count books", description = "Number of titles in the catalog. Cached.")
    @ApiResponse(responseCode = "200", description = "Total titles")
    public ResponseEntity<APISuccessResponse<Long>> countTotalNumberOfBooks() {
        long total = libraryService.countTotalNumberOfBooks();

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Total books in the catalog counted successfully", total, currentPath()));
    }

    @GetMapping(value = "/books/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch a book", description = "Includes copiesOnLoan and availableToBorrow. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The book"),
            @ApiResponse(responseCode = "400", description = "Id is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryBookResponseDto>> fetchBook(
            @Parameter(description = "Book id") @PathVariable UUID id) {

        LibraryBookResponseDto book = libraryService.fetchBook(id);
        String message = book.availableToBorrow()
                ? "Book fetched successfully: " + book.availableCopies() + " cop"
                  + (book.availableCopies() == 1 ? "y" : "ies") + " available"
                : "Book fetched successfully: currently not available to borrow";

        return ResponseEntity.ok(APISuccessResponse.ok(message, book, currentPath()));
    }

    @PatchMapping(value = "/books/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Update a book",
            description = """
                    Partial update. Multipart with:
                    - **data**: JSON with only the fields to change (send {} to change only the cover)
                    - **coverImage**: optional new cover

                    totalCopies cannot go below the copies currently on loan.
                    Set active=false to withdraw a book while keeping its loan history.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed or invalid image",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Book or course not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ISBN taken, copies below loans, or concurrent change",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryBookResponseDto>> updateBook(
            @Parameter(description = "Book id") @PathVariable UUID id,
            @Valid @RequestPart("data") UpdateLibraryBookRequestDto data,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage) {

        LibraryBookResponseDto book = libraryService.updateBook(id, data, coverImage);
        String message = Boolean.FALSE.equals(data.active())
                ? "\"" + book.title() + "\" withdrawn from the catalog"
                : "\"" + book.title() + "\" updated successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, book, currentPath()));
    }

    @DeleteMapping(value = "/books/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Delete a book",
            description = "Only books that were never borrowed. Otherwise withdraw them with active=false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book deleted"),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Book has loan history",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Void>> deleteBook(
            @Parameter(description = "Book id") @PathVariable UUID id) {

        libraryService.deleteBook(id);

        return ResponseEntity.ok(APISuccessResponse.ok("Book deleted from the catalog successfully", currentPath()));
    }

    @PostMapping(value = "/loans", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Borrow (reserve) a book",
            description = """
                    Reserves a copy for 48 hours; a librarian issues it when the student picks it up.
                    Rules: max 5 active loans, the same book only once at a time, no unpaid fees.
                    Send a unique **Idempotency-Key** header per request.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Book reserved"),
            @ApiResponse(responseCode = "400", description = "Validation failed or missing Idempotency-Key",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a student or no student profile",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "No copies, limit reached, unpaid fees or already borrowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanResponseDto>> borrowBook(
            @Valid @RequestBody BorrowBookRequestDto request,
            @Parameter(in = ParameterIn.HEADER, required = true,
                    description = "Unique key per request (e.g. a UUID generated by the client)",
                    example = "c2a7e0f4-1b3d-4e5f-8a9b-0c1d2e3f4a5b")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        LibraryLoanResponseDto loan = libraryService.borrowBook(request, idempotencyKey, currentUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "\"" + loan.bookTitle() + "\" reserved (" + loan.loanReference() + "). "
                        + "Please pick it up at the library before " + format(loan.reservationExpiresAt(), DATE_TIME) + ".",
                loan, currentPath()));
    }

    @PatchMapping(value = "/loans/{id}/issue", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Issue a reserved book",
            description = "Librarian hands the book to the student: RESERVED -> BORROWED. "
                    + "Due date defaults to today + the book's loan period.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book issued"),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Not reserved or reservation expired",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanResponseDto>> issueBook(
            @Parameter(description = "Loan id") @PathVariable UUID id,
            @Valid @RequestBody IssueBookRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        LibraryLoanResponseDto loan = libraryService.issueBook(id, request, currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "\"" + loan.bookTitle() + "\" issued successfully. Due back on " + format(loan.dueAt(), DATE) + ".",
                loan, currentPath()));
    }

    @GetMapping(value = "/loans", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Fetch all borrowed books (loans)", description = "Paginated, newest first. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of loans"),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanPageResponseDto>> fetchAllBorrowedBooks(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        LibraryLoanPageResponseDto result = libraryService.fetchAllBorrowedBooks(page, size);
        String message = result.totalElements() == 0
                ? "No loans found"
                : "Borrowed books fetched successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/loans/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Search borrowed books (loans)",
            description = """
                    Filter by book, student, status, overdueOnly, unpaidFeesOnly, keyword (loan reference
                    or book title) and due-date range.
                    Tip: status=RETURN_REQUESTED lists the returns waiting for confirmation.
                    sortBy: createdAt, reservedAt, borrowedAt, dueAt, returnRequestedAt, returnedAt, status, lateFee. Cached.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching loans"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanPageResponseDto>> searchBorrowedBooks(
            @Valid @ParameterObject @ModelAttribute PagedQueryLibraryLoanRequestDto query) {

        LibraryLoanPageResponseDto result = libraryService.searchBorrowedBooks(query);
        String message = result.totalElements() == 0
                ? "No loans match your search"
                : "Search completed: " + result.totalElements() + " loan(s) found";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/loans/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Count borrowed books",
            description = "Copies currently out with students (BORROWED, OVERDUE, RETURN_REQUESTED). Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Copies out"),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Long>> countTotalNumberOfBorrowedBooks() {
        long total = libraryService.countTotalNumberOfBorrowedBooks();

        return ResponseEntity.ok(APISuccessResponse.ok(
                total + " book(s) currently out with students", total, currentPath()));
    }

    @GetMapping(value = "/loans/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Download borrowed books as PDF",
            description = "Same filters and sorting as /loans/search (page and size ignored). Max 2,000 rows. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF report",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<byte[]> downloadBorrowedBooks(
            @Valid @ParameterObject @ModelAttribute PagedQueryLibraryLoanRequestDto query) {

        byte[] pdf = libraryService.downloadBorrowedBooks(query);
        String fileName = "library-loans-" + LocalDate.now() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(pdf);
    }

    @GetMapping(value = "/loans/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF + " or @libraryLoanAccess.isBorrower(#id, authentication.principal.id)")
    @Operation(summary = "Fetch a borrowed book (loan)",
            description = "Staff can view any loan; students only their own. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The loan"),
            @ApiResponse(responseCode = "403", description = "Not allowed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanResponseDto>> fetchBorrowedBook(
            @Parameter(description = "Loan id") @PathVariable UUID id) {

        LibraryLoanResponseDto loan = libraryService.fetchBorrowedBook(id);
        String message = loan.overdue()
                ? "Loan fetched successfully. This book is " + loan.daysOverdue() + " day(s) overdue."
                : "Loan fetched successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, loan, currentPath()));
    }

    @PatchMapping(value = "/loans/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Update a borrowed book (loan)",
            description = "Librarian corrects an active loan: new due date, condition or a note. "
                    + "A new future due date clears the OVERDUE status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loan updated"),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Loan is not borrowed/overdue or changed concurrently",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanResponseDto>> updateBorrowedBook(
            @Parameter(description = "Loan id") @PathVariable UUID id,
            @Valid @RequestBody IssueBookRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        LibraryLoanResponseDto loan = libraryService.updateBorrowedBook(id, request, currentUserId);
        String message = request.dueAt() != null
                ? "Loan updated successfully. New due date: " + format(loan.dueAt(), DATE) + "."
                : "Loan updated successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, loan, currentPath()));
    }

    @PatchMapping(value = "/loans/{id}/renew", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Renew a borrowed book",
            description = "Extends the due date by the book's loan period. Not allowed when overdue "
                    + "or when the renewal limit is reached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loan renewed"),
            @ApiResponse(responseCode = "403", description = "Not your loan",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Overdue, not borrowed, or renewal limit reached",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanResponseDto>> renewBorrowedBook(
            @Parameter(description = "Loan id") @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        LibraryLoanResponseDto loan = libraryService.renewBorrowedBook(id, currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "\"" + loan.bookTitle() + "\" renewed successfully. New due date: " + format(loan.dueAt(), DATE)
                        + " (renewal " + loan.renewalCount() + ").",
                loan, currentPath()));
    }

    @PatchMapping(value = "/loans/{id}/return", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Return a borrowed book",
            description = "The student declares the book returned: -> RETURN_REQUESTED. "
                    + "Late fees stop accruing now; a librarian confirms once the copy is checked.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Return requested"),
            @ApiResponse(responseCode = "403", description = "Not your loan",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Book is not borrowed or overdue",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanResponseDto>> returnBorrowedBook(
            @Parameter(description = "Loan id") @PathVariable UUID id,
            @Valid @RequestBody ReturnBookToLibraryRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        LibraryLoanResponseDto loan = libraryService.returnBorrowedBook(id, request, currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Return of \"" + loan.bookTitle() + "\" recorded. A librarian will confirm once the book has been checked.",
                loan, currentPath()));
    }

    @PatchMapping(value = "/loans/{id}/confirm-return", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Confirm a returned book",
            description = "Librarian confirms the copy is back: -> RETURNED. The copy goes back on the shelf "
                    + "and the late fee is finalized (calculated, waived or overridden). Also works for desk returns.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Return confirmed"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Book is not out or return date invalid",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanResponseDto>> confirmReturnBorrowedBook(
            @Parameter(description = "Loan id") @PathVariable UUID id,
            @Valid @RequestBody ConfirmBookReturnRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        LibraryLoanResponseDto loan = libraryService.confirmReturnBorrowedBook(id, request, currentUserId);

        String message = "Return of \"" + loan.bookTitle() + "\" confirmed. "
                + (hasFee(loan.lateFee())
                ? "Late fee: " + loan.lateFee().toPlainString() + " " + loan.feeCurrency()
                  + (loan.feePaid() ? " (paid)." : " (unpaid).")
                : "No late fee.");

        return ResponseEntity.ok(APISuccessResponse.ok(message, loan, currentPath()));
    }

    @PatchMapping(value = "/loans/{id}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('STUDENT') or " + STAFF)
    @Operation(summary = "Cancel a reservation",
            description = "Students cancel their own reservations; staff can cancel any. The copy returns to the shelf.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation cancelled"),
            @ApiResponse(responseCode = "403", description = "Not your reservation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Loan is not a reservation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanResponseDto>> cancelReservation(
            @Parameter(description = "Loan id") @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        LibraryLoanResponseDto loan = libraryService.cancelReservation(id, currentUserId, isStaff(authentication));

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Reservation of \"" + loan.bookTitle() + "\" cancelled successfully", loan, currentPath()));
    }

    @PatchMapping(value = "/loans/{id}/report-lost", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('STUDENT') or " + STAFF)
    @Operation(summary = "Report a borrowed book as lost",
            description = "Students report their own loans; staff any loan. Late fees up to today are charged; "
                    + "staff can add a replacementFee (ignored for students). The copy is removed from the total.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book reported lost"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not your loan",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Book is not out",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<LibraryLoanResponseDto>> reportBookLost(
            @Parameter(description = "Loan id") @PathVariable UUID id,
            @Valid @RequestBody ReportBookLostRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        LibraryLoanResponseDto loan = libraryService.reportBookLost(id, request, currentUserId, isStaff(authentication));

        String message = "\"" + loan.bookTitle() + "\" reported lost. "
                + (hasFee(loan.lateFee())
                ? "Amount due: " + loan.lateFee().toPlainString() + " " + loan.feeCurrency() + "."
                : "No fee charged.");

        return ResponseEntity.ok(APISuccessResponse.ok(message, loan, currentPath()));
    }

    @DeleteMapping(value = "/loans/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Delete a loan record",
            description = "Only finished loans (RETURNED, LOST, CANCELLED). Returns 200 so the success envelope can be sent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loan deleted"),
            @ApiResponse(responseCode = "403", description = "Not library staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Loan is still active",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Void>> deleteBorrowedBook(
            @Parameter(description = "Loan id") @PathVariable UUID id) {

        libraryService.deleteBorrowedBook(id);

        return ResponseEntity.ok(APISuccessResponse.ok("Loan record deleted successfully", currentPath()));
    }


    private static String currentPath() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
    }

    private static boolean isStaff(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_AUTHORITIES::contains);
    }

    private static boolean hasFee(BigDecimal fee) {
        return fee != null && fee.signum() > 0;
    }

    private static String format(Instant instant, DateTimeFormatter formatter) {
        return instant == null ? "-" : formatter.format(instant);
    }
}