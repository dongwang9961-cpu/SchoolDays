package com.schooldays.service.pricing;

import static com.schooldays.jooq.generated.tables.ClassFeeItems.CLASS_FEE_ITEMS;
import static com.schooldays.jooq.generated.tables.ClassPricing.CLASS_PRICING;
import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schooldays.dto.pricing.ClassFeeItemRequest;
import com.schooldays.dto.pricing.ClassFeeItemResponse;
import com.schooldays.dto.pricing.ClassPricingResponse;
import com.schooldays.dto.pricing.SaveClassPricingRequest;
import com.schooldays.jooq.generated.tables.records.ClassFeeItemsRecord;
import com.schooldays.jooq.generated.tables.records.ClassPricingRecord;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClassPricingService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DSLContext dsl;

    public ClassPricingService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ClassPricingResponse getPublicPricing(UUID classId) {
        requireClass(classId);
        return findPricing(classId);
    }

    public ClassPricingResponse getPricing(UUID tenantId, UUID classId) {
        requireTenant(tenantId);
        requireClass(tenantId, classId);
        return findPricing(classId);
    }

    public ClassPricingResponse savePricing(UUID tenantId, UUID classId, SaveClassPricingRequest request) {
        requireTenant(tenantId);
        requireClass(tenantId, classId);

        return dsl.transactionResult(configuration -> {
            DSLContext tx = configuration.dsl();
            List<ClassFeeItemRequest> feeItems = normalizedFeeItems(request.feeItems());
            int requiredTotal = feeItems.stream()
                    .filter(item -> "required_fees".equals(item.category()))
                    .mapToInt(ClassFeeItemRequest::fee)
                    .sum();
            String pricingType = requiredTotal == 0 && feeItems.isEmpty() ? "free" : normalizePricingType(request.pricingType());
            String currency = defaultCurrency(request.currency(), feeItems);
            OffsetDateTime now = OffsetDateTime.now();

            ClassPricingRecord pricing = tx.selectFrom(CLASS_PRICING)
                    .where(CLASS_PRICING.TENANT_ID.eq(tenantId))
                    .and(CLASS_PRICING.CLASS_ID.eq(classId))
                    .fetchOne();

            if (pricing == null) {
                pricing = tx.insertInto(CLASS_PRICING)
                        .set(CLASS_PRICING.ID, UUID.randomUUID())
                        .set(CLASS_PRICING.TENANT_ID, tenantId)
                        .set(CLASS_PRICING.CLASS_ID, classId)
                        .set(CLASS_PRICING.PRICING_TYPE, pricingType)
                        .set(CLASS_PRICING.CURRENCY, currency)
                        .set(CLASS_PRICING.TOTAL_AMOUNT, requiredTotal)
                        .set(CLASS_PRICING.STATUS, "active")
                        .set(CLASS_PRICING.METADATA, JSONB.valueOf("{}"))
                        .set(CLASS_PRICING.CREATED_AT, now)
                        .set(CLASS_PRICING.UPDATED_AT, now)
                        .returning()
                        .fetchOne();
            } else {
                pricing = tx.update(CLASS_PRICING)
                        .set(CLASS_PRICING.PRICING_TYPE, pricingType)
                        .set(CLASS_PRICING.CURRENCY, currency)
                        .set(CLASS_PRICING.TOTAL_AMOUNT, requiredTotal)
                        .set(CLASS_PRICING.STATUS, "active")
                        .set(CLASS_PRICING.UPDATED_AT, now)
                        .where(CLASS_PRICING.ID.eq(pricing.getId()))
                        .returning()
                        .fetchOne();
            }

            tx.deleteFrom(CLASS_FEE_ITEMS)
                    .where(CLASS_FEE_ITEMS.CLASS_PRICING_ID.eq(pricing.getId()))
                    .execute();

            for (ClassFeeItemRequest feeItem : feeItems) {
                tx.insertInto(CLASS_FEE_ITEMS)
                        .set(CLASS_FEE_ITEMS.ID, UUID.randomUUID())
                        .set(CLASS_FEE_ITEMS.TENANT_ID, tenantId)
                        .set(CLASS_FEE_ITEMS.CLASS_PRICING_ID, pricing.getId())
                        .set(CLASS_FEE_ITEMS.FEE_TYPE, normalizeCategory(feeItem.category()))
                        .set(CLASS_FEE_ITEMS.NAME, feeItem.name().trim())
                        .set(CLASS_FEE_ITEMS.AMOUNT, feeItem.fee())
                        .set(CLASS_FEE_ITEMS.REQUIRED, "required_fees".equals(feeItem.category()))
                        .set(CLASS_FEE_ITEMS.STATUS, "active")
                        .set(CLASS_FEE_ITEMS.METADATA, metadataFor(feeItem))
                        .set(CLASS_FEE_ITEMS.CREATED_AT, now)
                        .set(CLASS_FEE_ITEMS.UPDATED_AT, now)
                        .execute();
            }

            return responseFor(tx, pricing);
        });
    }

    private ClassPricingResponse findPricing(UUID classId) {
        ClassPricingRecord pricing = dsl.selectFrom(CLASS_PRICING)
                .where(CLASS_PRICING.CLASS_ID.eq(classId))
                .fetchOne();
        if (pricing == null) {
            return ClassPricingResponse.empty(List.of());
        }
        return responseFor(dsl, pricing);
    }

    private ClassPricingResponse responseFor(DSLContext context, ClassPricingRecord pricing) {
        List<ClassFeeItemResponse> feeItems = context.selectFrom(CLASS_FEE_ITEMS)
                .where(CLASS_FEE_ITEMS.CLASS_PRICING_ID.eq(pricing.getId()))
                .orderBy(CLASS_FEE_ITEMS.REQUIRED.desc(), CLASS_FEE_ITEMS.NAME.asc())
                .fetch()
                .stream()
                .map(ClassFeeItemResponse::from)
                .toList();
        return ClassPricingResponse.from(pricing, feeItems);
    }

    private void requireTenant(UUID tenantId) {
        boolean exists = dsl.fetchExists(dsl.selectOne()
                .from(TENANTS)
                .where(TENANTS.ID.eq(tenantId)));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant was not found");
        }
    }

    private void requireClass(UUID classId) {
        boolean exists = dsl.fetchExists(dsl.selectOne()
                .from(CLASSES)
                .where(CLASSES.ID.eq(classId)));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found");
        }
    }

    private void requireClass(UUID tenantId, UUID classId) {
        boolean exists = dsl.fetchExists(dsl.selectOne()
                .from(CLASSES)
                .where(CLASSES.ID.eq(classId))
                .and(CLASSES.TENANT_ID.eq(tenantId)));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found");
        }
    }

    private List<ClassFeeItemRequest> normalizedFeeItems(List<ClassFeeItemRequest> feeItems) {
        if (feeItems == null) {
            return List.of();
        }
        return feeItems.stream()
                .filter(item -> item != null && item.name() != null && !item.name().isBlank()
                        && item.fee() != null && item.fee() > 0)
                .map(item -> new ClassFeeItemRequest(
                        normalizeCategory(item.category()),
                        item.name().trim(),
                        normalizeCurrency(item.currency()),
                        item.fee(),
                        item.note() == null ? "" : item.note().trim()
                ))
                .toList();
    }

    private String normalizePricingType(String pricingType) {
        if (pricingType == null || pricingType.isBlank()) {
            return "paid";
        }
        String normalized = pricingType.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("free") && !normalized.equals("paid")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pricing type must be free or paid");
        }
        return normalized;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "USD";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultCurrency(String requestCurrency, List<ClassFeeItemRequest> feeItems) {
        if (requestCurrency != null && !requestCurrency.isBlank()) {
            return normalizeCurrency(requestCurrency);
        }
        return feeItems.stream()
                .findFirst()
                .map(ClassFeeItemRequest::currency)
                .map(this::normalizeCurrency)
                .orElse("USD");
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "required_fees";
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT).replace(" ", "_");
        if (!normalized.equals("required_fees") && !normalized.equals("optional_fees")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pricing category must be required_fees or optional_fees");
        }
        return normalized;
    }

    private JSONB metadataFor(ClassFeeItemRequest feeItem) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("currency", normalizeCurrency(feeItem.currency()));
        if (feeItem.note() != null && !feeItem.note().isBlank()) {
            root.put("note", feeItem.note().trim());
        }
        return JSONB.valueOf(root.toString());
    }
}
