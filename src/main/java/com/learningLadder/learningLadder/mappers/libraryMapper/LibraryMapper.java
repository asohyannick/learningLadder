package com.learningLadder.learningLadder.mappers.libraryMapper;

import com.learningLadder.learningLadder.dto.libraryLoanDto.*;
import com.learningLadder.learningLadder.entity.library.Library;
import com.learningLadder.learningLadder.entity.library.LibraryLoan;
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
public interface LibraryMapper {

    @Mapping(target = "availableCopies", source = "totalCopies")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coverImage", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Library toEntity(CreateLibraryBookRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coverImage", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "totalCopies", ignore = true)
    @Mapping(target = "availableCopies", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateLibraryBookRequestDto dto, @MappingTarget Library entity);

    @Mapping(target = "courseId", source = "course.id")
    LibraryBookResponseDto toBookResponse(Library entity);

    List<LibraryBookResponseDto> toBookResponseList(List<Library> entities);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "notes", source = "notes")
    LibraryLoan toEntity(BorrowBookRequestDto dto);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "conditionOnBorrow", source = "conditionOnBorrow")
    @Mapping(target = "dueAt", source = "dueAt")
    void applyIssue(IssueBookRequestDto dto, @MappingTarget LibraryLoan loan);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "returnRequestNote", source = "note")
    void applyReturnRequest(ReturnBookToLibraryRequestDto dto, @MappingTarget LibraryLoan loan);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "conditionOnReturn", source = "conditionOnReturn")
    @Mapping(target = "returnedAt", source = "returnedAt")
    void applyReturnConfirmation(ConfirmBookReturnRequestDto dto, @MappingTarget LibraryLoan loan);

    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "bookTitle", source = "book.title")
    @Mapping(target = "bookIsbn", source = "book.isbn")
    @Mapping(target = "studentId", source = "student.id")
    LibraryLoanResponseDto toLoanResponse(LibraryLoan entity);

    List<LibraryLoanResponseDto> toLoanResponseList(List<LibraryLoan> entities);
}