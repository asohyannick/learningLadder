package com.learningLadder.learningLadder.mappers.timeTableMapper;

import com.learningLadder.learningLadder.dto.timeTableDto.CreateTimeTableRequestDto;
import com.learningLadder.learningLadder.dto.timeTableDto.TimeTableResponseDto;
import com.learningLadder.learningLadder.dto.timeTableDto.UpdateTimeTableRequestDto;
import com.learningLadder.learningLadder.entity.timeTable.TimeTable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface TimeTableMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "timezone", source = "timezone", defaultValue = "UTC")
    TimeTable toEntity(CreateTimeTableRequestDto request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateTimeTableRequestDto request, @MappingTarget TimeTable entity);

    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "courseName", source = "course.name")
    @Mapping(target = "courseSlug", source = "course.slug")
    @Mapping(target = "durationMinutes",
            expression = "java(java.time.Duration.between(timeTable.getStartTime(), timeTable.getEndTime()).toMinutes())")
    TimeTableResponseDto toResponse(TimeTable timeTable);
}