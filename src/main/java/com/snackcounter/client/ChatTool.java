package com.snackcounter.client;

import com.snackcounter.model.Office;

/** The company chat tool, as far as this service needs it. Swap the simulated bean for a real adapter. */
public interface ChatTool {

    /** A direct message to one employee. */
    void direct(String employeeId, String message);

    /** A post in the office's snack channel, seen by everyone who follows it. */
    void channel(Office office, String message);
}
