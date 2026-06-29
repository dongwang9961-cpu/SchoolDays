package com.schooldays.controller;

import java.util.Map;
import java.util.UUID;

import com.schooldays.dto.api.EndpointStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController extends ApiPlaceholderSupport {

    @PostMapping("/api/enrollments/{enrollmentId}/stripe-checkout-sessions")
    public ResponseEntity<EndpointStatusResponse> createStripeCheckoutSession(
            @PathVariable("enrollmentId") UUID enrollmentId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/enrollments/{enrollmentId}/stripe-checkout-sessions");
    }

    @PostMapping("/api/webhooks/stripe")
    public ResponseEntity<EndpointStatusResponse> handleStripeWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature
    ) {
        return notImplemented("POST /api/webhooks/stripe");
    }

    @PostMapping("/api/enrollments/{enrollmentId}/offline-payments")
    public ResponseEntity<EndpointStatusResponse> recordOfflinePayment(
            @PathVariable("enrollmentId") UUID enrollmentId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/enrollments/{enrollmentId}/offline-payments");
    }

    @PostMapping("/api/enrollments/{enrollmentId}/payment-receipts")
    public ResponseEntity<EndpointStatusResponse> uploadPaymentReceipt(
            @PathVariable("enrollmentId") UUID enrollmentId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/enrollments/{enrollmentId}/payment-receipts");
    }

    @PostMapping("/api/tenants/{tenantId}/payment-receipts/{receiptId}/approve")
    public ResponseEntity<EndpointStatusResponse> approvePaymentReceipt(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("receiptId") UUID receiptId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenants/{tenantId}/payment-receipts/{receiptId}/approve");
    }

    @PostMapping("/api/tenants/{tenantId}/payment-receipts/{receiptId}/reject")
    public ResponseEntity<EndpointStatusResponse> rejectPaymentReceipt(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("receiptId") UUID receiptId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenants/{tenantId}/payment-receipts/{receiptId}/reject");
    }

    @GetMapping("/api/parents/me/payments")
    public ResponseEntity<EndpointStatusResponse> listParentPayments() {
        return notImplemented("GET /api/parents/me/payments");
    }

    @GetMapping("/api/tenants/{tenantId}/payments")
    public ResponseEntity<EndpointStatusResponse> listTenantPayments(@PathVariable("tenantId") UUID tenantId) {
        return notImplemented("GET /api/tenants/{tenantId}/payments");
    }

    @PostMapping("/api/tenants/{tenantId}/payments/{paymentId}/refund")
    public ResponseEntity<EndpointStatusResponse> refundPayment(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("paymentId") UUID paymentId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenants/{tenantId}/payments/{paymentId}/refund");
    }
}
