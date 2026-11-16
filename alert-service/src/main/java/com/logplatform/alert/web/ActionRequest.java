package com.logplatform.alert.web;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
public record ActionRequest(@NotBlank @Size(max=150) String value){}
