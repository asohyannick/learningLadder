package com.learningLadder.learningLadder.repository.libraryRepository;

import com.learningLadder.learningLadder.entity.library.Library;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface LibraryRepository extends JpaRepository<Library, UUID>, JpaSpecificationExecutor<Library> {

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, UUID id);
}