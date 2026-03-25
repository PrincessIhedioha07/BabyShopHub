package com.babyshophub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
@Tag(name = "Support", description = "Customer support tickets, FAQ, and feedback")
@SecurityRequirement(name = "bearerAuth")
public class SupportController {

    @Operation(summary = "Submit a support ticket")
    @PostMapping("/tickets")
    public ResponseEntity<Map<String, Object>> createTicket(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "ticketId", "TKT-" + System.currentTimeMillis(),
            "status",   "OPEN",
            "message",  "Ticket submitted. Our team will respond within 24 hours."
        ));
    }

    @Operation(summary = "List my tickets")
    @GetMapping("/tickets")
    public ResponseEntity<List<Map<String, Object>>> myTickets() {
        return ResponseEntity.ok(List.of(
            Map.of("ticketId", "TKT-001", "type", "Order Issue", "status", "OPEN", "createdAt", "2026-02-20")
        ));
    }

    @Operation(summary = "Get FAQs")
    @GetMapping("/faq")
    public ResponseEntity<List<Map<String, String>>> getFaq() {
        return ResponseEntity.ok(List.of(
            Map.of("q", "What is your return policy?", "a", "30-day hassle-free returns on all items in original condition."),
            Map.of("q", "Is free delivery available?", "a", "Free 2-day standard delivery on orders over $40."),
            Map.of("q", "Are all products safety certified?", "a", "Yes – every product meets ASTM, CPSC, or EN71 standards."),
            Map.of("q", "How do I track my order?", "a", "Open the Orders section and tap your order for real-time tracking."),
            Map.of("q", "Can I cancel my order?", "a", "You can cancel before shipment. Once shipped, use our returns flow.")
        ));
    }

    @Operation(summary = "Submit app feedback / rating")
    @PostMapping("/feedback")
    public ResponseEntity<Map<String, String>> submitFeedback(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("message", "Thank you for your feedback!"));
    }
}
