package com.learningLadder.learningLadder.service.aiAssistantService;

import com.learningLadder.learningLadder.config.backgroundJob.FireAndForgetEmailAsync.FireAndForgetEmailAsync;
import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.dto.aiAssistDto.*;
import com.learningLadder.learningLadder.dto.aiChatDto.AIConversationPageResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.AIConversationResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.AIMessagePageResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.AIMessageResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.ChatReplyResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.ChatStreamEventDto;
import com.learningLadder.learningLadder.dto.aiChatDto.MessageFeedbackRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.PagedQueryAIConversationRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.PagedQueryAIMessageRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.SendMessageRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.StartConversationRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.UpdateConversationRequestDto;
import com.learningLadder.learningLadder.entity.aiAssist.AIAssist;
import com.learningLadder.learningLadder.entity.aiAssist.AIConversation;
import com.learningLadder.learningLadder.entity.aiAssist.AIMessage;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.enums.aiMessageRole.AIMessageRole;
import com.learningLadder.learningLadder.enums.aiMessageStatus.AIMessageStatus;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.fileStorageException.FileStorageException;
import com.learningLadder.learningLadder.exception.groqException.GroqException;
import com.learningLadder.learningLadder.exception.notFoundRequestException.NotFoundRequestException;
import com.learningLadder.learningLadder.mappers.aiAssistMapper.AiAssistMapper;
import com.learningLadder.learningLadder.repository.aiAssistRepository.AIAssistRepository;
import com.learningLadder.learningLadder.repository.aiAssistRepository.AIAssistSpecification;
import com.learningLadder.learningLadder.repository.aiAssistRepository.AIConversationRepository;
import com.learningLadder.learningLadder.repository.aiAssistRepository.AIConversationSpecification;
import com.learningLadder.learningLadder.repository.aiAssistRepository.AIMessageRepository;
import com.learningLadder.learningLadder.repository.aiAssistRepository.AIMessageSpecification;
import com.learningLadder.learningLadder.repository.courseRepository.CourseRepository;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import com.learningLadder.learningLadder.service.aiAssistantService.LearningLadderPromptBuilder.ChatAudience;
import com.learningLadder.learningLadder.service.aiAssistantService.groq.GroqChatClient;
import com.learningLadder.learningLadder.service.aiAssistantService.groq.GroqChatRequest;
import com.learningLadder.learningLadder.service.aiAssistantService.groq.GroqCompletion;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI assistants (admin configuration) and AI chat (conversations and messages).
 * <p>
 * Chat requests never hold a database transaction while waiting for the model:
 * 1) a short transaction saves the user's message and a placeholder reply,
 * 2) Groq is called with no transaction open,
 * 3) a second short transaction stores the reply, token usage and timing.
 * <p>
 * Only assistant reads are cached. Chat data changes on every message, so it is never cached.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class AiAssistanceService {

    /** Clears every assistant cache after a successful write. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CacheEvict(cacheNames = {
            CacheNames.AI_ASSIST, CacheNames.AI_ASSISTS, CacheNames.AI_ASSIST_SEARCH,
            CacheNames.AI_ASSIST_COUNT, CacheNames.AI_ASSIST_PUBLIC
    }, allEntries = true)
    public @interface EvictAssistantCaches {
    }

    // ---------------------------------------------------------------- chat rules
    private static final List<AIMessageRole> CHAT_ROLES = List.of(AIMessageRole.USER, AIMessageRole.ASSISTANT);
    private static final List<AIMessageStatus> IN_PROGRESS = List.of(AIMessageStatus.PENDING, AIMessageStatus.STREAMING);
    /** A reply untouched for this long is considered abandoned and no longer blocks the conversation. */
    private static final Duration STUCK_REPLY_AFTER = Duration.ofMinutes(2);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(3);
    private static final int STREAM_THREADS = 32;
    private static final int SUMMARY_EVERY_MESSAGES = 10;
    private static final int SUMMARY_MAX_SOURCE_MESSAGES = 60;

    // ---------------------------------------------------------------- limits
    private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024; // 2 MB
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/svg+xml", "svg");
    private static final int MAX_PAGE_SIZE = 100;

    private final AiAssistMapper assistMapper;
    private final AIAssistRepository aiAssistRepository;
    private final AIConversationRepository aiConversationRepository;
    private final AIMessageRepository aiMessageRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final GroqChatClient groqChatClient;
    private final LearningLadderPromptBuilder promptBuilder;
    private final MinioFileStorageService minioFileStorageService;
    private final FireAndForgetEmailAsync fireAndForgetEmailAsync;
    private final PlatformTransactionManager transactionManager;

    /** Threads that stream replies to browsers (initialized here, so not part of the constructor). */
    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(STREAM_THREADS, namedThreads());

    /** Everything needed to call the model for one chat turn, prepared inside a short transaction. */
    private record PreparedTurn(UUID conversationId, String conversationTitle, UUID assistantMessageId,
                                AIMessageResponseDto userMessage, GroqChatRequest request,
                                int remainingAfter, AIMessageResponseDto blockedReply) {
        boolean blocked() {
            return blockedReply != null;
        }
    }

    private record LogoUpload(String objectKey, byte[] content, String contentType) {
    }

    /** Thrown inside the streaming callback when the browser has gone away, to stop generation. */
    private static final class ClientDisconnectedException extends RuntimeException {
        ClientDisconnectedException() {
            super("Client disconnected", null, false, false);
        }
    }

    // #####################################################################
    //                     ASSISTANTS (admin configuration)
    // #####################################################################

    @EvictAssistantCaches
    public AIAssistResponseDto createAssistant(CreateAIAssistRequestDto dto, MultipartFile logo, UUID adminId) {
        if (aiAssistRepository.existsByNameIgnoreCase(dto.name())) {
            throw new ConflictRequestException("An assistant named \"" + dto.name() + "\" already exists");
        }
        if (dto.slug() == null) {
            throw new BadRequestException("Could not build a slug from the name; please provide one");
        }
        if (aiAssistRepository.existsBySlug(dto.slug())) {
            throw new ConflictRequestException("The slug \"" + dto.slug() + "\" is already used by another assistant");
        }

        LogoUpload upload = prepareLogo(logo);

        AIAssist assistant = assistMapper.toEntity(dto);
        if (dto.courseId() != null) {
            assistant.setCourse(findCourse(dto.courseId()));
        }
        assistant.setCreatedBy(adminId);
        assistant.setLogo(upload == null ? null : upload.objectKey());

        AIAssist saved;
        try {
            saved = aiAssistRepository.saveAndFlush(assistant);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictRequestException("An assistant with this name or slug already exists");
        }

        if (upload != null) {
            uploadLogoInBackground(upload, null);
        }
        log.info("AI assistant {} \"{}\" created with model {} by {}", saved.getId(), saved.getName(), saved.getModel(), adminId);
        return assistMapper.toAssistResponse(saved);
    }

    @EvictAssistantCaches
    public AIAssistResponseDto updateAssistant(UUID id, UpdateAIAssistRequestDto dto, MultipartFile logo) {
        AIAssist assistant = findAssistant(id);

        if (dto.name() != null && aiAssistRepository.existsByNameIgnoreCaseAndIdNot(dto.name(), id)) {
            throw new ConflictRequestException("Another assistant is already named \"" + dto.name() + "\"");
        }
        if (dto.slug() != null && aiAssistRepository.existsBySlugAndIdNot(dto.slug(), id)) {
            throw new ConflictRequestException("The slug \"" + dto.slug() + "\" is already used by another assistant");
        }

        LogoUpload upload = prepareLogo(logo);

        assistMapper.updateEntity(dto, assistant);
        if (dto.courseId() != null) {
            assistant.setCourse(findCourse(dto.courseId()));
        }

        String previousLogo = null;
        if (upload != null) {
            previousLogo = assistant.getLogo();
            assistant.setLogo(upload.objectKey());
        }

        AIAssist saved;
        try {
            saved = aiAssistRepository.saveAndFlush(assistant);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictRequestException("An assistant with this name or slug already exists");
        } catch (ConflictRequestException ex) {
            throw new ConflictRequestException("This assistant was changed by someone else. Please reload and try again.");
        }

        if (upload != null) {
            uploadLogoInBackground(upload, previousLogo);
        }
        log.info("AI assistant {} updated", id);
        return assistMapper.toAssistResponse(saved);
    }

    @EvictAssistantCaches
    public void deleteAssistant(UUID id) {
        AIAssist assistant = findAssistant(id);
        if (aiConversationRepository.existsByAssistant_Id(id)) {
            throw new ConflictRequestException("This assistant has conversations and cannot be deleted. "
                    + "Deactivate it instead by updating it with active = false.");
        }
        String logo = assistant.getLogo();
        aiAssistRepository.delete(assistant);

        if (logo != null) {
            fireAndForgetEmailAsync.fire("delete assistant logo " + logo, () -> deleteFromMinio(logo));
        }
        log.info("AI assistant {} \"{}\" deleted", id, assistant.getName());
    }

    // ---------------------------------------------------------------- admin reads (full view)

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.AI_ASSIST, key = "#id")
    public AIAssistResponseDto fetchAssistant(UUID id) {
        return assistMapper.toAssistResponse(findAssistant(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.AI_ASSISTS, key = "#page + ':' + #size")
    public AIAssistPageResponseDto fetchAllAssistants(int page, int size) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.ASC, "name"));
        Page<AIAssist> result = aiAssistRepository.findAll(pageable);
        return new AIAssistPageResponseDto(assistMapper.toAssistResponseList(result.getContent()),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.AI_ASSIST_SEARCH, key = "#query")
    public AIAssistPageResponseDto searchAssistants(PagedQueryAIAssistRequestDto query) {
        Page<AIAssist> result = aiAssistRepository.findAll(AIAssistSpecification.fromQuery(query), query.toPageable());
        return new AIAssistPageResponseDto(assistMapper.toAssistResponseList(result.getContent()),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.AI_ASSIST_COUNT, key = "'total'")
    public long countAssistants() {
        return aiAssistRepository.count();
    }

    // ---------------------------------------------------------------- public reads (summary view)

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.AI_ASSIST_PUBLIC, key = "'list:' + #page + ':' + #size")
    public AIAssistSummaryPageResponseDto fetchAvailableAssistants(int page, int size) {
        PagedQueryAIAssistRequestDto query = new PagedQueryAIAssistRequestDto(
                null, null, null, null, true, safePage(page), safeSize(size), "name", "ASC");
        return toSummaryPage(aiAssistRepository.findAll(AIAssistSpecification.fromQuery(query), query.toPageable()));
    }

    /** Public search: inactive assistants are never returned, whatever the client sends. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.AI_ASSIST_PUBLIC, key = "'search:' + #query")
    public AIAssistSummaryPageResponseDto searchAvailableAssistants(PagedQueryAIAssistRequestDto query) {
        PagedQueryAIAssistRequestDto activeOnly = new PagedQueryAIAssistRequestDto(
                query.keyword(), query.purpose(), query.provider(), query.courseId(), true,
                query.page(), query.size(), query.sortBy(), query.sortDirection());
        return toSummaryPage(aiAssistRepository.findAll(AIAssistSpecification.fromQuery(activeOnly), activeOnly.toPageable()));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.AI_ASSIST_PUBLIC, key = "'slug:' + #slug")
    public AIAssistSummaryResponseDto fetchAssistantBySlug(String slug) {
        return aiAssistRepository.findBySlugAndActiveTrue(slug)
                .map(assistMapper::toAssistSummary)
                .orElseThrow(() -> new NotFoundRequestException("No active assistant found for \"" + slug + "\""));
    }

    // ---------------------------------------------------------------- admin helpers

    /**
     * Shows admins the exact system prompt the model receives for a given audience
     * (persona + platform knowledge + audience guidance + conduct rules).
     */
    @Transactional(readOnly = true)
    public String previewSystemPrompt(UUID assistantId, String audience) {
        ChatAudience chatAudience;
        try {
            chatAudience = audience == null ? ChatAudience.STUDENT : ChatAudience.valueOf(audience.trim().toUpperCase());
        } catch (BadRequestException ex) {
            throw new BadRequestException("audience must be one of: STUDENT, TUTOR, PARENT, STAFF, GUEST");
        }
        return promptBuilder.buildSystemPrompt(findAssistant(assistantId), chatAudience, null);
    }

    /** Starter persona text for a purpose (TUTORING, LIBRARY_HELP, CAREER_ADVICE, ADMISSIONS, TEACHING_ASSISTANT). */
    @Transactional(propagation = Propagation.SUPPORTS)
    public String getSystemPromptTemplate(String purpose) {
        return promptBuilder.systemPromptTemplate(purpose);
    }

    // #####################################################################
    //                             CONVERSATIONS
    // #####################################################################

    /**
     * Creates a conversation. If the request has a firstMessage, it is answered right away
     * and the reply is included; otherwise userMessage / assistantMessage are null.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatReplyResponseDto startConversation(StartConversationRequestDto dto, UUID userId,
                                                  Collection<String> authorities) {
        AIConversationResponseDto created = Objects.requireNonNull(tx().execute(status -> {
            AIAssist assistant = findActiveAssistant(dto.assistantId());

            AIConversation conversation = assistMapper.toEntity(dto);
            conversation.setAssistant(assistant);
            conversation.setUser(userRepository.getReferenceById(userId));
            if (conversation.getTitle() == null) {
                String fromMessage = promptBuilder.buildConversationTitle(dto.firstMessage());
                conversation.setTitle(fromMessage != null ? fromMessage : "New chat with " + assistant.getName());
            }
            return assistMapper.toConversationResponse(aiConversationRepository.saveAndFlush(conversation));
        }));

        log.info("Conversation {} started by user {} with assistant {}", created.id(), userId, created.assistantId());

        if (dto.firstMessage() == null) {
            return new ChatReplyResponseDto(created.id(), created.title(), null, null,
                    remainingMessagesToday(created.assistantId(), userId));
        }
        return sendMessage(created.id(), new SendMessageRequestDto(dto.firstMessage()), userId, authorities);
    }

    /** Chat list. Regular users only ever see their own conversations; staff may filter by any user. */
    @Transactional(readOnly = true)
    public AIConversationPageResponseDto fetchConversations(PagedQueryAIConversationRequestDto query, UUID userId,
                                                            Collection<String> authorities) {
        PagedQueryAIConversationRequestDto effective = isStaff(authorities) ? query : query.withUserId(userId);
        Page<AIConversation> result = aiConversationRepository.findAll(
                AIConversationSpecification.fromQuery(effective), effective.toPageable());
        return new AIConversationPageResponseDto(assistMapper.toConversationResponseList(result.getContent()),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    public AIConversationResponseDto fetchConversation(UUID conversationId, UUID userId, Collection<String> authorities) {
        AIConversation conversation = findConversationWithAssistant(conversationId);
        if (!isStaff(authorities)) {
            ensureConversationOwner(conversation, userId);
        }
        return assistMapper.toConversationResponse(conversation);
    }

    public AIConversationResponseDto updateConversation(UUID conversationId, UpdateConversationRequestDto dto, UUID userId) {
        AIConversation conversation = findConversationWithAssistant(conversationId);
        ensureConversationOwner(conversation, userId);

        assistMapper.updateEntity(dto, conversation);
        try {
            return assistMapper.toConversationResponse(aiConversationRepository.saveAndFlush(conversation));
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictRequestException("This conversation is being updated by a new message. Please try again.");
        }
    }

    public void deleteConversation(UUID conversationId, UUID userId, Collection<String> authorities) {
        AIConversation conversation = findConversationWithAssistant(conversationId);
        if (!isStaff(authorities)) {
            ensureConversationOwner(conversation, userId);
        }
        int removed = aiMessageRepository.deleteByConversationId(conversationId);
        aiConversationRepository.delete(conversation);
        log.info("Conversation {} deleted with {} message(s)", conversationId, removed);
    }

    // #####################################################################
    //                               MESSAGES
    // #####################################################################

    /** Sends a message and waits for the full reply. A provider failure returns a FAILED reply, not an HTTP error. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatReplyResponseDto sendMessage(UUID conversationId, SendMessageRequestDto dto, UUID userId,
                                            Collection<String> authorities) {
        ChatAudience audience = ChatAudience.fromAuthorities(authorities);
        PreparedTurn turn = prepareTurn(conversationId, dto.content(), userId, audience, false);

        if (turn.blocked()) {
            return new ChatReplyResponseDto(conversationId, turn.conversationTitle(),
                    turn.userMessage(), turn.blockedReply(), turn.remainingAfter());
        }

        AIMessageResponseDto reply;
        try {
            GroqCompletion completion = groqChatClient.complete(turn.request());
            reply = completeTurn(turn.assistantMessageId(), completion);
            scheduleSummaryRefresh(conversationId);
        } catch (GroqException ex) {
            reply = failTurn(turn.assistantMessageId(), ex.getErrorCode(), ex.getUserMessage(), null);
        } catch (RuntimeException ex) {
            log.error("Unexpected error while answering in conversation {}", conversationId, ex);
            reply = failTurn(turn.assistantMessageId(), "INTERNAL_ERROR",
                    "Something went wrong while answering. Please try again.", null);
        }

        return new ChatReplyResponseDto(conversationId, turn.conversationTitle(),
                turn.userMessage(), reply, turn.remainingAfter());
    }

    /**
     * Sends a message and streams the reply as Server-Sent Events (start, delta..., done | error).
     * Validation problems (limits, ownership, archived chat) are thrown BEFORE the stream opens,
     * so the client receives a normal APIErrorResponse.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SseEmitter streamMessage(UUID conversationId, SendMessageRequestDto dto, UUID userId,
                                    Collection<String> authorities) {
        ChatAudience audience = ChatAudience.fromAuthorities(authorities);
        PreparedTurn turn = prepareTurn(conversationId, dto.content(), userId, audience, true);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        AtomicBoolean connected = new AtomicBoolean(true);
        emitter.onCompletion(() -> connected.set(false));
        emitter.onTimeout(() -> connected.set(false));
        emitter.onError(ex -> connected.set(false));

        UUID messageId = turn.assistantMessageId();

        Runnable task = turn.blocked()
                ? () -> {
            send(emitter, ChatStreamEventDto.start(conversationId, messageId), connected);
            send(emitter, ChatStreamEventDto.delta(conversationId, messageId, turn.blockedReply().content()), connected);
            send(emitter, ChatStreamEventDto.done(conversationId, messageId, "moderation",
                    null, null, turn.remainingAfter()), connected);
            completeQuietly(emitter, connected);
        }
                : () -> streamReply(emitter, connected, turn);

        try {
            streamExecutor.execute(task);
        } catch (RejectedExecutionException ex) {
            failTurn(messageId, "SERVER_BUSY", "The assistant is very busy right now. Please try again in a moment.", null);
            throw new ConflictRequestException("The assistant is very busy right now. Please try again in a moment.");
        }
        return emitter;
    }

    /** Message history of a conversation (SYSTEM messages are never returned). */
    @Transactional(readOnly = true)
    public AIMessagePageResponseDto fetchMessages(UUID conversationId, PagedQueryAIMessageRequestDto query,
                                                  UUID userId, Collection<String> authorities) {
        AIConversation conversation = findConversationWithAssistant(conversationId);
        if (!isStaff(authorities)) {
            ensureConversationOwner(conversation, userId);
        }
        Page<AIMessage> result = aiMessageRepository.findAll(
                AIMessageSpecification.forConversation(conversationId, query), query.toPageable());
        return new AIMessagePageResponseDto(assistMapper.toMessageResponseList(result.getContent()),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    /** Thumbs up / down on an assistant reply. Sending again replaces the previous feedback. */
    public AIMessageResponseDto submitFeedback(UUID messageId, MessageFeedbackRequestDto dto, UUID userId) {
        AIMessage message = aiMessageRepository.findWithConversationById(messageId)
                .orElseThrow(() -> new NotFoundRequestException("Message not found with id " + messageId));
        ensureConversationOwner(message.getConversation(), userId);

        if (message.getRole() != AIMessageRole.ASSISTANT) {
            throw new BadRequestException("Feedback can only be given on the assistant's replies");
        }
        if (message.getStatus() != AIMessageStatus.COMPLETED) {
            throw new ConflictRequestException("Feedback can only be given once the reply is complete");
        }

        assistMapper.applyFeedback(dto, message);
        log.info("Feedback {} on message {} by user {}", dto.rating(), messageId, userId);
        return assistMapper.toMessageResponse(aiMessageRepository.saveAndFlush(message));
    }

    /** How many messages the user can still send to this assistant today (resets at midnight UTC). */
    @Transactional(readOnly = true)
    public int remainingMessagesToday(UUID assistantId, UUID userId) {
        AIAssist assistant = findAssistant(assistantId);
        long used = countMessagesToday(userId, assistantId);
        return (int) Math.max(assistant.getDailyMessageLimitPerUser() - used, 0);
    }

    // #####################################################################
    //                       CHAT TURN (internal steps)
    // #####################################################################

    /** Transaction 1: checks, saves the user message and a placeholder reply, builds the model request. */
    private PreparedTurn prepareTurn(UUID conversationId, String content, UUID userId,
                                     ChatAudience audience, boolean streaming) {
        return Objects.requireNonNull(tx().execute(status -> {
            AIConversation conversation = findConversationWithAssistant(conversationId);
            ensureConversationOwner(conversation, userId);

            if (conversation.isArchived()) {
                throw new ConflictRequestException("This conversation is archived. Unarchive it to continue chatting.");
            }
            AIAssist assistant = conversation.getAssistant();
            if (!assistant.isActive()) {
                throw new ConflictRequestException("This assistant is no longer available. Please start a chat with another assistant.");
            }
            if (content.length() > assistant.getMaxInputCharacters()) {
                throw new BadRequestException("Your message is too long. " + assistant.getName()
                        + " accepts up to " + assistant.getMaxInputCharacters() + " characters.");
            }
            if (aiMessageRepository.existsByConversation_IdAndStatusInAndUpdatedAtAfter(
                    conversationId, IN_PROGRESS, Instant.now().minus(STUCK_REPLY_AFTER))) {
                throw new ConflictRequestException("Please wait for the current reply to finish before sending another message.");
            }
            long usedToday = countMessagesToday(userId, assistant.getId());
            if (usedToday >= assistant.getDailyMessageLimitPerUser()) {
                throw new ConflictRequestException("You have reached today's limit of "
                        + assistant.getDailyMessageLimitPerUser() + " messages with " + assistant.getName()
                        + ". The limit resets at midnight (UTC).");
            }

            List<AIMessage> history = loadRecentHistory(conversationId, assistant.getContextWindowMessages());
            int nextSequence = aiMessageRepository.findMaxSequenceNumber(conversationId) + 1;
            boolean blocked = assistant.isModerationEnabled() && promptBuilder.containsSensitiveData(content);
            Instant now = Instant.now();

            AIMessage userMessage = assistMapper.toEntity(new SendMessageRequestDto(content));
            userMessage.setConversation(conversation);
            userMessage.setRole(AIMessageRole.USER);
            userMessage.setSequenceNumber(nextSequence);
            userMessage.setStatus(AIMessageStatus.COMPLETED);
            userMessage.setFlagged(blocked);

            AIMessage reply = new AIMessage();
            reply.setConversation(conversation);
            reply.setRole(AIMessageRole.ASSISTANT);
            reply.setSequenceNumber(nextSequence + 1);
            reply.setModel(assistant.getModel());
            if (blocked) {
                reply.setContent(promptBuilder.sensitiveDataReply());
                reply.setStatus(AIMessageStatus.COMPLETED);
                reply.setFinishReason("moderation");
                reply.setErrorCode("MODERATION_BLOCKED");
            } else {
                reply.setContent("");
                reply.setStatus(streaming ? AIMessageStatus.STREAMING : AIMessageStatus.PENDING);
            }

            conversation.setMessageCount(conversation.getMessageCount() + 2);
            conversation.setLastMessageAt(now);

            try {
                aiMessageRepository.save(userMessage);
                aiMessageRepository.save(reply);
                aiConversationRepository.saveAndFlush(conversation);
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
                throw new ConflictRequestException("Another message is being processed in this conversation. Please try again.");
            }

            int remainingAfter = (int) Math.max(assistant.getDailyMessageLimitPerUser() - usedToday - 1, 0);

            if (blocked) {
                log.warn("Message in conversation {} blocked: sensitive data detected", conversationId);
                return new PreparedTurn(conversationId, conversation.getTitle(), reply.getId(),
                        assistMapper.toMessageResponse(userMessage), null, remainingAfter,
                        assistMapper.toMessageResponse(reply));
            }

            GroqChatRequest request = new GroqChatRequest(
                    assistant.getModel(),
                    promptBuilder.buildChatMessages(assistant, audience, conversation.getSummary(), history, content),
                    assistant.getTemperature(),
                    assistant.getTopP(),
                    assistant.getMaxOutputTokens(),
                    streaming,
                    userId.toString());

            return new PreparedTurn(conversationId, conversation.getTitle(), reply.getId(),
                    assistMapper.toMessageResponse(userMessage), request, remainingAfter, null);
        }));
    }

    /** Runs on a stream thread: forwards each piece of text to the browser, then saves the result. */
    private void streamReply(SseEmitter emitter, AtomicBoolean connected, PreparedTurn turn) {
        UUID conversationId = turn.conversationId();
        UUID messageId = turn.assistantMessageId();
        StringBuilder received = new StringBuilder();

        send(emitter, ChatStreamEventDto.start(conversationId, messageId), connected);
        try {
            GroqCompletion completion = groqChatClient.stream(turn.request(), delta -> {
                received.append(delta);
                if (!send(emitter, ChatStreamEventDto.delta(conversationId, messageId, delta), connected)) {
                    throw new ClientDisconnectedException(); // stop generating (and paying for) tokens
                }
            });
            completeTurn(messageId, completion);
            send(emitter, ChatStreamEventDto.done(conversationId, messageId, completion.finishReason(),
                    completion.promptTokens(), completion.completionTokens(), turn.remainingAfter()), connected);
            scheduleSummaryRefresh(conversationId);

        } catch (ClientDisconnectedException ex) {
            log.info("Client left during streaming in conversation {}; keeping the partial reply", conversationId);
            saveInterruptedReply(messageId, received.toString());
        } catch (GroqException ex) {
            failTurn(messageId, ex.getErrorCode(), ex.getUserMessage(), received.toString());
            send(emitter, ChatStreamEventDto.error(conversationId, messageId, ex.getErrorCode(), ex.getUserMessage()), connected);
        } catch (RuntimeException ex) {
            log.error("Unexpected error while streaming in conversation {}", conversationId, ex);
            String friendly = "Something went wrong while answering. Please try again.";
            failTurn(messageId, "INTERNAL_ERROR", friendly, received.toString());
            send(emitter, ChatStreamEventDto.error(conversationId, messageId, "INTERNAL_ERROR", friendly), connected);
        } finally {
            completeQuietly(emitter, connected);
        }
    }

    /** Transaction 2 (success): stores the reply text, usage and timing, and updates conversation totals. */
    private AIMessageResponseDto completeTurn(UUID assistantMessageId, GroqCompletion completion) {
        return Objects.requireNonNull(tx().execute(status -> {
            AIMessage reply = findMessageWithConversation(assistantMessageId);
            reply.setContent(completion.content());
            reply.setStatus(AIMessageStatus.COMPLETED);
            if (completion.model() != null) {
                reply.setModel(completion.model());
            }
            reply.setProviderResponseId(completion.id());
            reply.setFinishReason(completion.finishReason());
            reply.setPromptTokens(completion.promptTokens());
            reply.setCompletionTokens(completion.completionTokens());
            reply.setTotalTokens(completion.totalTokens());
            reply.setLatencyMs((int) Math.min(completion.latencyMs(), Integer.MAX_VALUE));

            AIConversation conversation = reply.getConversation();
            addTokens(conversation, completion.promptTokens(), completion.completionTokens());
            conversation.setLastMessageAt(Instant.now());

            return assistMapper.toMessageResponse(aiMessageRepository.saveAndFlush(reply));
        }));
    }

    /** Transaction 2 (failure): keeps any partial text and records a user-friendly error. */
    private AIMessageResponseDto failTurn(UUID assistantMessageId, String errorCode, String userMessage, String partial) {
        return Objects.requireNonNull(tx().execute(status -> {
            AIMessage reply = findMessageWithConversation(assistantMessageId);
            reply.setStatus(AIMessageStatus.FAILED);
            reply.setContent(partial == null ? "" : partial);
            reply.setErrorCode(errorCode);
            reply.setErrorMessage(userMessage.length() > 500 ? userMessage.substring(0, 500) : userMessage);
            log.warn("Reply {} failed: {}", assistantMessageId, errorCode);
            return assistMapper.toMessageResponse(aiMessageRepository.saveAndFlush(reply));
        }));
    }

    /** The user closed the page mid-answer: keep what was generated so it shows up in history. */
    private void saveInterruptedReply(UUID assistantMessageId, String partial) {
        tx().executeWithoutResult(status -> {
            AIMessage reply = findMessageWithConversation(assistantMessageId);
            reply.setContent(partial);
            reply.setStatus(partial.isBlank() ? AIMessageStatus.FAILED : AIMessageStatus.COMPLETED);
            reply.setFinishReason("client_disconnected");
            if (partial.isBlank()) {
                reply.setErrorCode("CLIENT_DISCONNECTED");
                reply.setErrorMessage("The reply was interrupted. Please ask again.");
            }
            aiMessageRepository.saveAndFlush(reply);
        });
    }

    // #####################################################################
    //                      CONVERSATION MEMORY (summary)
    // #####################################################################

    private void scheduleSummaryRefresh(UUID conversationId) {
        try {
            streamExecutor.execute(() -> {
                try {
                    refreshSummaryIfNeeded(conversationId);
                } catch (Exception ex) {
                    log.warn("Could not refresh summary of conversation {}: {}", conversationId, ex.getMessage());
                }
            });
        } catch (RejectedExecutionException ex) {
            log.debug("Summary refresh skipped for {}: executor busy", conversationId);
        }
    }

    /**
     * When a chat grows beyond the assistant's context window, older messages are no longer sent.
     * Every few messages their content is folded into AIConversation.summary, which the prompt
     * builder adds to the system prompt so the assistant keeps the thread.
     */
    private void refreshSummaryIfNeeded(UUID conversationId) {
        GroqChatRequest summaryRequest = tx().execute(status -> {
            AIConversation conversation = findConversationWithAssistant(conversationId);
            AIAssist assistant = conversation.getAssistant();
            int window = assistant.getContextWindowMessages();
            if (window == 0 || conversation.getMessageCount() <= window
                    || conversation.getMessageCount() % SUMMARY_EVERY_MESSAGES != 0) {
                return null;
            }
            int cutoff = aiMessageRepository.findMaxSequenceNumber(conversationId) - window;
            if (cutoff < 1) {
                return null;
            }
            List<AIMessage> older = aiMessageRepository
                    .findByConversation_IdAndSequenceNumberBetweenAndRoleInAndStatusOrderBySequenceNumberAsc(
                            conversationId, Math.max(1, cutoff - SUMMARY_MAX_SOURCE_MESSAGES + 1), cutoff,
                            CHAT_ROLES, AIMessageStatus.COMPLETED);
            if (older.isEmpty()) {
                return null;
            }
            return new GroqChatRequest(assistant.getModel(),
                    promptBuilder.buildSummaryMessages(conversation.getSummary(), older),
                    new BigDecimal("0.20"), null, 400, false, null);
        });
        if (summaryRequest == null) {
            return;
        }

        GroqCompletion summary = groqChatClient.complete(summaryRequest);
        if (summary.content() == null || summary.content().isBlank()) {
            return;
        }

        tx().executeWithoutResult(status -> aiConversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setSummary(summary.content().trim());
            addTokens(conversation, summary.promptTokens(), summary.completionTokens());
            aiConversationRepository.saveAndFlush(conversation);
        }));
        log.debug("Summary of conversation {} refreshed", conversationId);
    }

    // #####################################################################
    //                               HELPERS
    // #####################################################################

    private List<AIMessage> loadRecentHistory(UUID conversationId, int windowSize) {
        if (windowSize <= 0) {
            return List.of();
        }
        List<AIMessage> newestFirst = aiMessageRepository.findByConversation_IdAndRoleInAndStatusAndFlaggedFalse(
                conversationId, CHAT_ROLES, AIMessageStatus.COMPLETED,
                PageRequest.of(0, windowSize, Sort.by(Sort.Direction.DESC, "sequenceNumber")));
        List<AIMessage> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    private long countMessagesToday(UUID userId, UUID assistantId) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return aiMessageRepository.countByConversation_User_IdAndConversation_Assistant_IdAndRoleAndCreatedAtGreaterThanEqual(
                userId, assistantId, AIMessageRole.USER, startOfDay);
    }

    private void addTokens(AIConversation conversation, Integer promptTokens, Integer completionTokens) {
        conversation.setTotalPromptTokens(conversation.getTotalPromptTokens() + (promptTokens == null ? 0 : promptTokens));
        conversation.setTotalCompletionTokens(conversation.getTotalCompletionTokens() + (completionTokens == null ? 0 : completionTokens));
    }

    private boolean send(SseEmitter emitter, ChatStreamEventDto event, AtomicBoolean connected) {
        if (!connected.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(event, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException ex) {
            connected.set(false);
            return false;
        }
    }

    private void completeQuietly(SseEmitter emitter, AtomicBoolean connected) {
        if (connected.getAndSet(false)) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isStaff(Collection<String> authorities) {
        return ChatAudience.fromAuthorities(authorities).isStaff();
    }

    private void ensureConversationOwner(AIConversation conversation, UUID userId) {
        if (!conversation.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own conversations");
        }
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------- lookups

    private AIAssist findAssistant(UUID id) {
        return aiAssistRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Assistant not found with id " + id));
    }

    private AIAssist findActiveAssistant(UUID id) {
        AIAssist assistant = findAssistant(id);
        if (!assistant.isActive()) {
            throw new ConflictRequestException("This assistant is not available at the moment");
        }
        return assistant;
    }

    private AIConversation findConversationWithAssistant(UUID id) {
        return aiConversationRepository.findWithAssistantById(id)
                .orElseThrow(() -> new NotFoundRequestException("Conversation not found with id " + id));
    }

    private AIMessage findMessageWithConversation(UUID id) {
        return aiMessageRepository.findWithConversationById(id)
                .orElseThrow(() -> new NotFoundRequestException("Message not found with id " + id));
    }

    private Course findCourse(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Course not found with id " + id));
    }

    // ---------------------------------------------------------------- logo (MinIO, background)

    private LogoUpload prepareLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new BadRequestException("Logo must not exceed 2 MB");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : ALLOWED_IMAGE_TYPES.get(contentType.toLowerCase());
        if (extension == null) {
            throw new BadRequestException("Logo must be a JPEG, PNG, WEBP or SVG image");
        }
        try {
            // Bytes are read now: the MultipartFile's temp file is deleted when the request ends
            return new LogoUpload("ai-assist/logos/" + UUID.randomUUID() + "." + extension, file.getBytes(), contentType);
        } catch (IOException ex) {
            throw new BadRequestException("Could not read the logo file");
        }
    }

    private void uploadLogoInBackground(LogoUpload upload, String previousObjectKey) {
        fireAndForgetEmailAsync.fire("upload assistant logo " + upload.objectKey(), () -> {
            try {
                minioFileStorageService.uploadFile(upload.objectKey(), new ByteArrayInputStream(upload.content()),
                        upload.content().length, upload.contentType());
            } catch (Exception ex) {
                throw new FileStorageException("MinIO upload failed for " + upload.objectKey(), ex);
            }
            if (previousObjectKey != null) {
                try {
                    deleteFromMinio(previousObjectKey);
                } catch (Exception ex) {
                    log.warn("Could not delete old assistant logo {}: {}", previousObjectKey, ex.getMessage());
                }
            }
        });
    }

    private void deleteFromMinio(String objectKey) {
        try {
            minioFileStorageService.deleteFile(objectKey);
        } catch (Exception ex) {
            throw new FileStorageException("MinIO delete failed for " + objectKey, ex);
        }
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safeSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }

    private AIAssistSummaryPageResponseDto toSummaryPage(Page<AIAssist> page) {
        return new AIAssistSummaryPageResponseDto(assistMapper.toAssistSummaryList(page.getContent()),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private static java.util.concurrent.ThreadFactory namedThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "ai-stream-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdownStreams() {
        streamExecutor.shutdown();
        try {
            if (!streamExecutor.awaitTermination(20, TimeUnit.SECONDS)) {
                streamExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            streamExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}