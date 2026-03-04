package org.bson.codecs.record.records;

public record LargeRecord(
        String firstName,
        String lastName,
        String emailAddress,
        String phoneNumber,
        String streetAddress,
        String cityName,
        String stateName,
        String postalCode,
        String countryCode,
        int accountId,
        int customerId,
        int orderId,
        long createdTimestamp,
        long updatedTimestamp,
        long lastLoginTime,
        boolean isActive,
        boolean isVerified,
        boolean hasSubscription,
        double accountBalance
) {
}

