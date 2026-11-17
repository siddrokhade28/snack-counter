package com.snackcounter.controller;

import com.snackcounter.dto.ChatMessageResponse;
import com.snackcounter.dto.HrClaimResponse;
import com.snackcounter.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo-only controls for the simulated chat tool and HR system. Would not exist in production. */
@RestController
@RequestMapping("/api/sim")
@Tag(name = "3. Simulation", description = "Demo only: play the HR system, read the chat tool, run the scheduler now")
public class SimulationController {

    private final SimulationService simulation;

    public SimulationController(SimulationService simulation) {
        this.simulation = simulation;
    }

    @GetMapping("/chat")
    @Operation(summary = "Every chat message the system sent (direct messages and #snacks-oslo / #snacks-bengaluru)")
    public List<ChatMessageResponse> chatMessages() {
        return simulation.chatMessages();
    }

    @GetMapping("/hr/claims")
    @Operation(summary = "Claims the HR system received (ids HR-1, HR-2, ...)")
    public List<HrClaimResponse> claims() {
        return simulation.claims();
    }

    @PostMapping("/hr/claims/{claimId}/approve")
    @Operation(summary = "Play HR: approve a claim (then run a tick)")
    public void approveClaim(@PathVariable String claimId) {
        simulation.approveClaim(claimId);
    }

    @PostMapping("/hr/claims/{claimId}/reject")
    @Operation(summary = "Play HR: reject a claim (then run a tick)")
    public void rejectClaim(@PathVariable String claimId) {
        simulation.rejectClaim(claimId);
    }

    /** Run the scheduled checks now instead of waiting a minute: deadlines and HR decisions. */
    @PostMapping("/tick")
    @Operation(summary = "Run the every-minute checks now",
            description = "Hands off missed deadlines and picks up HR decisions (REIMBURSED / CLAIM_REJECTED).")
    public void tick() {
        simulation.tick();
    }
}
