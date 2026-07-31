package com.patrick.fintech.loan_backend.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Field-level AES-256-GCM encryption for sensitive PII columns (national ID,
 * phone, address, spouse details) — applied via @Convert on the entity field.
 *
 * This encrypts the actual column VALUE, independent of database access
 * controls or disk encryption: even someone with raw read access to the
 * database (a leaked backup, a compromised DB user, a cloud provider
 * incident) sees ciphertext, not the plaintext national ID or phone number.
 *
 * The actual crypto and key-versioning logic lives in {@link PiiCrypto} —
 * shared with the PII key-rotation tool so a compromised key can actually
 * be rotated (see PiiCrypto's javadoc and SECRETS_AND_KEY_ROTATION.md).
 *
 * Note on wiring: JPA AttributeConverters are instantiated by Hibernate via
 * reflection, not by Spring's DI container, so PiiCrypto deliberately reads
 * keys straight from the environment (System.getenv) rather than using
 * @Value — @Value injection into a Hibernate-managed converter is not
 * reliable across Hibernate/Spring Boot version combinations.
 */
@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return PiiCrypto.encryptCurrent(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return PiiCrypto.decrypt(stored);
    }
}