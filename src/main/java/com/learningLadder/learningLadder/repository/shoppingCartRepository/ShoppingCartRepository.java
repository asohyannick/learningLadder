package com.learningLadder.learningLadder.repository.shoppingCartRepository;

import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCart;
import com.learningLadder.learningLadder.enums.cartStatus.CartStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShoppingCartRepository extends JpaRepository<ShoppingCart, UUID>, JpaSpecificationExecutor<ShoppingCart> {

    @EntityGraph(attributePaths = "items")
    Optional<ShoppingCart> findFirstByUser_IdAndStatusIn(UUID userId, Collection<CartStatus> statuses);

    @EntityGraph(attributePaths = "items")
    Optional<ShoppingCart> findWithItemsById(UUID id);

    @EntityGraph(attributePaths = "items")
    List<ShoppingCart> findByCouponCodeAndStatusIn(String couponCode, Collection<CartStatus> statuses);
}