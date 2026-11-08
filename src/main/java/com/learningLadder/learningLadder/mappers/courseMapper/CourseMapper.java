package com.learningLadder.learningLadder.mappers.courseMapper;

import com.learningLadder.learningLadder.dto.courseDto.*;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.course.CourseMaterial;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface CourseMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "name", source = "name", qualifiedByName = "trim")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "thumbnail", ignore = true)
    @Mapping(target = "promoVideo", ignore = true)
    @Mapping(target = "syllabus", ignore = true)
    @Mapping(target = "materials", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Course toEntity(CreateCourseRequestDto dto);

    @Mapping(target = "name", source = "name", qualifiedByName = "trim")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "thumbnail", ignore = true)
    @Mapping(target = "promoVideo", ignore = true)
    @Mapping(target = "syllabus", ignore = true)
    @Mapping(target = "materials", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateCourseRequestDto dto, @MappingTarget Course course);

    @Mapping(target = "thumbnailUrl", source = "thumbnail.presignedUrl")
    @Mapping(target = "promoVideoUrl", source = "promoVideo.presignedUrl")
    @Mapping(target = "syllabusUrl", source = "syllabus.presignedUrl")
    CourseResponseDto toResponse(Course course);

    @Mapping(target = "thumbnailUrl", source = "thumbnail.presignedUrl")
    CourseSummaryResponseDto toSummary(Course course);

    @Mapping(target = "fileId", source = "file.id")
    @Mapping(target = "fileName", source = "file.originalFileName")
    @Mapping(target = "contentType", source = "file.contentType")
    @Mapping(target = "sizeBytes", source = "file.sizeBytes")
    @Mapping(target = "url", source = "file.presignedUrl")
    CourseMaterialResponseDto toMaterialResponse(CourseMaterial material);

    @Named("trim")
    default String trim(String value) {
        return value == null ? null : value.trim();
    }
}