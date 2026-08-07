package com.example.audit;

public record FieldChange(String field, Object oldValue, Object newValue) {
}
