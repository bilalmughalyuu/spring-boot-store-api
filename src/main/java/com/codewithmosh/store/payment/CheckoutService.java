package com.codewithmosh.store.payment;

import com.codewithmosh.store.entities.Order;
import com.codewithmosh.store.entities.OrderItem;
import com.codewithmosh.store.entities.PaymentStatus;
import com.codewithmosh.store.exceptions.CartEmptyException;
import com.codewithmosh.store.exceptions.CartNotFoundException;
import com.codewithmosh.store.repositories.CartRepository;
import com.codewithmosh.store.repositories.OrderRepository;
import com.codewithmosh.store.services.*;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class CheckoutService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final AuthService authService;
    private final CartService cartService;
    private final PaymentGateway paymentGateway;

    @Value("${websiteUrl}")
    private String websiteUrl;

    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request) {
        var cart = cartRepository.getCartWithItems(request.getCartId()).orElse(null);
        if(cart == null) {
            throw new CartNotFoundException();
        }

        if(cart.isEmpty()) {
            throw new CartEmptyException();
        }

        var order = Order.fromCart(cart, authService.getCurrentUser());

        orderRepository.save(order);

        try {
            var session = paymentGateway.createCheckoutSession(order);

            cartService.clearCart(cart.getId());

            return new CheckoutResponse(order.getId(), session.getCheckoutUrl());
        } catch (PaymentException e) {
            orderRepository.delete(order);
            throw e;
        }

    }

    public void handleWebhookEvent(WebhookRequest request) {
        paymentGateway
                .parseWebhookRequest(request)
                .ifPresent(paymentResult -> {
                    var order = orderRepository
                            .findById(paymentResult.getOrderId())
                            .orElseThrow();
                    order.setStatus(paymentResult.getPaymentStatus());
                    orderRepository.save(order);
                });


    }

    public static interface PaymentGateway {
        CheckoutSession createCheckoutSession(Order order);
        Optional<PaymentResult> parseWebhookRequest(WebhookRequest request);
    }

    @Service
    public static class StripePaymentGateway implements PaymentGateway {
        @Value("${websiteUrl}")
        private String websiteUrl;

        @Value("${stripe.webhookSecretKey}")
        private String webhookSecretKey;

        @Override
        public CheckoutSession createCheckoutSession(Order order) {
            try {
                var builder = SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl(websiteUrl + "/checkout-success?orderId=" + order.getId())
                        .setCancelUrl(websiteUrl + "/checkout-cancel")
                        .setPaymentIntentData(createPaymentIntent(order));

                order.getItems().forEach(item -> {
                    var lineItem = createLineItem(item);
                    builder.addLineItem(lineItem);
                });

                var session = Session.create(builder.build());
                return new CheckoutSession(session.getUrl());
            }
            catch (StripeException ex) {
                System.out.println(ex.getMessage());
                throw new PaymentException();
            }
        }

        @Override
        public Optional<PaymentResult> parseWebhookRequest(WebhookRequest request) {
            try {
                var payload = request.getPayload();
                var signature = request.getHeaders().get("stripe-signature");
                var event = Webhook.constructEvent(payload, signature, webhookSecretKey);


                return switch (event.getType()) {
                    case "payment_intent.succeeded" ->
                            Optional.of(new PaymentResult(extractOrderId(event), PaymentStatus.PAID));
                    case "payment_intent.payment_failed" ->
                            Optional.of(new PaymentResult(extractOrderId(event), PaymentStatus.FAILED));
                    default ->
                         Optional.empty();

                };
            } catch (SignatureVerificationException e) {
                throw new PaymentException("Invalid Signature");
            }
        }

        private static SessionCreateParams.PaymentIntentData createPaymentIntent(Order order) {
            return SessionCreateParams.PaymentIntentData.builder()
                    .putMetadata("order_id", order.getId().toString())
                    .build();
        }

        private Long extractOrderId(Event event) {
            var stripeObject = event.getDataObjectDeserializer().getObject().orElseThrow(
                    () -> new PaymentException("Could not deserialize stripe event. Check the sdk and api version")
            );
            var paymentIntent = (PaymentIntent) stripeObject;
            return Long.valueOf(paymentIntent.getMetadata().get("order_id"));
        }

        private SessionCreateParams.LineItem createLineItem(OrderItem item) {
            return SessionCreateParams.LineItem.builder()
                    .setQuantity(Long.valueOf(item.getQuantity()))
                    .setPriceData(createPriceData(item))
                    .build();
        }

        private SessionCreateParams.LineItem.PriceData createPriceData(OrderItem item) {
            return SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency("usd")
                    .setUnitAmountDecimal(
                            item.getUnitPrice().multiply(BigDecimal.valueOf(100)))
                    .setProductData(createProductData(item))
                    .build();
        }

        private SessionCreateParams.LineItem.PriceData.ProductData createProductData(OrderItem item) {
            return SessionCreateParams.LineItem.PriceData.ProductData.builder()
                    .setName(item.getProduct().getName())
                    .build();
        }
    }

    @AllArgsConstructor
    @Getter
    public static class PaymentResult {
        private Long orderId;
        private PaymentStatus paymentStatus;
    }

    @AllArgsConstructor
    @Getter
    public static class WebhookRequest {
        private Map<String, String> headers;
        private String payload;
    }
}
