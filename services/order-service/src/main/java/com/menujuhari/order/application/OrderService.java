package com.menujuhari.order.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.menujuhari.order.domain.Coupon;
import com.menujuhari.order.domain.CouponRedemption;
import com.menujuhari.order.domain.CouponRedemptionRepository;
import com.menujuhari.order.domain.CouponRepository;
import com.menujuhari.order.domain.Order;
import com.menujuhari.order.domain.OrderItem;
import com.menujuhari.order.domain.OrderRepository;
import com.menujuhari.order.domain.OrderStatus;
import com.menujuhari.order.infra.config.OrderProperties;
import tools.jackson.databind.ObjectMapper;

/**
 * Logika order & harga sesuai ORD-001..006 + CUP-001..004 (PROJECT_PLAN).
 * Harga = snapshot dari katalog; kupon divalidasi dengan lock pessimistik.
 */
@Service
public class OrderService {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private final OrderRepository orderRepository;
	private final CouponRepository couponRepository;
	private final CouponRedemptionRepository redemptionRepository;
	private final CatalogClient catalogClient;
	private final PaymentClient paymentClient;
	private final EventPublisher eventPublisher;
	private final OrderProperties props;
	private final ObjectMapper objectMapper;

	public OrderService(OrderRepository orderRepository, CouponRepository couponRepository,
			CouponRedemptionRepository redemptionRepository, CatalogClient catalogClient,
			PaymentClient paymentClient, EventPublisher eventPublisher, OrderProperties props,
			ObjectMapper objectMapper) {
		this.orderRepository = orderRepository;
		this.couponRepository = couponRepository;
		this.redemptionRepository = redemptionRepository;
		this.catalogClient = catalogClient;
		this.paymentClient = paymentClient;
		this.eventPublisher = eventPublisher;
		this.props = props;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public Order checkout(UUID userId, String customerEmail, UUID invitationId, String packageCode,
			String couponCode, String idempotencyKey) {
		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
			if (existing.isPresent()) {
				return existing.get(); // ORD-003: idempoten terhadap klik ganda/retry
			}
		}

		CatalogClient.PackageInfo pkg = catalogClient.getPackage(packageCode);
		if (!pkg.active()) {
			throw new PackageInactiveException();
		}

		int subtotal = pkg.priceIdr();
		int discount = 0;
		Coupon appliedCoupon = null;
		if (couponCode != null && !couponCode.isBlank()) {
			appliedCoupon = resolveAndValidateCoupon(couponCode.trim().toUpperCase(), pkg, subtotal, userId);
			discount = computeDiscount(appliedCoupon, subtotal);
		}

		String orderNumber = uniqueOrderNumber();
		Order order = Order.newPending(orderNumber, userId, invitationId, customerEmail, subtotal, discount,
				appliedCoupon == null ? null : appliedCoupon.getCode(),
				idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey);
		order.addItem(OrderItem.forPackage(pkg.code(), pkg.name(), pkg.priceIdr()));
		try {
			order = orderRepository.save(order);
		}
		catch (DataIntegrityViolationException e) {
			// Idempotency key kembar datang hampir bersamaan -> kembalikan order yang ada
			if (idempotencyKey != null && !idempotencyKey.isBlank()) {
				return orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
						.orElseThrow(() -> e);
			}
			throw e;
		}
		if (appliedCoupon != null) {
			redemptionRepository.save(CouponRedemption.of(appliedCoupon, order.getId(), userId, discount));
		}
		log.info("Order dibuat: {} total Rp{} (user {})", orderNumber, order.getTotalIdr(), userId);
		return order;
	}

	/** Validasi kupon tanpa mengunci penukaran (preview di halaman checkout). */
	public CouponPreview previewCoupon(String code, String packageCode, UUID userId) {
		CatalogClient.PackageInfo pkg = catalogClient.getPackage(packageCode);
		if (!pkg.active()) {
			throw new PackageInactiveException();
		}
		Coupon coupon = resolveAndValidateCoupon(code.trim().toUpperCase(), pkg, pkg.priceIdr(), userId);
		return new CouponPreview(coupon.getCode(), computeDiscount(coupon, pkg.priceIdr()), pkg.priceIdr());
	}

	public record CouponPreview(String code, int discountIdr, int subtotalIdr) {
	}

	public org.springframework.data.domain.Page<Order> list(UUID userId, org.springframework.data.domain.Pageable pageable) {
		return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
	}

	public Order getOwned(UUID userId, String orderNumber) {
		return orderRepository.findByOrderNumberAndUserId(orderNumber, userId)
				.orElseThrow(OrderNotFoundException::new);
	}

	@Transactional
	public PaymentClient.PaymentSession startPayment(String orderNumber, UUID userId, String customerEmail) {
		Order order = orderRepository.findByOrderNumberAndUserId(orderNumber, userId)
				.orElseThrow(OrderNotFoundException::new);
		if (order.getStatus() == OrderStatus.PAID) {
			throw new OrderAlreadyPaidException();
		}
		if (order.getStatus() != OrderStatus.PENDING) {
			throw new OrderNotPayableException();
		}
		String itemName = order.getItems().isEmpty() ? "Paket Undangan"
				: order.getItems().get(0).getNameSnapshot();
		return paymentClient.createSession(order.getOrderNumber(), order.getTotalIdr(), itemName,
				customerEmail != null ? customerEmail : order.getCustomerEmail());
	}

	/** Konsumen event payment.settled (mh:stream:payments) — sumber satu-satunya status PAID. */
	@Transactional
	public void applyPaymentSettled(String orderNumber, String paymentType, Instant paidAt) {
		Order order = orderRepository.findByOrderNumber(orderNumber).orElse(null);
		if (order == null) {
			log.warn("payment.settled untuk order tak dikenal: {}", orderNumber);
			return;
		}
		if (order.getStatus() == OrderStatus.PAID) {
			return; // idempoten (PAY-002)
		}
		if (order.getStatus() == OrderStatus.EXPIRED) {
			// Uang benar-benar masuk meski order kedaluwarsa -> tetap PAID (kebijakan K6)
			log.info("Settlement terlambat diterima untuk order {}", orderNumber);
		}
		if (order.getStatus() == OrderStatus.REFUNDED || order.getStatus() == OrderStatus.CANCELLED) {
			log.warn("Settlement diterima untuk order {} (status {})", orderNumber, order.getStatus());
			return;
		}
		order.markPaid(paidAt == null ? Instant.now() : paidAt);
		log.info("Order {} PAID via {}", orderNumber, paymentType);
		eventPublisher.publishOrderPaid(order.getOrderNumber(), order.getInvitationId(), order.getUserId());
	}

	@Transactional
	public int expireStaleOrders() {
		Instant cutoff = Instant.now().minus(Duration.ofHours(props.orderTtlHours()));
		List<Order> stale = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);
		for (Order order : stale) {
			order.markExpired();
			if (order.getCouponCode() != null) {
				redemptionRepository.deleteByOrderId(order.getId());
			}
		}
		if (!stale.isEmpty()) {
			log.info("Order kedaluwarsa: {} buah", stale.size());
		}
		return stale.size();
	}

	private Coupon resolveAndValidateCoupon(String code, CatalogClient.PackageInfo pkg, int amount, UUID userId) {
		// Lock pessimistik: batas penukaran global & per-user aman antar request (CUP-003)
		Coupon coupon = couponRepository.lockByCode(code.toLowerCase())
				.orElseThrow(CouponInvalidException::new);

		Instant now = Instant.now();
		if (!coupon.isActive()
				|| (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt()))
				|| (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt()))) {
			throw new CouponInactiveException();
		}
		if (amount < coupon.getMinTransactionIdr()) {
			throw new CouponMinTransactionException();
		}
		if (coupon.getAllowedPackages() != null && !allowedPackages(coupon).contains(pkg.code())) {
			throw new CouponPackageNotAllowedException();
		}
		if (coupon.getMaxRedemptions() != null
				&& redemptionRepository.countByCouponId(coupon.getId()) >= coupon.getMaxRedemptions()) {
			throw new CouponQuotaExceededException();
		}
		if (redemptionRepository.countByCouponIdAndUserId(coupon.getId(), userId) >= coupon.getMaxPerUser()) {
			throw new CouponPerUserLimitException();
		}
		return coupon;
	}

	private List<String> allowedPackages(Coupon coupon) {
		try {
			return objectMapper.readValue(coupon.getAllowedPackages(),
					new tools.jackson.core.type.TypeReference<List<String>>() {
					});
		}
		catch (Exception e) {
			return List.of();
		}
	}

	private int computeDiscount(Coupon coupon, int amount) {
		int discount = coupon.getKind() == Coupon.Kind.PERCENT
				? (amount * coupon.getValue()) / 100
				: coupon.getValue();
		if (coupon.getMaxDiscountIdr() != null && discount > coupon.getMaxDiscountIdr()) {
			discount = coupon.getMaxDiscountIdr();
		}
		return Math.min(discount, amount); // total tak pernah negatif
	}

	private String uniqueOrderNumber() {
		String date = LocalDate.now(WIB).format(DATE);
		for (int attempt = 0; attempt < props.orderNumberMaxAttempts(); attempt++) {
			StringBuilder suffix = new StringBuilder();
			for (int i = 0; i < 6; i++) {
				suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
			}
			String candidate = "MH-" + date + "-" + suffix;
			if (orderRepository.findByOrderNumber(candidate).isEmpty()) {
				return candidate;
			}
		}
		throw new IllegalStateException("Gagal membuat nomor order unik");
	}

	public static class PackageInactiveException extends RuntimeException {
		public PackageInactiveException() {
			super("Paket tidak aktif");
		}
	}

	public static class CouponInvalidException extends RuntimeException {
		public CouponInvalidException() {
			super("Kupon tidak ditemukan");
		}
	}

	public static class CouponInactiveException extends RuntimeException {
		public CouponInactiveException() {
			super("Kupon tidak berlaku");
		}
	}

	public static class CouponMinTransactionException extends RuntimeException {
		public CouponMinTransactionException() {
			super("Transaksi belum memenuhi minimum kupon");
		}
	}

	public static class CouponPackageNotAllowedException extends RuntimeException {
		public CouponPackageNotAllowedException() {
			super("Kupon tidak berlaku untuk paket ini");
		}
	}

	public static class CouponQuotaExceededException extends RuntimeException {
		public CouponQuotaExceededException() {
			super("Kuota kupon habis");
		}
	}

	public static class CouponPerUserLimitException extends RuntimeException {
		public CouponPerUserLimitException() {
			super("Anda telah menggunakan kupon ini");
		}
	}

	public static class OrderNotFoundException extends RuntimeException {
		public OrderNotFoundException() {
			super("Order tidak ditemukan");
		}
	}

	public static class OrderAlreadyPaidException extends RuntimeException {
		public OrderAlreadyPaidException() {
			super("Order sudah dibayar");
		}
	}

	public static class OrderNotPayableException extends RuntimeException {
		public OrderNotPayableException() {
			super("Order tidak dapat dibayar");
		}
	}
}
