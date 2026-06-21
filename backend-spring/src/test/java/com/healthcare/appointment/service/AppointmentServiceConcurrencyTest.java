package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.CreateAppointmentRequest;
import com.healthcare.appointment.entity.Role;
import com.healthcare.appointment.entity.Slot;
import com.healthcare.appointment.entity.User;
import com.healthcare.appointment.event.AppointmentEventProducer;
import com.healthcare.appointment.repository.AppointmentRepository;
import com.healthcare.appointment.repository.SlotRepository;
import com.healthcare.appointment.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@ActiveProfiles("test")
class AppointmentServiceConcurrencyTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @MockBean
    private AppointmentEventProducer eventProducer;

    private Slot slot;
    private List<User> users = new ArrayList<>();

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        userRepository.deleteAll();

        // Mock Kafka event producer so we don't need a running Kafka instance
        doNothing().when(eventProducer).publish(any());

        // Create a single shared slot
        slot = Slot.builder()
                .doctorName("Dr. Smith")
                .specialization("Cardiology")
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .isBooked(false)
                .build();
        slot = slotRepository.save(slot);

        // Create multiple users who will try to book the same slot concurrently
        for (int i = 0; i < 5; i++) {
            User user = User.builder()
                    .fullName("Patient " + i)
                    .email("patient" + i + "@example.com")
                    .password("password")
                    .phone("123456789" + i)
                    .role(Role.PATIENT)
                    .build();
            users.add(userRepository.save(user));
        }
    }

    @Test
    void testConcurrentBookingSameSlot() throws InterruptedException, ExecutionException {
        int numberOfThreads = users.size();
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (User user : users) {
            Callable<Boolean> task = () -> {
                try {
                    latch.await(); // Wait for all threads to start together
                    CreateAppointmentRequest request = new CreateAppointmentRequest();
                    request.setSlotId(slot.getId());
                    appointmentService.createAppointment(user.getId(), request);
                    successCount.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    return false;
                }
            };
            futures.add(executorService.submit(task));
        }

        // Start all threads concurrently
        latch.countDown();

        // Wait for all threads to finish
        for (Future<Boolean> future : futures) {
            future.get();
        }

        executorService.shutdown();

        // Verify that exactly 1 booking succeeded and 4 failed
        assertEquals(1, successCount.get(), "Exactly one user should succeed in booking the slot");
        assertEquals(numberOfThreads - 1, failureCount.get(), "Remaining users should fail to book the slot");

        // Verify slot is marked booked in DB
        Slot updatedSlot = slotRepository.findById(slot.getId()).orElseThrow();
        assertEquals(true, updatedSlot.getIsBooked());

        // Verify exactly one appointment was saved
        long appointmentCount = appointmentRepository.count();
        assertEquals(1, appointmentCount);
    }
}
