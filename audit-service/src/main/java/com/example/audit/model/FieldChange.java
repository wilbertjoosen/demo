package com.example.audit.model;

public record FieldChange(String field, Object oldValue, Object newValue) {
}
