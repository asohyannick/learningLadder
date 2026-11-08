package com.learningLadder.learningLadder.dto.courseDto;

import java.io.Serializable;

public record CoursePdfDto(String fileName, byte[] content) implements Serializable { }