package com.healthcare.appointment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.appointment.dto.CreateAppointmentRequest;
import com.healthcare.appointment.entity.Role;
import com.healthcare.appointment.entity.Slot;
import com.healthcare.appointment.entity.User;
import com.healthcare.appointment.event.AppointmentEventProducer;
import com.healthcare.appointment.repository.AppointmentRepository;
import com.healthcare.appointment.repository.SlotRepository;
import com.healthcare.appointment.repository.UserRepository;
import com.healthcare.appointment.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppointmentEventProducer eventProducer;

    private User patient;
    private Slot slot;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        userRepository.deleteAll();

        doNothing().when(eventProducer).publish(any());

        patient = User.builder()
                .fullName("Jane Doe")
                .email("jane.doe@example.com")
                .password("password123")
                .phone("0987654321")
                .role(Role.PATIENT)
                .build();
        patient = userRepository.save(patient);

        slot = Slot.builder()
                .doctorName("Dr. House")
                .specialization("Diagnostic Medicine")
                .slotDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(14, 30))
                .isBooked(false)
                .build();
        slot = slotRepository.save(slot);

        jwtToken = "Bearer " + jwtUtil.generateToken(patient.getEmail(), patient.getId(), patient.getRole().name());
    }

    @Test
    void testCompleteAppointmentFlow() throws Exception {
        // 1. Create Appointment
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setSlotId(slot.getId());

        String responseContent = mockMvc.perform(post("/api/appointments")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("BOOKED")))
                .andExpect(jsonPath("$.patientName", is("Jane Doe")))
                .andExpect(jsonPath("$.slot.doctorName", is("Dr. House")))
                .andReturn().getResponse().getContentAsString();

        Long appointmentId = objectMapper.readTree(responseContent).get("id").asLong();

        // 2. Fetch User's Appointments
        mockMvc.perform(get("/api/appointments/me")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(appointmentId.intValue())))
                .andExpect(jsonPath("$[0].status", is("BOOKED")));

        // 3. Cancel the Appointment
        mockMvc.perform(delete("/api/appointments/" + appointmentId)
                        .header("Authorization", jwtToken))
                .andExpect(status().isNoContent());

        // 4. Fetch Appointments again, verify it is marked CANCELLED
        mockMvc.perform(get("/api/appointments/me")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("CANCELLED")));
    }
}
