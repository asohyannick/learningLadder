package com.learningLadder.learningLadder.repository.couponRepository;

import com.learningLadder.learningLadder.entity.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID>, JpaSpecificationExecutor<Coupon> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);
}