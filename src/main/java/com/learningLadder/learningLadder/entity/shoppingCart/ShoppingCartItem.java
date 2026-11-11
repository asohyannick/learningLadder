package com.learningLadder.learningLadder.entity.shoppingCart;

import com.learningLadder.learningLadder.entity.course.Course;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One course in a shopping cart. A course can be in a cart only once (no quantity:
 * a user enrolls in a course once).
 * <p>
 * Price and title are copied when the course is added ("snapshot"), so the cart shows
 * what the user saw. The service compares the snapshot with the course's current price
 * at checkout and tells the user if it changed.
 */
@Entity(name = "shopping_cart_item")
@Table(
        name = "shopping_cart_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cart_item_course", columnNames = {"cart_id", "course_id"})
        },
        indexes = {
                @Index(name = "idx_cart_item_cart", columnList = "cart_id"),
                @Index(name = "idx_cart_item_course", columnList = "course_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ShoppingCartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    private ShoppingCart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false)
    private Course course;

    @Column(name = "course_title", nullable = false, length = 255)
    private String courseTitle;

    @Column(name = "course_thumbnail", length = 1024)
    private String courseThumbnail;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "saved_for_later", nullable = false)
    private boolean savedForLater = false;

    @Column(name = "price_changed", nullable = false)
    private boolean priceChanged = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}