package com.snackcounter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.snackcounter.config.OpenApiConfig;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The whole app wired by Spring (real application.yml): one refill end to end over HTTP, plus error mapping. */
@SpringBootTest(properties = "logging.level.com.snackcounter=WARN") // levels are JVM-wide: keep later tests quiet
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RefillApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void aRefillEndToEndOverHttp() throws Exception {
        call(put("/api/presence/E100"), "{\"office\":\"OSLO\"}").andExpect(status().isNoContent());
        call(put("/api/presence/E101"), "{\"office\":\"OSLO\"}").andExpect(status().isNoContent());

        String created = call(post("/api/refills/empty"), "{\"office\":\"OSLO\",\"reportedBy\":\"E101\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OFFERED"))
                .andExpect(jsonPath("$.assignee").value("E100"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");

        call(post("/api/refills/" + id + "/commit"), "{\"employeeId\":\"E101\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED"))
                .andExpect(jsonPath("$.assignee").value("E101"));
        call(post("/api/refills/" + id + "/commit"), "{\"employeeId\":\"E100\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already being bought by Lars")));

        call(post("/api/refills/" + id + "/receipt"),
                "{\"employeeId\":\"E101\",\"amount\":450,\"currency\":\"NOK\",\"items\":[\"chips\"],\"receiptImageBase64\":\"AQID\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIM_SUBMITTED"))
                .andExpect(jsonPath("$.receipt.amount.amount").value(450.0))
                .andExpect(jsonPath("$.receipt.amount.currency").value("NOK"))
                .andExpect(jsonPath("$.claimId").value("HR-1"));

        mvc.perform(post("/api/sim/hr/claims/HR-1/approve")).andExpect(status().isOk());
        mvc.perform(post("/api/sim/tick")).andExpect(status().isOk());
        mvc.perform(get("/api/refills/" + id)).andExpect(jsonPath("$.status").value("REIMBURSED"));

        YearMonth osloMonth = YearMonth.from(LocalDate.now(ZoneId.of("Europe/Oslo")));
        mvc.perform(get("/api/reports/monthly").param("month", osloMonth.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offices[0].office").value("OSLO"))
                .andExpect(jsonPath("$.offices[0].spent.amount").value(450.0))
                .andExpect(jsonPath("$.totalSpent.amount").value(450.0))
                .andExpect(jsonPath("$.totalSpent.currency").value("NOK"))
                .andExpect(jsonPath("$.totalSpent.positive").doesNotExist()); // Money serialises as amount + currency only
        mvc.perform(get("/api/sim/chat")).andExpect(jsonPath("$[*].to", hasItem("#snacks-oslo")));
    }

    @Test
    void swaggerDocumentsEveryEndpointInDemoOrder() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Snack Counter Automation API"))
                .andExpect(jsonPath("$.paths['/api/presence/{employeeId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/refills/empty'].post").exists())
                .andExpect(jsonPath("$.paths['/api/refills/{id}/commit'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/refills/{id}/receipt'].post").exists())
                .andExpect(jsonPath("$.paths['/api/refills/{id}/approve'].post").exists())
                .andExpect(jsonPath("$.paths['/api/reports/monthly'].get").exists())
                .andExpect(jsonPath("$.paths['/api/sim/tick'].post").exists())
                .andExpect(jsonPath("$.tags[*].name", hasItem("1. Presence")))
                .andExpect(jsonPath("$.components.schemas.ReportEmptyRequest.properties.office.example").value("OSLO"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> paths = JsonPath.read(spec, "$.paths");
        List<String> tags = JsonPath.read(spec, "$.tags[*].name");
        assertThat(paths.keySet()).as("every endpoint, in demo order").containsExactlyElementsOf(OpenApiConfig.DEMO_ORDER);
        assertThat(tags).containsExactly("1. Presence", "2. Refills", "3. Simulation", "4. Reports");
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void receiptInputMistakesAreClear400sNot500s() throws Exception {
        call(put("/api/presence/E100"), "{\"office\":\"OSLO\"}");
        String created = call(post("/api/refills/empty"), "{\"office\":\"OSLO\",\"reportedBy\":\"E102\"}")
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");
        call(post("/api/refills/" + id + "/commit"), "{\"employeeId\":\"E100\"}").andExpect(status().isOk());
        String receipt = "/api/refills/" + id + "/receipt";

        call(post(receipt), "{\"employeeId\":\"E100\",\"amount\":450,\"currency\":\"XYZ\",\"receiptImageBase64\":\"AQID\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown currency 'XYZ': use NOK for Oslo, INR for Bengaluru"));
        call(post(receipt), "{\"employeeId\":\"E100\",\"amount\":450,\"currency\":\"NOK\",\"receiptImageBase64\":\"not base64!\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("receiptImageBase64 is not valid base64"));
        call(post(receipt), "{\"employeeId\":\"E100\",\"amount\":450,\"currency\":\"nok\",\"items\":[\"chips\",null,\" \"],\"receiptImageBase64\":\"AQID\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIM_SUBMITTED"))
                .andExpect(jsonPath("$.receipt.items").value(org.hamcrest.Matchers.contains("chips")));
    }

    @Test
    void rulesBecomeClearHttpErrors() throws Exception {
        mvc.perform(get("/api/refills/999")).andExpect(status().isNotFound());

        call(put("/api/presence/E100"), "{\"office\":\"OSLO\"}");
        String created = call(post("/api/refills/empty"), "{\"office\":\"OSLO\",\"reportedBy\":\"E100\"}")
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");
        call(post("/api/refills/" + id + "/commit"), "{\"employeeId\":\"E100\"}");

        call(post("/api/refills/" + id + "/receipt"),
                "{\"employeeId\":\"E100\",\"amount\":450,\"currency\":\"INR\",\"receiptImageBase64\":\"AQID\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("paid in NOK")));
        call(post("/api/refills/" + id + "/receipt"),
                "{\"employeeId\":\"E101\",\"amount\":450,\"currency\":\"NOK\",\"receiptImageBase64\":\"AQID\"}")
                .andExpect(status().isForbidden());
        call(post("/api/refills/" + id + "/approve"), "{\"adminId\":\"E100\"}").andExpect(status().isForbidden());
        call(post("/api/refills/empty"), "{\"office\":\"OSLO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("reportedBy must not be blank"));
        call(post("/api/refills/empty"), "{\"office\":\"PARIS\",\"reportedBy\":\"E100\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("OSLO or BENGALURU")));
    }

    @Test
    void springsOwnErrorsKeepTheirStatusInsteadOfBecoming500() throws Exception {
        mvc.perform(get("/api/refills/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'abc' is not a valid id"));
        mvc.perform(get("/api/reports/monthly?month=November")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/refills/1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/no-such-thing")).andExpect(status().isNotFound());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
