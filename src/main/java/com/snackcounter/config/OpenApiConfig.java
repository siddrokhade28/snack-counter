package com.snackcounter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI (http://localhost:8085/swagger-ui.html): a demo cheat sheet on top, endpoints in demo order. */
@Configuration
public class OpenApiConfig {

    /** Every endpoint, in the order you'd demo it. A test fails if an endpoint is missing from this list. */
    public static final List<String> DEMO_ORDER = List.of(
            "/api/presence/{employeeId}",
            "/api/refills/empty",
            "/api/refills/{id}/commit",
            "/api/refills/{id}/decline",
            "/api/refills/{id}/receipt",
            "/api/refills/{id}/approve",
            "/api/refills/{id}",
            "/api/refills",
            "/api/sim/hr/claims",
            "/api/sim/hr/claims/{claimId}/approve",
            "/api/sim/hr/claims/{claimId}/reject",
            "/api/sim/tick",
            "/api/sim/chat",
            "/api/reports/monthly");

    private static final String DESCRIPTION = """
            Automates the snack counters in **Oslo (NOK)** and **Bengaluru (INR)**: who buys, making sure only one
            person buys, the monthly budget check, the HR reimbursement claim, and one monthly report.

            **Demo data** (from application.yml):

            | Office | Volunteers | Admin | Budget | Typical purchase |
            |---|---|---|---|---|
            | OSLO | E100 Ingrid, E101 Lars, E102 Sofie | oslo-admin | 6 000 NOK | 300 to 1 200 NOK |
            | BENGALURU | E200 Priya, E201 Arjun, E202 Meera | blr-admin | 15 000 INR | 500 to 3 000 INR |

            **Demo: work top to bottom.** Check in, report the box empty, commit, upload the receipt, play HR and run
            a tick, then read the monthly report. Every call with real outputs, for both offices:
            docs/DEMO-CURLS.md.
            """;

    @Bean
    public OpenAPI snackCounterOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Snack Counter Automation API")
                .version("1.0.0")
                .description(DESCRIPTION));
    }

    /** Swagger UI shows paths in spec order; put them (and the numbered tags) in demo order. */
    @Bean
    public OpenApiCustomizer demoOrder() {
        return openApi -> {
            Paths ordered = new Paths();
            openApi.getPaths().entrySet().stream()
                    .sorted(Comparator.comparingInt(entry -> rank(entry.getKey())))
                    .forEach(entry -> ordered.addPathItem(entry.getKey(), entry.getValue()));
            openApi.setPaths(ordered);
            if (openApi.getTags() != null) {
                openApi.setTags(openApi.getTags().stream().sorted(Comparator.comparing(Tag::getName)).toList());
            }
        };
    }

    private static int rank(String path) {
        int index = DEMO_ORDER.indexOf(path);
        return index < 0 ? DEMO_ORDER.size() : index;
    }
}
