package com.learningLadder.learningLadder.entity.shoppingCart;

import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.cartStatus.CartStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "shopping_cart")
@Table(
        name = "shopping_cart",
        indexes = {
                @Index(name = "idx_shopping_cart_user_status", columnList = "user_id, status"),
                @Index(name = "idx_shopping_cart_status_activity", columnList = "status, last_activity_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ShoppingCart {

    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CartStatus status = CartStatus.ACTIVE;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ShoppingCartItem> items = new ArrayList<>();

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "item_count", nullable = false)
    private int itemCount = 0;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO.setScale(MONEY_SCALE);

    @Column(name = "discount_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO.setScale(MONEY_SCALE);

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO.setScale(MONEY_SCALE);

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "coupon_discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal couponDiscount = BigDecimal.ZERO.setScale(MONEY_SCALE);

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(name = "checkout_reference", length = 100)
    private String checkoutReference;

    @Column(name = "abandoned_at")
    private Instant abandonedAt;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        if (lastActivityAt == null) {
            lastActivityAt = Instant.now();
        }
    }
}