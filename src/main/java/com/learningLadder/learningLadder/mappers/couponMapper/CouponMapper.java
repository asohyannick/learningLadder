package com.learningLadder.learningLadder.mappers.couponMapper;

import com.learningLadder.learningLadder.dto.couponDto.CouponResponseDto;
import com.learningLadder.learningLadder.dto.couponDto.CreateCouponRequestDto;
import com.learningLadder.learningLadder.entity.coupon.Coupon;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CouponMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "assignedUser", ignore = true)
    @Mapping(target = "redemptionCount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "revokedAt", ignore = true)
    @Mapping(target = "revokedBy", ignore = true)
    @Mapping(target = "revokeReason", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "issuerRole", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Coupon toEntity(CreateCouponRequestDto dto);

    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "assignedUserId", source = "assignedUser.id")
    CouponResponseDto toResponse(Coupon coupon);

    List<CouponResponseDto> toResponseList(List<Coupon> coupons);
}