package com.learningLadder.learningLadder.service.shoppingCartService;

import com.learningLadder.learningLadder.dto.couponDto.CouponPageResponseDto;
import com.learningLadder.learningLadder.dto.couponDto.CouponResponseDto;
import com.learningLadder.learningLadder.dto.couponDto.CreateCouponRequestDto;
import com.learningLadder.learningLadder.dto.couponDto.PagedQueryCouponRequestDto;
import com.learningLadder.learningLadder.dto.couponDto.RevokeCouponRequestDto;
import com.learningLadder.learningLadder.entity.coupon.Coupon;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.enums.couponStatus.CouponStatus;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.notFoundRequestException.NotFoundRequestException;
import com.learningLadder.learningLadder.mappers.couponMapper.CouponMapper;
import com.learningLadder.learningLadder.repository.couponRepository.CouponRepository;
import com.learningLadder.learningLadder.repository.couponRepository.CouponSpecification;
import com.learningLadder.learningLadder.repository.courseRepository.CourseRepository;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Issuing and revoking coupons. Codes are always generated here, never by a client.
 * <ul>
 *   <li>Admins: any coupon, platform-wide or for any course; can revoke any coupon.</li>
 *   <li>Instructors: only for courses they teach; can revoke only coupons they issued.</li>
 *   <li>Students: can list the usable coupons assigned to them.</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private static final Set<String> ADMIN_ROLES = Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_IT_ADMIN");
    private static final Set<String> INSTRUCTOR_ROLES = Set.of("ROLE_LECTURER", "ROLE_PROFESSOR", "ROLE_ASSISTANT_LECTURER");
    private static final int CODE_GENERATION_ATTEMPTS = 5;

    private final CouponRepository couponRepository;
    private final CouponMapper couponMapper;
    private final CouponRules couponRules;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseCommerceAdapter courseAdapter;
    private final ShoppingCartService shoppingCartService;

    /** Generates a unique code and stores the coupon. The response contains the code to give to students. */
    public CouponResponseDto issueCoupon(CreateCouponRequestDto dto, UUID issuerId, Collection<String> authorities) {
        boolean admin = hasAny(authorities, ADMIN_ROLES);
        boolean instructor = hasAny(authorities, INSTRUCTOR_ROLES);
        if (!admin && !instructor) {
            throw ShoppingCartService.forbidden("Only administrators and instructors can issue coupons");
        }

        Coupon coupon = couponMapper.toEntity(dto);

        if (dto.courseId() != null) {
            Course course = courseRepository.findById(dto.courseId())
                    .orElseThrow(() -> new NotFoundRequestException("Course not found with id " + dto.courseId()));
            if (!admin && !courseAdapter.isInstructorOfCourse(course, issuerId)) {
                throw ShoppingCartService.forbidden("You can only issue coupons for courses you teach");
            }
            coupon.setCourse(course);
        } else if (!admin) {
            throw new BadRequestException("Instructors must choose one of their courses (courseId) for a coupon");
        }

        if (dto.assignedUserId() != null) {
            coupon.setAssignedUser(userRepository.findById(dto.assignedUserId())
                    .orElseThrow(() -> new NotFoundRequestException("Student not found with id " + dto.assignedUserId())));
        }

        coupon.setStartsAt(dto.startsAt() == null ? Instant.now() : dto.startsAt());
        coupon.setCreatedBy(issuerId);
        coupon.setIssuerRole(admin ? "ADMIN" : "INSTRUCTOR");
        coupon.setCode(generateUniqueCode());

        Coupon saved;
        try {
            saved = couponRepository.saveAndFlush(coupon);
        } catch (BadRequestException ex) {
            throw new ConflictRequestException("Could not generate a unique coupon code. Please try again.");
        }

        log.info("Coupon {} issued by {} ({}): {} {} until {}", saved.getCode(), issuerId, saved.getIssuerRole(),
                saved.getDiscountType(), saved.getDiscountValue(), saved.getExpiresAt());
        return couponMapper.toResponse(saved);
    }

    public CouponResponseDto revokeCoupon(UUID couponId, RevokeCouponRequestDto dto, UUID userId,
                                          Collection<String> authorities) {
        Coupon coupon = findCoupon(couponId);
        ensureCanManage(coupon, userId, authorities);

        if (coupon.getStatus() == CouponStatus.REVOKED) {
            throw new ConflictRequestException("This coupon was already revoked on " + coupon.getRevokedAt());
        }

        coupon.setStatus(CouponStatus.REVOKED);
        coupon.setRevokedAt(Instant.now());
        coupon.setRevokedBy(userId);
        coupon.setRevokeReason(dto.reason());

        Coupon saved;
        try {
            saved = couponRepository.saveAndFlush(coupon);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictRequestException("This coupon was changed at the same time. Please reload and try again.");
        }

        int carts = shoppingCartService.detachCouponFromOpenCarts(saved.getCode());
        log.info("Coupon {} revoked by {} (removed from {} open cart(s)): {}", saved.getCode(), userId, carts, dto.reason());
        return couponMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CouponResponseDto fetchCoupon(UUID couponId, UUID userId, Collection<String> authorities) {
        Coupon coupon = findCoupon(couponId);
        ensureCanManage(coupon, userId, authorities);
        return couponMapper.toResponse(coupon);
    }

    @Transactional(readOnly = true)
    public CouponPageResponseDto fetchCoupons(PagedQueryCouponRequestDto query, UUID userId,
                                              Collection<String> authorities) {
        boolean admin = hasAny(authorities, ADMIN_ROLES);
        if (!admin && !hasAny(authorities, INSTRUCTOR_ROLES)) {
            throw ShoppingCartService.forbidden("Only administrators and instructors can list coupons");
        }
        return search(admin ? query : query.forIssuer(userId));
    }

    @Transactional(readOnly = true)
    public CouponPageResponseDto fetchMyCoupons(PagedQueryCouponRequestDto query, UUID studentId) {
        return search(query.forStudent(studentId));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = couponRules.generateCode();
            if (!couponRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new ConflictRequestException("Could not generate a unique coupon code. Please try again.");
    }

    private void ensureCanManage(Coupon coupon, UUID userId, Collection<String> authorities) {
        if (hasAny(authorities, ADMIN_ROLES)) {
            return;
        }
        if (hasAny(authorities, INSTRUCTOR_ROLES) && coupon.getCreatedBy().equals(userId)) {
            return;
        }
        throw ShoppingCartService.forbidden("You can only manage coupons you issued");
    }

    private Coupon findCoupon(UUID id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Coupon not found with id " + id));
    }

    private CouponPageResponseDto search(PagedQueryCouponRequestDto query) {
        Page<Coupon> page = couponRepository.findAll(CouponSpecification.fromQuery(query), query.toPageable());
        return new CouponPageResponseDto(couponMapper.toResponseList(page.getContent()),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private static boolean hasAny(Collection<String> authorities, Set<String> roles) {
        return authorities != null && authorities.stream().anyMatch(roles::contains);
    }
}