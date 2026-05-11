package com.myplans.auth.service;

import com.myplans.auth.exception.InvalidPasswordException;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidPasswordException("Debes ingresar una contraseña");
        }
        if (password.length() < 8) {
            throw new InvalidPasswordException(
                    "La contraseña debe tener al menos 8 caracteres");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new InvalidPasswordException(
                    "La contraseña debe contener al menos una letra mayúscula");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new InvalidPasswordException(
                    "La contraseña debe contener al menos una letra minúscula");
        }
        if (!password.matches(".*\\d.*")) {
            throw new InvalidPasswordException(
                    "La contraseña debe contener al menos un número");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new InvalidPasswordException(
                    "La contraseña debe contener al menos un carácter especial (!@#$%^&* etc.)");
        }
    }
}
