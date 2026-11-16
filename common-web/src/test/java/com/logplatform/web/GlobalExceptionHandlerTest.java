package com.logplatform.web;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.logplatform.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
class GlobalExceptionHandlerTest {
  record Body(@NotBlank String name) {}
  @RestController static class TestController {
    @GetMapping("/missing") String missing() { throw ApiException.notFound("nothing here"); }
    @PostMapping("/valid") String valid(@Valid @RequestBody Body b) { return "ok"; }
    @GetMapping("/boom") String boom() { throw new IllegalStateException("secret internals"); }
  }
  MockMvc mvc;
  @BeforeEach void setup() {
    mvc = MockMvcBuilders.standaloneSetup(new TestController()).setControllerAdvice(new GlobalExceptionHandler()).build();
  }
  @Test void apiExceptionBecomesJsonWithCode() throws Exception {
    mvc.perform(get("/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
  @Test void validationErrorListsFields() throws Exception {
    mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
  }
  @Test void unexpectedErrorDoesNotLeakDetails() throws Exception {
    mvc.perform(get("/boom")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("Unexpected error"));
  }
}
