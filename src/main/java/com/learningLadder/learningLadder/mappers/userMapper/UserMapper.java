package com.learningLadder.learningLadder.mappers.userMapper;

import com.learningLadder.learningLadder.dto.userDto.LoginRequestDto;
import com.learningLadder.learningLadder.dto.userDto.RegisterRequestDto;
import com.learningLadder.learningLadder.entity.user.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "firstName", source = "firstName", qualifiedByName = "trim")
    @Mapping(target = "lastName", source = "lastName", qualifiedByName = "trim")
    @Mapping(target = "email", source = "email", qualifiedByName = "normalizeEmail")
    User toEntity(RegisterRequestDto dto);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "email", source = "email", qualifiedByName = "normalizeEmail")
    @Mapping(target = "password", source = "password")
    User toLoginUser(LoginRequestDto dto);

    @Named("trim")
    default String trim(String value) {
        return value == null ? null : value.trim();
    }

    @Named("normalizeEmail")
    default String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }
}