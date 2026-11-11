package com.learningLadder.learningLadder.controller.partnersController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.partners.CreatePartnersRequestDto;
import com.learningLadder.learningLadder.dto.partners.PagedQueryRequestDto;
import com.learningLadder.learningLadder.dto.partners.PartnerPublicPageResponseDto;
import com.learningLadder.learningLadder.dto.partners.PartnerPublicResponseDto;
import com.learningLadder.learningLadder.dto.partners.PartnersPageResponseDto;
import com.learningLadder.learningLadder.dto.partners.PartnersResponseDto;
import com.learningLadder.learningLadder.dto.partners.UpdatePartnerRequestDto;
import com.learningLadder.learningLadder.enums.partnerStatus.PartnerStatus;
import com.learningLadder.learningLadder.service.partnersService.PartnersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
@RequestMapping("/api/${app.api.version}/partners")
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "Partnership Management Endpoints",
        description = """
                Learning Ladder partners and sponsors (academic, corporate, government, NGO, community, media).
                Staff manage partnerships, agreements and sponsorship tiers; the public website lists active partners.
                Logos and banners are resized and uploaded in the background.
                Read endpoints are cached; write endpoints refresh the cache automatically."""
)
public class PartnersController {

    private static final String STAFF = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'IT_ADMIN')";

    private final PartnersService partnersService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Add a partner or sponsor",
            description = """
                    Multipart request:
                    - **data**: the partner as JSON (content type application/json)
                    - **logo**: optional JPEG/PNG/WEBP, max 5 MB (resized to 512 px)
                    - **banner**: optional JPEG/PNG/WEBP, max 5 MB (resized to 1920 px)

                    Images are validated immediately, then resized and uploaded in the background.
                    Sponsors (partnerType SPONSOR) need a sponsorshipTier. New partners are PENDING by default
                    and appear on the website once ACTIVE. Upload the signed agreement first through the
                    file endpoint and pass its id as agreementDocumentId.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Partner added"),
            @ApiResponse(responseCode = "400", description = "Validation failed or invalid image",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Agreement document not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Name or slug already used",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PartnersResponseDto>> addPartnership(
            @Valid @RequestPart("data") CreatePartnersRequestDto data,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestPart(value = "banner", required = false) MultipartFile banner,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        PartnersResponseDto partner = partnersService.addPartnership(data, logo, banner, currentUserId);

        String role = partner.sponsorshipTier() != null
                ? partner.sponsorshipTier() + " sponsor"
                : partner.partnerType() + " partner";
        String visibility = partner.status() == PartnerStatus.ACTIVE && partner.showOnWebsite()
                ? " It is now visible on the website."
                : " It will appear on the website once its status is ACTIVE and showOnWebsite is true.";

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "\"" + partner.name() + "\" added as " + role + "." + visibility, partner, currentPath()));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Update a partner or sponsor",
            description = """
                    Partial update. Multipart with:
                    - **data**: JSON with only the fields to change (send {} to change only images)
                    - **logo** / **banner**: optional new images (old ones are deleted after the upload)

                    removeSponsorshipTier=true clears the tier; removeAgreementEndDate=true makes the agreement
                    open-ended. Rules are re-checked on the final result (dates, sponsors need a tier).""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partner updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed or invalid image",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Partner or agreement document not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Name/slug taken or concurrent change",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PartnersResponseDto>> updatePartnership(
            @Parameter(description = "Partner id") @PathVariable UUID id,
            @Valid @RequestPart("data") UpdatePartnerRequestDto data,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {

        PartnersResponseDto partner = partnersService.updatePartnership(id, data, logo, banner);

        String message;
        if (data.status() == PartnerStatus.ACTIVE) {
            message = "\"" + partner.name() + "\" is now an active partnership"
                    + (partner.showOnWebsite() ? " and visible on the website" : "");
        } else if (data.status() != null) {
            message = "\"" + partner.name() + "\" is now " + data.status() + " and no longer shown on the website";
        } else if (Boolean.FALSE.equals(data.showOnWebsite())) {
            message = "\"" + partner.name() + "\" updated and hidden from the website";
        } else {
            message = "\"" + partner.name() + "\" updated successfully";
        }
        if (logo != null || banner != null) {
            message += ". New images are being processed and will appear shortly.";
        }

        return ResponseEntity.ok(APISuccessResponse.ok(message, partner, currentPath()));
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Delete a partner or sponsor",
            description = "Deletes the partner and removes its logo and banner from storage. "
                    + "The signed agreement file is kept. To keep the history, set status to INACTIVE instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Partner deleted"),
            @ApiResponse(responseCode = "403", description = "Not staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Partner not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Void>> deletePartnership(
            @Parameter(description = "Partner id") @PathVariable UUID id) {

        partnersService.deletePartnership(id);

        return ResponseEntity.ok(APISuccessResponse.ok("Partner deleted successfully", currentPath()));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Fetch all partners (staff view)",
            description = "All partners in any status, featured first. Includes contact, agreement and amounts. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of partners"),
            @ApiResponse(responseCode = "403", description = "Not staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PartnersPageResponseDto>> fetchAllPartnerships(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        PartnersPageResponseDto result = partnersService.fetchAllPartnerships(page, size);
        String message = result.totalElements() == 0
                ? "No partners have been added yet"
                : "Partners fetched successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Search partners (staff view)",
            description = """
                    Filter by keyword (name, short description, industry), partnerType, sponsorshipTier, status,
                    industry, country, featured, showOnWebsite, sponsorsOnly and agreement end-date range.
                    Example, agreements to renew this year: status=ACTIVE&agreementEndTo=2026-12-31.
                    sortBy: displayOrder, name, createdAt, updatedAt, agreementEndDate, sponsorshipAmount. Cached.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching partners"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PartnersPageResponseDto>> searchPartnerships(
            @Valid @ParameterObject @ModelAttribute PagedQueryRequestDto query) {

        PartnersPageResponseDto result = partnersService.searchPartnerships(query);
        String message = result.totalElements() == 0
                ? "No partners match your search"
                : "Search completed: " + result.totalElements() + " partner(s) found";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Count partners", description = "Total number of partners and sponsors, any status. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Total"),
            @ApiResponse(responseCode = "403", description = "Not staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Long>> countTotalNumberOfPartnerships() {
        long total = partnersService.countTotalNumberOfPartnerships();

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Learning Ladder has " + total + " partner(s) and sponsor(s)", total, currentPath()));
    }

    @GetMapping(value = "/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Download partners as PDF",
            description = "Confidential staff report with the same filters and sorting as /search "
                    + "(page and size ignored). Includes agreement end dates and sponsorship amounts. Max 2,000 rows. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF report",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<byte[]> downloadPartnerships(
            @Valid @ParameterObject @ModelAttribute PagedQueryRequestDto query) {

        byte[] pdf = partnersService.downloadPartnerships(query);
        String fileName = "partners-" + LocalDate.now() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(pdf);
    }


    @GetMapping(value = "/public", produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Partners & sponsors for the website",
            description = "Active partners marked showOnWebsite, featured first. Public fields only. No login required. Cached.")
    @ApiResponse(responseCode = "200", description = "Page of partners")
    public ResponseEntity<APISuccessResponse<PartnerPublicPageResponseDto>> fetchPublicPartnerships(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        PartnerPublicPageResponseDto result = partnersService.fetchPublicPartnerships(page, size);
        String message = result.totalElements() == 0
                ? "No partners to show yet"
                : result.totalElements() + " partner(s) and sponsor(s)";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/public/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Search partners on the website",
            description = "Same filters as the staff search, but status and visibility are always forced to "
                    + "ACTIVE and visible. Example: sponsorsOnly=true&sponsorshipTier=GOLD. No login required. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching partners"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PartnerPublicPageResponseDto>> searchPublicPartnerships(
            @Valid @ParameterObject @ModelAttribute PagedQueryRequestDto query) {

        PartnerPublicPageResponseDto result = partnersService.searchPublicPartnerships(query);
        String message = result.totalElements() == 0
                ? "No partners match your search"
                : result.totalElements() + " partner(s) found";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/public/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Partner page for the website",
            description = "One active partner by slug, e.g. /partners/public/orange-foundation. No login required. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The partner"),
            @ApiResponse(responseCode = "404", description = "No active, visible partner with this slug",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PartnerPublicResponseDto>> fetchPublicPartnershipBySlug(
            @Parameter(description = "Partner slug", example = "orange-foundation") @PathVariable String slug) {

        PartnerPublicResponseDto partner = partnersService.fetchPublicPartnershipBySlug(slug);

        return ResponseEntity.ok(APISuccessResponse.ok("Partner fetched successfully", partner, currentPath()));
    }


    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Fetch a partner (staff view)",
            description = "Full details incl. contact, agreement and amounts. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The partner"),
            @ApiResponse(responseCode = "400", description = "Id is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Partner not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PartnersResponseDto>> fetchPartnership(
            @Parameter(description = "Partner id") @PathVariable UUID id) {

        PartnersResponseDto partner = partnersService.fetchPartnership(id);

        Long daysLeft = partner.daysUntilAgreementEnds();
        String message;
        if (daysLeft != null && daysLeft < 0) {
            message = "Partner fetched. The agreement ended " + (-daysLeft) + " day(s) ago.";
        } else if (daysLeft != null && daysLeft <= 30) {
            message = "Partner fetched. The agreement ends in " + daysLeft + " day(s): consider renewing it.";
        } else {
            message = "Partner fetched successfully";
        }

        return ResponseEntity.ok(APISuccessResponse.ok(message, partner, currentPath()));
    }

    private static String currentPath() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
    }
}
