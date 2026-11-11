package com.learningLadder.learningLadder.service.shoppingCartService;

import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.dto.shoppingCartDto.AddToCartRequestDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.PagedQueryShoppingCartRequestDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.ShoppingCartItemResponseDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.ShoppingCartPageResponseDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.ShoppingCartResponseDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.UpdateCartItemRequestDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.UpdateShoppingCartRequestDto;
import com.learningLadder.learningLadder.entity.coupon.Coupon;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCart;
import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCartItem;
import com.learningLadder.learningLadder.enums.cartStatus.CartStatus;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.notFoundRequestException.NotFoundRequestException;
import com.learningLadder.learningLadder.mappers.shoppingCartMapper.ShoppingCartMapper;
import com.learningLadder.learningLadder.repository.couponRepository.CouponRepository;
import com.learningLadder.learningLadder.repository.courseRepository.CourseRepository;
import com.learningLadder.learningLadder.repository.shoppingCartRepository.ShoppingCartRepository;
import com.learningLadder.learningLadder.repository.shoppingCartRepository.ShoppingCartSpecification;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import com.learningLadder.learningLadder.service.shoppingCartService.CourseCommerceAdapter.CourseSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ShoppingCartService {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.SHOPPING_CART_COUNT, key = "#userId"),
            @CacheEvict(cacheNames = {
                    CacheNames.SHOPPING_CART_ITEM, CacheNames.SHOPPING_CART_HISTORY,
                    CacheNames.SHOPPING_CART_SEARCH, CacheNames.SHOPPING_CART_BY_ID
            }, allEntries = true)
    })
    public @interface EvictUserCartCaches {
    }

    private static final List<CartStatus> OPEN_STATUSES = List.of(CartStatus.ACTIVE, CartStatus.ABANDONED);
    private static final int MAX_ITEMS_PER_CART = 50;

    private final ShoppingCartRepository shoppingCartRepository;
    private final ShoppingCartMapper shoppingCartMapper;
    private final CouponRepository couponRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CouponRules couponRules;
    private final CourseCommerceAdapter courseAdapter;
    private final CacheManager cacheManager;

    @EvictUserCartCaches
    public ShoppingCartResponseDto addProductToCart(AddToCartRequestDto dto, UUID userId) {
        Course course = courseRepository.findById(dto.courseId())
                .orElseThrow(() -> new NotFoundRequestException("Course not found with id " + dto.courseId()));
        CourseSnapshot snapshot = courseAdapter.snapshotOf(course);

        if (!snapshot.purchasable()) {
            throw new ConflictRequestException("\"" + snapshot.title() + "\" is not available for purchase");
        }
        if (courseAdapter.isAlreadyEnrolled(userId, course.getId())) {
            throw new ConflictRequestException("You are already enrolled in \"" + snapshot.title() + "\"");
        }

        ShoppingCart cart = getOrCreateOpenCart(userId);

        if (findItemByCourse(cart, course.getId()).isPresent()) {
            throw new ConflictRequestException("\"" + snapshot.title() + "\" is already in your cart");
        }
        if (cart.getItems().size() >= MAX_ITEMS_PER_CART) {
            throw new ConflictRequestException("Your cart can hold at most " + MAX_ITEMS_PER_CART + " courses");
        }
        if (cart.getItems().isEmpty()) {
            cart.setCurrency(snapshot.currency()); // the first course decides the cart's currency
        } else if (!cart.getCurrency().equalsIgnoreCase(snapshot.currency())) {
            throw new ConflictRequestException("\"" + snapshot.title() + "\" is priced in " + snapshot.currency()
                    + " but your cart is in " + cart.getCurrency() + ". Check out or clear your cart first.");
        }

        ShoppingCartItem item = shoppingCartMapper.toItemEntity(dto);
        item.setCourse(course);
        item.setCourseTitle(snapshot.title());
        item.setCourseThumbnail(snapshot.thumbnail());
        item.setUnitPrice(snapshot.price());
        item.setDiscountAmount(snapshot.discount().min(snapshot.price()));
        item.setCurrency(snapshot.currency());

        item.setCart(cart);
        cart.getItems().add(item);

        touch(cart);
        refreshTotals(cart);
        log.info("Course {} added to cart {} of user {}", course.getId(), cart.getId(), userId);
        return save(cart);
    }

    public ShoppingCartResponseDto fetchAllProductsFromCart(UUID userId) {
        Optional<ShoppingCart> open = shoppingCartRepository.findFirstByUser_IdAndStatusIn(userId, OPEN_STATUSES);
        if (open.isEmpty()) {
            return emptyCart(userId);
        }
        ShoppingCart cart = open.get();

        String couponBefore = cart.getCouponCode();
        BigDecimal totalBefore = cart.getTotal();
        long priceFlagsBefore = cart.getItems().stream().filter(ShoppingCartItem::isPriceChanged).count();

        flagPriceChanges(cart);
        refreshTotals(cart);

        long priceFlagsAfter = cart.getItems().stream().filter(ShoppingCartItem::isPriceChanged).count();
        boolean changed = !Objects.equals(couponBefore, cart.getCouponCode())
                || totalBefore.compareTo(cart.getTotal()) != 0
                || priceFlagsBefore != priceFlagsAfter;
        if (changed) {
            evictCartCachesOf(userId); // e.g. an expired coupon was just removed
        }
        return shoppingCartMapper.toResponse(cart);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.SHOPPING_CART_ITEM, key = "#userId + ':' + #itemId")
    public ShoppingCartItemResponseDto fetchProductFromCart(UUID itemId, UUID userId) {
        ShoppingCart cart = requireOpenCart(userId);
        return shoppingCartMapper.toItemResponse(findItem(cart, itemId));
    }

    @EvictUserCartCaches
    public ShoppingCartResponseDto updateProductFromCart(UUID itemId, UpdateCartItemRequestDto dto, UUID userId) {
        ShoppingCart cart = requireOpenCart(userId);
        ShoppingCartItem item = findItem(cart, itemId);

        shoppingCartMapper.updateItem(dto, item);

        touch(cart);
        refreshTotals(cart);
        return save(cart);
    }

    @EvictUserCartCaches
    public ShoppingCartResponseDto deleteProductFromCart(UUID itemId, UUID userId) {
        ShoppingCart cart = requireOpenCart(userId);
        ShoppingCartItem item = findItem(cart, itemId);

        cart.getItems().remove(item);
        if (cart.getItems().isEmpty()) {
            clearCoupon(cart);
        }

        touch(cart);
        refreshTotals(cart);
        log.info("Item {} removed from cart {}", itemId, cart.getId());
        return save(cart);
    }

    @EvictUserCartCaches
    public ShoppingCartResponseDto clearCart(UUID userId) {
        ShoppingCart cart = requireOpenCart(userId);
        cart.getItems().clear();
        clearCoupon(cart);
        touch(cart);
        refreshTotals(cart);
        return save(cart);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.SHOPPING_CART_COUNT, key = "#userId")
    public int countTotalNumberOfItemsInCart(UUID userId) {
        return shoppingCartRepository.findFirstByUser_IdAndStatusIn(userId, OPEN_STATUSES)
                .map(ShoppingCart::getItemCount)
                .orElse(0);
    }

    @EvictUserCartCaches
    public ShoppingCartResponseDto addCouponCodeToCart(UpdateShoppingCartRequestDto dto, UUID userId) {
        if (dto.couponCode() == null) {
            throw new BadRequestException("Please enter a coupon code");
        }
        ShoppingCart cart = requireOpenCart(userId);
        if (cart.getItemCount() == 0) {
            throw new BadRequestException("Add a course to your cart before applying a coupon");
        }

        Coupon coupon = couponRepository.findByCode(dto.couponCode())
                .orElseThrow(() -> new BadRequestException("This coupon code is not valid."));

        couponRules.findProblem(coupon, cart, userId, Instant.now()).ifPresent(problem -> {
            throw new BadRequestException(problem);
        });

        shoppingCartMapper.updateCart(dto, cart);
        touch(cart);
        couponRules.recalculateTotals(cart, coupon);

        log.info("Coupon {} applied to cart {} (discount {} {})",
                coupon.getCode(), cart.getId(), cart.getCouponDiscount(), cart.getCurrency());
        return save(cart);
    }

    @EvictUserCartCaches
    public ShoppingCartResponseDto removeCouponCodeFromCart(UUID userId) {
        ShoppingCart cart = requireOpenCart(userId);
        if (cart.getCouponCode() == null) {
            throw new BadRequestException("There is no coupon on your cart");
        }
        shoppingCartMapper.updateCart(new UpdateShoppingCartRequestDto(null, true), cart);
        touch(cart);
        refreshTotals(cart);
        return save(cart);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.SHOPPING_CART_HISTORY, key = "#userId + ':' + #query")
    public ShoppingCartPageResponseDto fetchMyCarts(PagedQueryShoppingCartRequestDto query, UUID userId) {
        return searchInternal(query.forUser(userId));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.SHOPPING_CART_SEARCH, key = "#query")
    public ShoppingCartPageResponseDto searchCarts(PagedQueryShoppingCartRequestDto query) {
        return searchInternal(query);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.SHOPPING_CART_BY_ID, key = "#cartId")
    public ShoppingCartResponseDto fetchCartById(UUID cartId) {
        return shoppingCartMapper.toResponse(shoppingCartRepository.findWithItemsById(cartId)
                .orElseThrow(() -> new NotFoundRequestException("Cart not found with id " + cartId)));
    }

    @CacheEvict(cacheNames = {
            CacheNames.SHOPPING_CART_HISTORY, CacheNames.SHOPPING_CART_SEARCH, CacheNames.SHOPPING_CART_BY_ID
    }, allEntries = true)
    public int detachCouponFromOpenCarts(String couponCode) {
        List<ShoppingCart> carts = shoppingCartRepository.findByCouponCodeAndStatusIn(couponCode, OPEN_STATUSES);
        for (ShoppingCart cart : carts) {
            clearCoupon(cart);
            couponRules.recalculateTotals(cart, null);
        }
        shoppingCartRepository.saveAll(carts);
        return carts.size();
    }

    private void evictCartCachesOf(UUID userId) {
        Cache count = cacheManager.getCache(CacheNames.SHOPPING_CART_COUNT);
        if (count != null) {
            count.evict(userId);
        }
        for (String name : List.of(CacheNames.SHOPPING_CART_ITEM, CacheNames.SHOPPING_CART_HISTORY,
                CacheNames.SHOPPING_CART_SEARCH, CacheNames.SHOPPING_CART_BY_ID)) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private void refreshTotals(ShoppingCart cart) {
        Coupon coupon = null;
        if (cart.getCouponCode() != null) {
            coupon = couponRepository.findByCode(cart.getCouponCode()).orElse(null);
            Optional<String> problem = coupon == null
                    ? Optional.of("coupon no longer exists")
                    : couponRules.findProblem(coupon, cart, cart.getUser().getId(), Instant.now());
            if (problem.isPresent()) {
                log.info("Coupon {} removed from cart {}: {}", cart.getCouponCode(), cart.getId(), problem.get());
                clearCoupon(cart);
                coupon = null;
            }
        }
        couponRules.recalculateTotals(cart, coupon);
    }

    private void flagPriceChanges(ShoppingCart cart) {
        for (ShoppingCartItem item : cart.getItems()) {
            BigDecimal current = courseAdapter.snapshotOf(item.getCourse()).price();
            item.setPriceChanged(current.compareTo(item.getUnitPrice()) != 0);
        }
    }

    private ShoppingCart getOrCreateOpenCart(UUID userId) {
        Optional<ShoppingCart> existing = shoppingCartRepository.findFirstByUser_IdAndStatusIn(userId, OPEN_STATUSES);
        if (existing.isPresent()) {
            return existing.get();
        }
        ShoppingCart cart = new ShoppingCart();
        cart.setUser(userRepository.getReferenceById(userId));
        try {
            return shoppingCartRepository.saveAndFlush(cart);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictRequestException("Your cart was just updated in another tab. Please try again.");
        }
    }

    private ShoppingCart requireOpenCart(UUID userId) {
        return shoppingCartRepository.findFirstByUser_IdAndStatusIn(userId, OPEN_STATUSES)
                .orElseThrow(() -> new NotFoundRequestException("Your cart is empty"));
    }

    private ShoppingCartItem findItem(ShoppingCart cart, UUID itemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundRequestException("This course is not in your cart"));
    }

    private Optional<ShoppingCartItem> findItemByCourse(ShoppingCart cart, UUID courseId) {
        return cart.getItems().stream().filter(item -> item.getCourse().getId().equals(courseId)).findFirst();
    }

    private void clearCoupon(ShoppingCart cart) {
        cart.setCouponCode(null);
        cart.setCouponDiscount(BigDecimal.ZERO.setScale(2));
    }

    private void touch(ShoppingCart cart) {
        if (!cart.getStatus().isEditable()) {
            throw new ConflictRequestException("This cart has already been checked out and cannot be changed");
        }
        cart.setLastActivityAt(Instant.now());
        if (cart.getStatus() == CartStatus.ABANDONED) {
            cart.setStatus(CartStatus.ACTIVE);
            cart.setAbandonedAt(null);
        }
    }

    private ShoppingCartResponseDto save(ShoppingCart cart) {
        try {
            return shoppingCartMapper.toResponse(shoppingCartRepository.saveAndFlush(cart));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictRequestException("This course is already in your cart");
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictRequestException("Your cart was changed in another tab. Please reload and try again.");
        }
    }

    private ShoppingCartPageResponseDto searchInternal(PagedQueryShoppingCartRequestDto query) {
        Page<ShoppingCart> page = shoppingCartRepository.findAll(
                ShoppingCartSpecification.fromQuery(query), query.toPageable());
        return new ShoppingCartPageResponseDto(shoppingCartMapper.toSummaryList(page.getContent()),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private ShoppingCartResponseDto emptyCart(UUID userId) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new ShoppingCartResponseDto(null, userId, CartStatus.ACTIVE, List.of(), "USD", 0,
                zero, zero, zero, null, zero, null, null, null, null, null, null);
    }

    static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}