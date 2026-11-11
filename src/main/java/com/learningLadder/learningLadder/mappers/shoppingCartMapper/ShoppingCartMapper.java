package com.learningLadder.learningLadder.mappers.shoppingCartMapper;

import com.learningLadder.learningLadder.dto.shoppingCartDto.AddToCartRequestDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.ShoppingCartItemResponseDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.ShoppingCartResponseDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.ShoppingCartSummaryResponseDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.UpdateCartItemRequestDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.UpdateShoppingCartRequestDto;
import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCart;
import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCartItem;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.List;


@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface ShoppingCartMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "savedForLater", source = "saveForLater")
    ShoppingCartItem toItemEntity(AddToCartRequestDto dto);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "savedForLater", source = "savedForLater")
    void updateItem(UpdateCartItemRequestDto dto, @MappingTarget ShoppingCartItem item);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "couponCode", source = "couponCode")
    void updateCart(UpdateShoppingCartRequestDto dto, @MappingTarget ShoppingCart cart);

    @AfterMapping
    default void applyCouponRemoval(UpdateShoppingCartRequestDto dto, @MappingTarget ShoppingCart cart) {
        if (Boolean.TRUE.equals(dto.removeCoupon())) {
            cart.setCouponCode(null);
            cart.setCouponDiscount(BigDecimal.ZERO.setScale(2));
        }
    }

    @Mapping(target = "cartId", source = "cart.id")
    @Mapping(target = "courseId", source = "course.id")
    ShoppingCartItemResponseDto toItemResponse(ShoppingCartItem item);

    List<ShoppingCartItemResponseDto> toItemResponseList(List<ShoppingCartItem> items);

    @Mapping(target = "userId", source = "user.id")
    ShoppingCartResponseDto toResponse(ShoppingCart cart);

    @Mapping(target = "userId", source = "user.id")
    ShoppingCartSummaryResponseDto toSummary(ShoppingCart cart);

    List<ShoppingCartSummaryResponseDto> toSummaryList(List<ShoppingCart> carts);
}