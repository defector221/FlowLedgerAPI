package com.flowledger.commerce.fulfillment.delivery;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.fulfillment.delivery.entity.DeliverySlot;
import com.flowledger.commerce.fulfillment.delivery.entity.PickupSlot;
import com.flowledger.commerce.fulfillment.delivery.entity.SlotBooking;
import com.flowledger.commerce.fulfillment.delivery.repository.DeliverySlotRepository;
import com.flowledger.commerce.fulfillment.delivery.repository.PickupSlotRepository;
import com.flowledger.commerce.fulfillment.delivery.repository.SlotBookingRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SlotBookingService {
    private final DeliverySlotRepository deliverySlots;
    private final PickupSlotRepository pickupSlots;
    private final SlotBookingRepository bookings;

    public SlotBookingService(
            DeliverySlotRepository deliverySlots,
            PickupSlotRepository pickupSlots,
            SlotBookingRepository bookings) {
        this.deliverySlots = deliverySlots;
        this.pickupSlots = pickupSlots;
        this.bookings = bookings;
    }

    public List<CommerceDtos.FulfillmentSlotResponse> listSlots(UUID storeId, String type, LocalDate date) {
        List<CommerceDtos.FulfillmentSlotResponse> result = new ArrayList<>();
        if ("DELIVERY".equalsIgnoreCase(type) || type == null || type.isBlank()) {
            for (DeliverySlot slot : deliverySlots.findByStoreIdAndSlotDateAndActiveTrue(storeId, date)) {
                result.add(new CommerceDtos.FulfillmentSlotResponse(
                        slot.getId(),
                        slot.getStoreId(),
                        "DELIVERY",
                        slot.getSlotDate(),
                        slot.getStartTime().toString(),
                        slot.getEndTime().toString(),
                        slot.getMaxOrders(),
                        slot.getBookedCount()));
            }
        }
        if ("PICKUP".equalsIgnoreCase(type) || type == null || type.isBlank()) {
            for (PickupSlot slot : pickupSlots.findByStoreIdAndSlotDateAndActiveTrue(storeId, date)) {
                result.add(new CommerceDtos.FulfillmentSlotResponse(
                        slot.getId(),
                        slot.getStoreId(),
                        "PICKUP",
                        slot.getSlotDate(),
                        slot.getStartTime().toString(),
                        slot.getEndTime().toString(),
                        slot.getMaxOrders(),
                        slot.getBookedCount()));
            }
        }
        return result;
    }

    public SlotBooking bookDeliverySlot(UUID slotId, UUID checkoutSessionId) {
        DeliverySlot slot = deliverySlots.findById(slotId).orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
        if (slot.getBookedCount() >= slot.getMaxOrders()) {
            throw new BusinessException("Delivery slot is full");
        }
        slot.setBookedCount(slot.getBookedCount() + 1);
        deliverySlots.save(slot);
        SlotBooking booking = new SlotBooking();
        booking.setSlotType("DELIVERY");
        booking.setDeliverySlotId(slotId);
        return bookings.save(booking);
    }

    public long openPickingTasks(UUID storeId) {
        return deliverySlots.findByStoreIdAndSlotDateAndActiveTrue(storeId, LocalDate.now()).stream()
                .mapToInt(s -> s.getMaxOrders() - s.getBookedCount())
                .sum();
    }
}
