package com.learningLadder.learningLadder.controller.shoppingCartController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.shoppingCartDto.AddToCartRequestDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.PagedQueryShoppingCartRequestDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.ShoppingCartItemResponseDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.ShoppingCartPageResponseDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.ShoppingCartResponseDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.UpdateCartItemRequestDto;
import com.learningLadder.learningLadder.dto.shoppingCartDto.UpdateShoppingCartRequestDto;
import com.learningLadder.learningLadder.service.shoppingCartService.ShoppingCartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/${app.api.version}/shopping-cart")
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "Shopping Cart Management Endpoints",
        description = """
                Add courses to your cart, save them for later, apply a coupon received from an instructor or
                administrator, and review past carts. Prices are captured when a course is added; the cart flags
                courses whose price changed since. Expired or revoked coupons are removed automatically."""
)
public class ShoppingCartController {

    private static final String STAFF = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'IT_ADMIN')";

    private final ShoppingCartService shoppingCartService;

    @PostMapping(value = "/items",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add a course to my cart",
            description = "Copies the course's current name, price and discount into the cart. A course can be in "
                    + "the cart only once, must be published, and must use the same currency as the courses already "
                    + "in the cart. saveForLater=true keeps it in the cart without buying it at checkout.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Course added"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Course not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Already in cart, already enrolled, not for sale, "
                    + "different currency or cart full",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ShoppingCartResponseDto>> addProductToCart(
            @Valid @RequestBody AddToCartRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        ShoppingCartResponseDto cart = shoppingCartService.addProductToCart(request, currentUserId);

        String message = Boolean.TRUE.equals(request.saveForLater())
                ? "Course saved for later in your cart"
                : "Course added to your cart. " + totalLine(cart);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APISuccessResponse.created(message, cart, currentPath()));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch my cart",
            description = "Returns the cart with all its courses. The coupon is re-checked (expired or revoked "
                    + "coupons are removed) and courses whose price changed are flagged with priceChanged=true.")
    @ApiResponse(responseCode = "200", description = "The cart (empty if none yet)")
    public ResponseEntity<APISuccessResponse<ShoppingCartResponseDto>> fetchAllProductsFromCart(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        ShoppingCartResponseDto cart = shoppingCartService.fetchAllProductsFromCart(currentUserId);

        String message;
        if (cart.items().isEmpty()) {
            message = "Your cart is empty";
        } else if (cart.hasPriceChanges()) {
            message = "Some course prices have changed since you added them. Please review your cart. " + totalLine(cart);
        } else {
            message = "Your cart has " + cart.itemCount() + " course(s) ready to buy. " + totalLine(cart);
        }
        return ResponseEntity.ok(APISuccessResponse.ok(message, cart, currentPath()));
    }

    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Count courses in my cart",
            description = "Courses ready to buy (saved-for-later excluded). Ideal for the cart badge in the header.")
    @ApiResponse(responseCode = "200", description = "Number of courses")
    public ResponseEntity<APISuccessResponse<Integer>> countTotalNumberOfItemsInCart(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        int count = shoppingCartService.countTotalNumberOfItemsInCart(currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                count == 0 ? "Your cart is empty" : "You have " + count + " course(s) in your cart",
                count, currentPath()));
    }

    @GetMapping(value = "/items/{itemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch one course from my cart")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The cart item"),
            @ApiResponse(responseCode = "404", description = "Not in your cart",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ShoppingCartItemResponseDto>> fetchProductFromCart(
            @Parameter(description = "Cart item id") @PathVariable UUID itemId,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        ShoppingCartItemResponseDto item = shoppingCartService.fetchProductFromCart(itemId, currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok("\"" + item.courseTitle() + "\" fetched from your cart",
                item, currentPath()));
    }

    @PatchMapping(value = "/items/{itemId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Save a course for later, or move it back to the cart",
            description = "savedForLater=true removes it from the totals; false moves it back. A coupon that only "
                    + "applies to this course is removed when the course is saved for later.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart updated"),
            @ApiResponse(responseCode = "404", description = "Not in your cart",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Cart already checked out or changed in another tab",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ShoppingCartResponseDto>> updateProductFromCart(
            @Parameter(description = "Cart item id") @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        ShoppingCartResponseDto cart = shoppingCartService.updateProductFromCart(itemId, request, currentUserId);

        String message = request.savedForLater()
                ? "Course saved for later. " + totalLine(cart)
                : "Course moved back to your cart. " + totalLine(cart);
        return ResponseEntity.ok(APISuccessResponse.ok(message, cart, currentPath()));
    }

    @DeleteMapping(value = "/items/{itemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove a course from my cart")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Course removed"),
            @ApiResponse(responseCode = "404", description = "Not in your cart",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ShoppingCartResponseDto>> deleteProductFromCart(
            @Parameter(description = "Cart item id") @PathVariable UUID itemId,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        ShoppingCartResponseDto cart = shoppingCartService.deleteProductFromCart(itemId, currentUserId);

        String message = cart.items().isEmpty()
                ? "Course removed. Your cart is now empty"
                : "Course removed from your cart. " + totalLine(cart);
        return ResponseEntity.ok(APISuccessResponse.ok(message, cart, currentPath()));
    }

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Clear my cart", description = "Removes every course and the coupon.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart cleared"),
            @ApiResponse(responseCode = "404", description = "No cart yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ShoppingCartResponseDto>> clearCart(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        ShoppingCartResponseDto cart = shoppingCartService.clearCart(currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok("Your cart has been cleared", cart, currentPath()));
    }

    @PostMapping(value = "/coupon", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Apply a coupon code",
            description = """
                    Body: {"couponCode": "LL-7KQ2-M9XD"}. Codes are generated by the server and given to you by an
                    instructor or administrator. Revoked, expired, not-yet-active, used-up codes, codes assigned to
                    another student, and codes for a course not in your cart are rejected with the reason.
                    A new coupon replaces the previous one.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coupon applied"),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, revoked or not applicable coupon",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No cart yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ShoppingCartResponseDto>> addCouponCodeToCart(
            @Valid @RequestBody UpdateShoppingCartRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        ShoppingCartResponseDto cart = shoppingCartService.addCouponCodeToCart(request, currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Coupon " + cart.couponCode() + " applied: you save " + money(cart.couponDiscount(), cart.currency())
                        + ". " + totalLine(cart),
                cart, currentPath()));
    }

    @DeleteMapping(value = "/coupon", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove the coupon from my cart")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coupon removed"),
            @ApiResponse(responseCode = "400", description = "No coupon on the cart",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ShoppingCartResponseDto>> removeCouponCodeFromCart(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        ShoppingCartResponseDto cart = shoppingCartService.removeCouponCodeFromCart(currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok("Coupon removed. " + totalLine(cart), cart, currentPath()));
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My carts",
            description = "Your current and past carts without their items, e.g. status=CHECKED_OUT for purchase history.")
    @ApiResponse(responseCode = "200", description = "Page of carts")
    public ResponseEntity<APISuccessResponse<ShoppingCartPageResponseDto>> fetchMyCarts(
            @Valid @ParameterObject @ModelAttribute PagedQueryShoppingCartRequestDto query,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        ShoppingCartPageResponseDto result = shoppingCartService.fetchMyCarts(query, currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                result.totalElements() == 0 ? "No carts found" : "Carts fetched successfully", result, currentPath()));
    }

    @GetMapping(value = "/admin/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Search carts (staff)",
            description = "e.g. status=ABANDONED&minTotal=50 to follow up abandoned carts, or courseId=... to see who "
                    + "has a course in their cart. sortBy: lastActivityAt, createdAt, updatedAt, checkedOutAt, total, itemCount.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching carts"),
            @ApiResponse(responseCode = "403", description = "Not staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ShoppingCartPageResponseDto>> searchCarts(
            @Valid @ParameterObject @ModelAttribute PagedQueryShoppingCartRequestDto query) {

        ShoppingCartPageResponseDto result = shoppingCartService.searchCarts(query);

        return ResponseEntity.ok(APISuccessResponse.ok(
                result.totalElements() == 0 ? "No carts match your search"
                        : "Search completed: " + result.totalElements() + " cart(s) found",
                result, currentPath()));
    }

    @GetMapping(value = "/admin/{cartId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Fetch any cart with its items (staff)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The cart"),
            @ApiResponse(responseCode = "403", description = "Not staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cart not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<ShoppingCartResponseDto>> fetchCartById(
            @Parameter(description = "Cart id") @PathVariable UUID cartId) {

        return ResponseEntity.ok(APISuccessResponse.ok("Cart fetched successfully",
                shoppingCartService.fetchCartById(cartId), currentPath()));
    }

    private static String totalLine(ShoppingCartResponseDto cart) {
        return "Total: " + money(cart.total(), cart.currency()) + " (" + cart.itemCount() + " course(s)).";
    }

    private static String money(BigDecimal amount, String currency) {
        return (amount == null ? BigDecimal.ZERO : amount).toPlainString() + " " + currency;
    }

    private static String currentPath() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
    }
}