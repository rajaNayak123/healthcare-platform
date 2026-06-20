package com.healthcare.appointment.config;

import com.healthcare.appointment.entity.Slot;
import com.healthcare.appointment.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final SlotRepository slotRepository;

    private static final List<String> DOCTORS = List.of(
            "Dr. Asha Mehta", "Dr. Rohan Verma", "Dr. Priya Nair"
    );
    private static final List<String> SPECIALIZATIONS = List.of(
            "General Physician", "Cardiologist", "Dermatologist"
    );

    @Override
    public void run(String... args) {
        if (slotRepository.count() > 0) {
            return;
        }

        for (int day = 1; day <= 5; day++) {
            LocalDate date = LocalDate.now().plusDays(day);
            for (int i = 0; i < DOCTORS.size(); i++) {
                for (int hour = 9; hour <= 16; hour += 2) {
                    Slot slot = Slot.builder()
                            .doctorName(DOCTORS.get(i))
                            .specialization(SPECIALIZATIONS.get(i))
                            .slotDate(date)
                            .startTime(LocalTime.of(hour, 0))
                            .endTime(LocalTime.of(hour + 1, 0))
                            .isBooked(false)
                            .build();
                    slotRepository.save(slot);
                }
            }
        }
    }
}
