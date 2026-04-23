package com.example.enterprisedocumentredactor.domain.model

enum class PiiType(val displayName: String, val emoji: String) {
    PERSON_NAME("Person Name", "👤"),
    EMAIL("Email", "📧"),
    PHONE("Phone", "📞"),
    CREDIT_CARD("Credit Card", "💳"),
    SSN("SSN", "🔒"),
    PASSPORT("Passport", "🛂"),
    MEDICAL_TERM("Medical Term", "🏥"),
    FINANCIAL_TERM("Financial Term", "💰"),
    ADDRESS("Address", "📍"),
    DATE_OF_BIRTH("Date of Birth", "📅"),
    CUSTOM("Custom", "⚠️")
}
