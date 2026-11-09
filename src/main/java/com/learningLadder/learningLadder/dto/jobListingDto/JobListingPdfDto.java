package com.learningLadder.learningLadder.dto.jobListingDto;

import java.io.Serializable;

public record JobListingPdfDto(String fileName, byte[] content) implements Serializable { }