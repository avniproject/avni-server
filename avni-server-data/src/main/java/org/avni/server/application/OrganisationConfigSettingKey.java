package org.avni.server.application;

public enum OrganisationConfigSettingKey {
    languages, searchFilters, myDashboardFilters, lowestAddressLevelType, saveDrafts, enableComments,
    searchResultFields, useMinioForStorage, useKeycloakAsIDP, skipRuleExecution, customRegistrationLocations, enableMessaging,
    donotRequirePasswordChangeOnFirstLogin, failOnValidationError, guideUserToRegisterButton,
    enableSqliteSnapshotGeneration, enabledSqliteSnapshotGenerationAt, disabledSqliteSnapshotGenerationAt,
    // Server-only per-org/per-data-class storage routing + named target metadata
    // (avniproject/avni-server#1012). MUST be excluded from /web/organisationConfig and device sync
    // - see OrganisationConfig.SERVER_ONLY_SETTING_KEYS.
    storageBackends, storageTargets
}
