package com.learningLadder.learningLadder.controller.aiAssistController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.aiAssistDto.AIAssistPageResponseDto;
import com.learningLadder.learningLadder.dto.aiAssistDto.AIAssistResponseDto;
import com.learningLadder.learningLadder.dto.aiAssistDto.AIAssistSummaryPageResponseDto;
import com.learningLadder.learningLadder.dto.aiAssistDto.AIAssistSummaryResponseDto;
import com.learningLadder.learningLadder.dto.aiAssistDto.CreateAIAssistRequestDto;
import com.learningLadder.learningLadder.dto.aiAssistDto.PagedQueryAIAssistRequestDto;
import com.learningLadder.learningLadder.dto.aiAssistDto.UpdateAIAssistRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.AIConversationPageResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.AIConversationResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.AIMessagePageResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.AIMessageResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.ChatReplyResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.MessageFeedbackRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.PagedQueryAIConversationRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.PagedQueryAIMessageRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.SendMessageRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.StartConversationRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.UpdateConversationRequestDto;
import com.learningLadder.learningLadder.enums.aiMessageStatus.AIMessageStatus;
import com.learningLadder.learningLadder.service.aiAssistantService.AiAssistanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * AI assistants and AI chat.
 * <ul>
 *   <li>/assistants/admin/** : admins configure assistants (full view incl. system prompt)</li>
 *   <li>/assistants/**       : every signed-in user browses available assistants (public view)</li>
 *   <li>/conversations/**    : each user's own chats and messages (staff may read any)</li>
 *   <li>/messages/{id}/feedback : thumbs up / down on replies</li>
 * </ul>
 * Successful calls return {@link APISuccessResponse} (except the SSE stream);
 * failures return {@link APIErrorResponse} through the GlobalExceptionHandler.
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/${app.api.version}/ai-assist")
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "AI-Assistance Management Endpoints",
        description = """
                Learning Ladder AI assistants powered by Groq (OpenAI-compatible API).
                Admins configure assistants; students, tutors, parents and staff chat with them.
                Replies adapt to the user's role. Each assistant has a daily message limit per user.
                Use /messages for a complete reply or /messages/stream for a token-by-token reply (Server-Sent Events)."""
)
public class AiAssistController {

    /** Keep in sync with AI_ASSIST_ADMIN_ROLES in SecurityConfig. */
    private static final String ADMIN = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'IT_ADMIN')";

    private final AiAssistanceService aiAssistanceService;

    // #####################################################################
    //                     ASSISTANTS - ADMIN CONFIGURATION
    // #####################################################################

    @PostMapping(value = "/assistants", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN)
    @Operation(
            summary = "Create an AI assistant",
            description = """
                    Multipart request:
                    - **data**: the assistant as JSON (content type application/json)
                    - **logo**: optional JPEG/PNG/WEBP/SVG image, max 2 MB, uploaded in the background

                    Tip: GET /assistants/admin/prompt-template?purpose=TUTORING returns a starter system prompt.
                    The slug is generated from the name when omitted. Copy the model id from the Groq console.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Assistant created"),
            @ApiResponse(responseCode = "400", description = "Validation failed or invalid logo",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not an administrator",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Course not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Name or slug already used",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIAssistResponseDto>> createAssistant(
            @Valid @RequestPart("data") CreateAIAssistRequestDto data,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        AIAssistResponseDto assistant = aiAssistanceService.createAssistant(data, logo, currentUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "AI assistant \"" + assistant.name() + "\" created successfully and is now available to users",
                assistant, currentPath()));
    }

    @PatchMapping(value = "/assistants/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN)
    @Operation(
            summary = "Update an AI assistant",
            description = """
                    Partial update. Multipart with:
                    - **data**: JSON with only the fields to change (send {} to change only the logo)
                    - **logo**: optional new logo

                    active=false hides the assistant from users; existing conversations are kept.
                    Changes apply to the next message.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assistant updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed or invalid logo",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not an administrator",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Assistant or course not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Name/slug taken or concurrent change",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIAssistResponseDto>> updateAssistant(
            @Parameter(description = "Assistant id") @PathVariable UUID id,
            @Valid @RequestPart("data") UpdateAIAssistRequestDto data,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {

        AIAssistResponseDto assistant = aiAssistanceService.updateAssistant(id, data, logo);

        String message;
        if (Boolean.FALSE.equals(data.active())) {
            message = "AI assistant \"" + assistant.name() + "\" deactivated: users can no longer start chats with it";
        } else if (Boolean.TRUE.equals(data.active())) {
            message = "AI assistant \"" + assistant.name() + "\" activated and available to users";
        } else {
            message = "AI assistant \"" + assistant.name() + "\" updated successfully";
        }
        return ResponseEntity.ok(APISuccessResponse.ok(message, assistant, currentPath()));
    }

    @DeleteMapping(value = "/assistants/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN)
    @Operation(summary = "Delete an AI assistant",
            description = "Only assistants without conversations. Otherwise deactivate them with active=false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assistant deleted"),
            @ApiResponse(responseCode = "403", description = "Not an administrator",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Assistant not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Assistant has conversations",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Void>> deleteAssistant(
            @Parameter(description = "Assistant id") @PathVariable UUID id) {

        aiAssistanceService.deleteAssistant(id);

        return ResponseEntity.ok(APISuccessResponse.ok("AI assistant deleted successfully", currentPath()));
    }

    @GetMapping(value = "/assistants/admin", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN)
    @Operation(summary = "Fetch all assistants (admin view)",
            description = "Includes inactive assistants, system prompts and model settings. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of assistants"),
            @ApiResponse(responseCode = "403", description = "Not an administrator",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIAssistPageResponseDto>> fetchAllAssistants(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        AIAssistPageResponseDto result = aiAssistanceService.fetchAllAssistants(page, size);
        String message = result.totalElements() == 0
                ? "No AI assistants have been created yet"
                : "AI assistants fetched successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/assistants/admin/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN)
    @Operation(summary = "Search assistants (admin view)",
            description = "Filter by keyword (name, slug, description), purpose, provider, course and active "
                    + "(defaults to true; pass active=false for deactivated ones). "
                    + "sortBy: name, purpose, createdAt, updatedAt. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching assistants"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not an administrator",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIAssistPageResponseDto>> searchAssistants(
            @Valid @ParameterObject @ModelAttribute PagedQueryAIAssistRequestDto query) {

        AIAssistPageResponseDto result = aiAssistanceService.searchAssistants(query);
        String message = result.totalElements() == 0
                ? "No AI assistants match your search"
                : "Search completed: " + result.totalElements() + " assistant(s) found";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/assistants/admin/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN)
    @Operation(summary = "Count assistants", description = "Total number of assistants, active or not. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Total"),
            @ApiResponse(responseCode = "403", description = "Not an administrator",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Long>> countAssistants() {
        long total = aiAssistanceService.countAssistants();

        return ResponseEntity.ok(APISuccessResponse.ok("AI assistants counted successfully", total, currentPath()));
    }

    @GetMapping(value = "/assistants/admin/prompt-template", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN)
    @Operation(summary = "Get a starter system prompt",
            description = "Starter persona text to adapt when creating an assistant. "
                    + "purpose: TUTORING, LIBRARY_HELP, CAREER_ADVICE, ADMISSIONS, TEACHING_ASSISTANT (anything else returns a general template).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template text"),
            @ApiResponse(responseCode = "403", description = "Not an administrator",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<String>> getSystemPromptTemplate(
            @Parameter(description = "Assistant purpose", example = "TUTORING")
            @RequestParam(required = false) @Size(max = 50) String purpose) {

        String template = aiAssistanceService.getSystemPromptTemplate(purpose);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Starter system prompt" + (purpose == null ? "" : " for " + purpose.toUpperCase())
                        + ". Adapt it to your school before saving.",
                template, currentPath()));
    }

    @GetMapping(value = "/assistants/admin/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN)
    @Operation(summary = "Fetch an assistant (admin view)", description = "Full configuration. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The assistant"),
            @ApiResponse(responseCode = "403", description = "Not an administrator",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Assistant not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIAssistResponseDto>> fetchAssistant(
            @Parameter(description = "Assistant id") @PathVariable UUID id) {

        AIAssistResponseDto assistant = aiAssistanceService.fetchAssistant(id);

        return ResponseEntity.ok(APISuccessResponse.ok("AI assistant fetched successfully", assistant, currentPath()));
    }

    @GetMapping(value = "/assistants/admin/{id}/system-prompt-preview", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(ADMIN)
    @Operation(summary = "Preview the full system prompt",
            description = "Shows exactly what the model receives: persona + Learning Ladder knowledge + "
                    + "audience guidance + professional conduct rules. audience: STUDENT, TUTOR, PARENT, STAFF, GUEST.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Composed prompt"),
            @ApiResponse(responseCode = "400", description = "Unknown audience",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Assistant not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<String>> previewSystemPrompt(
            @Parameter(description = "Assistant id") @PathVariable UUID id,
            @Parameter(description = "Who the prompt is built for", example = "STUDENT")
            @RequestParam(defaultValue = "STUDENT")
            @Pattern(regexp = "^(?i)(STUDENT|TUTOR|PARENT|STAFF|GUEST)$",
                    message = "audience must be one of: STUDENT, TUTOR, PARENT, STAFF, GUEST") String audience) {

        String prompt = aiAssistanceService.previewSystemPrompt(id, audience);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "System prompt preview for audience " + audience.toUpperCase(), prompt, currentPath()));
    }

    // #####################################################################
    //                   ASSISTANTS - AVAILABLE TO ALL USERS
    // #####################################################################

    @GetMapping(value = "/assistants", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch available assistants",
            description = "Active assistants a user can chat with (public view, no system prompt). Cached.")
    @ApiResponse(responseCode = "200", description = "Page of assistants")
    public ResponseEntity<APISuccessResponse<AIAssistSummaryPageResponseDto>> fetchAvailableAssistants(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        AIAssistSummaryPageResponseDto result = aiAssistanceService.fetchAvailableAssistants(page, size);
        String message = result.totalElements() == 0
                ? "No AI assistants are available right now"
                : result.totalElements() + " AI assistant(s) available";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/assistants/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search available assistants",
            description = "Same filters as the admin search, but only active assistants are ever returned. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching assistants"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIAssistSummaryPageResponseDto>> searchAvailableAssistants(
            @Valid @ParameterObject @ModelAttribute PagedQueryAIAssistRequestDto query) {

        AIAssistSummaryPageResponseDto result = aiAssistanceService.searchAvailableAssistants(query);
        String message = result.totalElements() == 0
                ? "No AI assistants match your search"
                : "Search completed: " + result.totalElements() + " assistant(s) found";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/assistants/slug/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch an assistant by slug", description = "e.g. /assistants/slug/study-buddy. Cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The assistant"),
            @ApiResponse(responseCode = "404", description = "No active assistant with this slug",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIAssistSummaryResponseDto>> fetchAssistantBySlug(
            @Parameter(description = "Assistant slug", example = "study-buddy") @PathVariable String slug) {

        AIAssistSummaryResponseDto assistant = aiAssistanceService.fetchAssistantBySlug(slug);

        return ResponseEntity.ok(APISuccessResponse.ok("AI assistant fetched successfully", assistant, currentPath()));
    }

    @GetMapping(value = "/assistants/{id}/remaining-messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Messages left today",
            description = "How many messages the current user can still send to this assistant today (resets at midnight UTC).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Remaining messages"),
            @ApiResponse(responseCode = "404", description = "Assistant not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Integer>> remainingMessagesToday(
            @Parameter(description = "Assistant id") @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        int remaining = aiAssistanceService.remainingMessagesToday(id, currentUserId);
        String message = remaining == 0
                ? "You have used all your messages with this assistant for today. The limit resets at midnight (UTC)."
                : "You can send " + remaining + " more message(s) to this assistant today";

        return ResponseEntity.ok(APISuccessResponse.ok(message, remaining, currentPath()));
    }

    // #####################################################################
    //                             CONVERSATIONS
    // #####################################################################

    @PostMapping(value = "/conversations", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Start a conversation",
            description = "Creates a chat with an assistant. If firstMessage is given it is answered immediately "
                    + "and the reply is included; otherwise userMessage and assistantMessage are null. "
                    + "The title is generated from the first message when omitted.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversation started"),
            @ApiResponse(responseCode = "400", description = "Validation failed or message too long",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Assistant not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Assistant inactive or daily limit reached",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ChatReplyResponseDto>> startConversation(
            @Valid @RequestBody StartConversationRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        ChatReplyResponseDto result = aiAssistanceService.startConversation(
                request, currentUserId, authorities(authentication));

        String message = result.assistantMessage() == null
                ? "Conversation \"" + result.conversationTitle() + "\" started. Send your first message."
                : "Conversation started. " + replyMessage(result);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APISuccessResponse.created(message, result, currentPath()));
    }

    @GetMapping(value = "/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch my conversations",
            description = "Chat list: pinned first, then most recent activity. Archived chats are hidden unless "
                    + "archived=true. Users only see their own chats; staff may filter by any userId. Not cached.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of conversations"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort value",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIConversationPageResponseDto>> fetchConversations(
            @Valid @ParameterObject @ModelAttribute PagedQueryAIConversationRequestDto query,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        AIConversationPageResponseDto result = aiAssistanceService.fetchConversations(
                query, currentUserId, authorities(authentication));
        String message = result.totalElements() == 0
                ? (Boolean.TRUE.equals(query.archived()) ? "No archived conversations" : "No conversations yet. Start one with an assistant!")
                : "Conversations fetched successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @GetMapping(value = "/conversations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch a conversation", description = "Your own conversation (staff may read any).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The conversation"),
            @ApiResponse(responseCode = "403", description = "Not your conversation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIConversationResponseDto>> fetchConversation(
            @Parameter(description = "Conversation id") @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        AIConversationResponseDto conversation = aiAssistanceService.fetchConversation(
                id, currentUserId, authorities(authentication));

        return ResponseEntity.ok(APISuccessResponse.ok("Conversation fetched successfully", conversation, currentPath()));
    }

    @PatchMapping(value = "/conversations/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Rename, pin or archive a conversation",
            description = "Send only the fields to change. Only the owner can update a conversation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation updated"),
            @ApiResponse(responseCode = "403", description = "Not your conversation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Changed at the same time by a new message",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIConversationResponseDto>> updateConversation(
            @Parameter(description = "Conversation id") @PathVariable UUID id,
            @Valid @RequestBody UpdateConversationRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        AIConversationResponseDto conversation = aiAssistanceService.updateConversation(id, request, currentUserId);

        String message;
        if (Boolean.TRUE.equals(request.archived())) {
            message = "Conversation archived";
        } else if (Boolean.FALSE.equals(request.archived())) {
            message = "Conversation restored from the archive";
        } else if (Boolean.TRUE.equals(request.pinned())) {
            message = "Conversation pinned to the top";
        } else if (Boolean.FALSE.equals(request.pinned())) {
            message = "Conversation unpinned";
        } else {
            message = "Conversation renamed to \"" + conversation.title() + "\"";
        }
        return ResponseEntity.ok(APISuccessResponse.ok(message, conversation, currentPath()));
    }

    @DeleteMapping(value = "/conversations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a conversation",
            description = "Permanently deletes the conversation and all its messages (owner or staff).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation deleted"),
            @ApiResponse(responseCode = "403", description = "Not your conversation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<Void>> deleteConversation(
            @Parameter(description = "Conversation id") @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        aiAssistanceService.deleteConversation(id, currentUserId, authorities(authentication));

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Conversation and all its messages deleted successfully", currentPath()));
    }

    // #####################################################################
    //                               MESSAGES
    // #####################################################################

    @PostMapping(value = "/conversations/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send a message (complete reply)",
            description = """
                    Sends a message and waits for the full answer.
                    If the AI provider fails, the response is still 200: your message is saved and
                    assistantMessage has status FAILED with a friendly errorMessage, so the UI can offer a retry.
                    Messages containing passwords, codes or card numbers are not sent to the AI.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reply (or a FAILED reply with an explanation)"),
            @ApiResponse(responseCode = "400", description = "Empty or too long message",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not your conversation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Daily limit reached, reply in progress, archived chat or inactive assistant",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ChatReplyResponseDto>> sendMessage(
            @Parameter(description = "Conversation id") @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        ChatReplyResponseDto result = aiAssistanceService.sendMessage(
                id, request, currentUserId, authorities(authentication));

        return ResponseEntity.ok(APISuccessResponse.ok(replyMessage(result), result, currentPath()));
    }

    @PostMapping(value = "/conversations/{id}/messages/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send a message (streamed reply)",
            description = """
                    Streams the answer as Server-Sent Events. Each event's data is a ChatStreamEventDto:
                    - **start**: messageId of the reply being written
                    - **delta**: the next piece of text (append it)
                    - **done**: finishReason, token usage and remainingMessagesToday
                    - **error**: errorCode and a friendly errorMessage

                    Problems detected before streaming (limits, ownership, archived chat) return a normal
                    APIErrorResponse JSON instead of a stream. Closing the connection stops generation;
                    the partial reply is kept. Swagger UI cannot display streams: use curl -N or a browser client.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event stream",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(responseCode = "400", description = "Empty or too long message",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not your conversation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Daily limit reached, reply in progress, archived chat or inactive assistant",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public SseEmitter streamMessage(
            @Parameter(description = "Conversation id") @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        return aiAssistanceService.streamMessage(id, request, currentUserId, authorities(authentication));
    }

    @GetMapping(value = "/conversations/{id}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch conversation messages",
            description = "Newest first by default: show the page reversed and request the next page for older "
                    + "messages. Staff can review quality with flaggedOnly=true, status=FAILED or feedbackRating=-1.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of messages"),
            @ApiResponse(responseCode = "403", description = "Not your conversation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conversation not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIMessagePageResponseDto>> fetchMessages(
            @Parameter(description = "Conversation id") @PathVariable UUID id,
            @Valid @ParameterObject @ModelAttribute PagedQueryAIMessageRequestDto query,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        AIMessagePageResponseDto result = aiAssistanceService.fetchMessages(
                id, query, currentUserId, authorities(authentication));
        String message = result.totalElements() == 0
                ? "No messages in this conversation yet"
                : "Messages fetched successfully";

        return ResponseEntity.ok(APISuccessResponse.ok(message, result, currentPath()));
    }

    @PatchMapping(value = "/messages/{id}/feedback", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Rate an assistant reply",
            description = "rating 1 = helpful, -1 = not helpful, with an optional comment. Sending again replaces "
                    + "the previous feedback. Only completed assistant replies in your own conversations can be rated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback saved"),
            @ApiResponse(responseCode = "400", description = "Invalid rating or not an assistant reply",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not your conversation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Message not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Reply not complete yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<AIMessageResponseDto>> submitFeedback(
            @Parameter(description = "Message id") @PathVariable UUID id,
            @Valid @RequestBody MessageFeedbackRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        AIMessageResponseDto message = aiAssistanceService.submitFeedback(id, request, currentUserId);
        String text = request.rating() == 1
                ? "Thanks! Glad this reply was helpful."
                : "Thanks for your feedback. It helps us improve the assistant.";

        return ResponseEntity.ok(APISuccessResponse.ok(text, message, currentPath()));
    }

    // #####################################################################
    //                               HELPERS
    // #####################################################################

    /** Message for a chat turn, based on what happened to the reply. */
    private static String replyMessage(ChatReplyResponseDto result) {
        AIMessageResponseDto reply = result.assistantMessage();
        String remaining = " You have " + result.remainingMessagesToday() + " message(s) left today.";

        if (reply == null) {
            return "Message sent." + remaining;
        }
        if ("moderation".equals(reply.finishReason())) {
            return "For your security, your message was not sent to the assistant because it seems to contain "
                    + "a password, code or card number.";
        }
        if (reply.status() == AIMessageStatus.FAILED) {
            return "Your message was saved, but the assistant could not answer: "
                    + (reply.errorMessage() == null ? "please try again." : reply.errorMessage());
        }
        if (reply.truncated()) {
            return "Reply received (shortened because it reached the maximum length; ask the assistant to continue)."
                    + remaining;
        }
        return "Reply received." + remaining;
    }

    private static List<String> authorities(Authentication authentication) {
        if (authentication == null) {
            return List.of();
        }
        Collection<? extends GrantedAuthority> granted = authentication.getAuthorities();
        return granted.stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static String currentPath() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
    }
}