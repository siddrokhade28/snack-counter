package com.snackcounter.client;

import com.snackcounter.model.Office;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stand-in for the chat tool: records every message so tests and the demo API can read them back. */
@Component
public class SimulatedChatTool implements ChatTool {

    private static final Logger log = LoggerFactory.getLogger(SimulatedChatTool.class);

    /** {@code to} is an employee id for direct messages, or a channel name like {@code #snacks-oslo}. */
    public record ChatMessage(Instant at, String to, String text) {}

    private final Clock clock;
    private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();

    public SimulatedChatTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void direct(String employeeId, String message) {
        send(employeeId, message);
    }

    @Override
    public void channel(Office office, String message) {
        send(office.channel(), message);
    }

    /** Records the message and prints it, so the console shows what people would see in the chat tool. */
    private void send(String to, String message) {
        log.info("[chat -> {}] {}", to, message);
        messages.add(new ChatMessage(clock.instant(), to, message));
    }

    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    public List<String> textsTo(String recipient) {
        return messages.stream().filter(m -> m.to().equals(recipient)).map(ChatMessage::text).toList();
    }

    public void clear() {
        messages.clear();
    }
}
