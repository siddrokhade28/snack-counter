package com.snackcounter.service;

import com.snackcounter.client.SimulatedChatTool;
import com.snackcounter.client.SimulatedHrSystem;
import com.snackcounter.dto.ChatMessageResponse;
import com.snackcounter.dto.HrClaimResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/** Demo-only: reads and drives the simulated chat tool and HR system for the walkthrough. */
@Service
public class SimulationService {

    private final SimulatedChatTool chat;
    private final SimulatedHrSystem hr;
    private final RefillService refills;

    public SimulationService(SimulatedChatTool chat, SimulatedHrSystem hr, RefillService refills) {
        this.chat = chat;
        this.hr = hr;
        this.refills = refills;
    }

    public List<ChatMessageResponse> chatMessages() {
        return chat.messages().stream().map(m -> new ChatMessageResponse(m.at(), m.to(), m.text())).toList();
    }

    public List<HrClaimResponse> claims() {
        return hr.claims().stream()
                .map(c -> new HrClaimResponse(c.id(), c.request().employeeId(), c.request().amount(),
                        c.request().description(), c.status().name()))
                .toList();
    }

    public void approveClaim(String claimId) {
        hr.approve(claimId);
    }

    public void rejectClaim(String claimId) {
        hr.reject(claimId);
    }

    /** Runs what the scheduler runs every minute, right now. */
    public void tick() {
        refills.runScheduledChecks();
    }
}
