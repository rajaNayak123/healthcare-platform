package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.SlotResponse;
import com.healthcare.appointment.entity.Slot;
import com.healthcare.appointment.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotService {

    private final SlotRepository slotRepository;

    public List<SlotResponse> getAvailableSlots(LocalDate date) {
        log.info("Fetching available slots. filter=date:{}", date != null ? date : "ALL");
        List<Slot> slots = (date != null)
                ? slotRepository.findByIsBookedFalseAndSlotDateOrderByStartTimeAsc(date)
                : slotRepository.findByIsBookedFalseOrderBySlotDateAscStartTimeAsc();

        return slots.stream().map(this::toResponse).toList();
    }

    private SlotResponse toResponse(Slot slot) {
        return SlotResponse.builder()
                .id(slot.getId())
                .doctorName(slot.getDoctorName())
                .specialization(slot.getSpecialization())
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .isBooked(slot.getIsBooked())
                .build();
    }
}
