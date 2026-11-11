package com.learningLadder.learningLadder.mappers.aiAssistMapper;

import com.learningLadder.learningLadder.dto.aiAssistDto.AIAssistResponseDto;
import com.learningLadder.learningLadder.dto.aiAssistDto.AIAssistSummaryResponseDto;
import com.learningLadder.learningLadder.dto.aiAssistDto.CreateAIAssistRequestDto;
import com.learningLadder.learningLadder.dto.aiAssistDto.UpdateAIAssistRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.AIConversationResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.AIMessageResponseDto;
import com.learningLadder.learningLadder.dto.aiChatDto.MessageFeedbackRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.SendMessageRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.StartConversationRequestDto;
import com.learningLadder.learningLadder.dto.aiChatDto.UpdateConversationRequestDto;
import com.learningLadder.learningLadder.entity.aiAssist.AIAssist;
import com.learningLadder.learningLadder.entity.aiAssist.AIConversation;
import com.learningLadder.learningLadder.entity.aiAssist.AIMessage;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface AiAssistMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "logo", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AIAssist toEntity(CreateAIAssistRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "logo", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateAIAssistRequestDto dto, @MappingTarget AIAssist entity);

    @Mapping(target = "courseId", source = "course.id")
    AIAssistResponseDto toAssistResponse(AIAssist entity);

    List<AIAssistResponseDto> toAssistResponseList(List<AIAssist> entities);

    @Mapping(target = "courseId", source = "course.id")
    AIAssistSummaryResponseDto toAssistSummary(AIAssist entity);

    List<AIAssistSummaryResponseDto> toAssistSummaryList(List<AIAssist> entities);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title", source = "title")
    AIConversation toEntity(StartConversationRequestDto dto);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "pinned", source = "pinned")
    @Mapping(target = "archived", source = "archived")
    void updateEntity(UpdateConversationRequestDto dto, @MappingTarget AIConversation entity);

    @Mapping(target = "assistantId", source = "assistant.id")
    @Mapping(target = "assistantName", source = "assistant.name")
    @Mapping(target = "assistantLogo", source = "assistant.logo")
    @Mapping(target = "userId", source = "user.id")
    AIConversationResponseDto toConversationResponse(AIConversation entity);

    List<AIConversationResponseDto> toConversationResponseList(List<AIConversation> entities);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "content", source = "content")
    AIMessage toEntity(SendMessageRequestDto dto);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "feedbackRating", source = "rating")
    @Mapping(target = "feedbackComment", source = "comment",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void applyFeedback(MessageFeedbackRequestDto dto, @MappingTarget AIMessage entity);

    @Mapping(target = "conversationId", source = "conversation.id")
    AIMessageResponseDto toMessageResponse(AIMessage entity);

    List<AIMessageResponseDto> toMessageResponseList(List<AIMessage> entities);
}