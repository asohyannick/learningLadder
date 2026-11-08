package com.learningLadder.learningLadder.mappers.studentProfileMapper;

import com.learningLadder.learningLadder.dto.studentProfileDto.*;
import com.learningLadder.learningLadder.entity.studentProfile.StudentProfile;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface StudentProfileMapper {

    @Mapping(target = "firstName", source = "firstName", qualifiedByName = "trim")
    @Mapping(target = "lastName", source = "lastName", qualifiedByName = "trim")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "studentNumber", ignore = true)
    @Mapping(target = "enrollmentStatus", ignore = true)
    @Mapping(target = "admissionDate", ignore = true)
    @Mapping(target = "expectedGraduationDate", ignore = true)
    @Mapping(target = "gpa", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    StudentProfile toEntity(CreateStudentProfileRequestDto dto);

    @Mapping(target = "firstName", source = "firstName", qualifiedByName = "trim")
    @Mapping(target = "lastName", source = "lastName", qualifiedByName = "trim")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "studentNumber", ignore = true)
    @Mapping(target = "enrollmentStatus", ignore = true)
    @Mapping(target = "admissionDate", ignore = true)
    @Mapping(target = "expectedGraduationDate", ignore = true)
    @Mapping(target = "gpa", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateStudentProfileRequestDto dto, @MappingTarget StudentProfile profile);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "enrollmentStatus", source = "enrollmentStatus")
    @Mapping(target = "gpa", source = "gpa")
    @Mapping(target = "admissionDate", source = "admissionDate")
    @Mapping(target = "expectedGraduationDate", source = "expectedGraduationDate")
    void updateAcademicRecord(UpdateStudentAcademicRecordRequestDto dto, @MappingTarget StudentProfile profile);


    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "profilePictureUrl", source = "profilePicture.presignedUrl")
    StudentProfileResponseDto toResponse(StudentProfile profile);

    @Mapping(target = "profilePictureUrl", source = "profilePicture.presignedUrl")
    StudentProfileSummaryResponseDto toSummary(StudentProfile profile);

    @Named("trim")
    default String trim(String value) {
        return value == null ? null : value.trim();
    }
}