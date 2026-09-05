package com.codewithmosh.store.dtos;

import com.codewithmosh.store.validation.Lowercase;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUserRequest {
    @NotBlank(message = "name is required")
    @Size(max = 255, message = "name must be less than 255 characters")
    private String name;

    @NotBlank(message = "email is required")
    @Email(message= "Email must be a valid email")
    @Lowercase(message = "Email must be in lower case")
    private String email;

    @NotBlank(message= "Password is required")
    @Size(min = 6, max = 25, message = "Password must be between 6 to 25 characters long")
    private String password;
}