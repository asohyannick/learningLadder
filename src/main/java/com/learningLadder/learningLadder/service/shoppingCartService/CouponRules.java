package com.learningLadder.learningLadder.service.shoppingCartService;

import com.learningLadder.learningLadder.entity.coupon.Coupon;
import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCart;
import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCartItem;
import com.learningLadder.learningLadder.enums.couponStatus.CouponStatus;
import com.learningLadder.learningLadder.enums.discountType.DiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Coupon code generation, validity rules and cart totals: one place, used by both
 * ShoppingCartService (apply / refresh) and CouponService (issue).
 */
@Component
public class CouponRules {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int MONEY_SCALE = 2;

    private final SecureRandom random = new SecureRandom();

    public String generateCode() {
        return "LL-" + randomBlock(4) + "-" + randomBlock(4);
    }

    public Optional<String> findProblem(Coupon coupon, ShoppingCart cart, UUID userId, Instant now) {
        if (coupon.getStatus() == CouponStatus.REVOKED) {
            return Optional.of("This coupon code has been revoked.");
        }
        if (now.isBefore(coupon.getStartsAt())) {
            return Optional.of("This coupon code is not active yet.");
        }
        if (!now.isBefore(coupon.getExpiresAt())) {
            return Optional.of("This coupon code has expired.");
        }
        if (coupon.getMaxRedemptions() != null && coupon.getRedemptionCount() >= coupon.getMaxRedemptions()) {
            return Optional.of("This coupon code has already been used the maximum number of times.");
        }
        if (coupon.getAssignedUser() != null && !coupon.getAssignedUser().getId().equals(userId)) {
            return Optional.of("This coupon code is not valid for your account.");
        }
        if (coupon.getCourse() != null && findPayableItem(cart, coupon.getCourse().getId()) == null) {
            return Optional.of("This coupon only applies to a specific course. Add that course to your cart first.");
        }
        if (coupon.getDiscountType() == DiscountType.FIXED_AMOUNT
                && !coupon.getCurrency().equalsIgnoreCase(cart.getCurrency())) {
            return Optional.of("This coupon is in " + coupon.getCurrency() + " but your cart is in " + cart.getCurrency() + ".");
        }
        if (coupon.getMinimumCartTotal() != null && amountBeforeCoupon(cart).compareTo(coupon.getMinimumCartTotal()) < 0) {
            return Optional.of("This coupon requires a minimum order of "
                    + coupon.getMinimumCartTotal().toPlainString() + " " + coupon.getCurrency() + ".");
        }
        if (discountFor(coupon, cart).signum() == 0) {
            return Optional.of("This coupon gives no discount on the courses in your cart.");
        }
        return Optional.empty();
    }

    /** Discount the coupon gives on this cart (never more than what it applies to). */
    public BigDecimal discountFor(Coupon coupon, ShoppingCart cart) {
        BigDecimal base;
        if (coupon.getCourse() != null) {
            ShoppingCartItem item = findPayableItem(cart, coupon.getCourse().getId());
            base = item == null ? BigDecimal.ZERO : finalPrice(item);
        } else {
            base = amountBeforeCoupon(cart);
        }
        if (base.signum() <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }

        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = base.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscountAmount() != null) {
                discount = discount.min(coupon.getMaxDiscountAmount());
            }
        } else {
            discount = coupon.getDiscountValue();
        }
        return discount.min(base).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public void recalculateTotals(ShoppingCart cart, Coupon validCouponOrNull) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal itemDiscounts = BigDecimal.ZERO;
        int count = 0;
        for (ShoppingCartItem item : cart.getItems()) {
            if (item.isSavedForLater()) {
                continue;
            }
            count++;
            subtotal = subtotal.add(item.getUnitPrice());
            itemDiscounts = itemDiscounts.add(item.getDiscountAmount());
        }

        BigDecimal couponDiscount = validCouponOrNull == null ? BigDecimal.ZERO : discountFor(validCouponOrNull, cart);
        BigDecimal discounts = itemDiscounts.add(couponDiscount).min(subtotal);

        cart.setItemCount(count);
        cart.setSubtotal(subtotal.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        cart.setCouponDiscount(couponDiscount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        cart.setDiscountTotal(discounts.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        cart.setTotal(subtotal.subtract(discounts).max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal amountBeforeCoupon(ShoppingCart cart) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShoppingCartItem item : cart.getItems()) {
            if (!item.isSavedForLater()) {
                total = total.add(finalPrice(item));
            }
        }
        return total;
    }

    private ShoppingCartItem findPayableItem(ShoppingCart cart, UUID courseId) {
        return cart.getItems().stream()
                .filter(item -> !item.isSavedForLater() && item.getCourse().getId().equals(courseId))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal finalPrice(ShoppingCartItem item) {
        return item.getUnitPrice().subtract(item.getDiscountAmount()).max(BigDecimal.ZERO);
    }

    private String randomBlock(int length) {
        StringBuilder block = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            block.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return block.toString();
    }
}