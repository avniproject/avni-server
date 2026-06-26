package org.avni.server.application;

public enum OrganisationConfigSettingKey {
    languages, searchFilters, myDashboardFilters, lowestAddressLevelType, saveDrafts, enableComments,
    searchResultFields, useMinioForStorage, useKeycloakAsIDP, skipRuleExecution, customRegistrationLocations, enableMessaging,
    donotRequirePasswordChangeOnFirstLogin, failOnValidationError, guideUserToRegisterButton,
    enableSqliteSnapshotGeneration, enabledSqliteSnapshotGenerationAt, disabledSqliteSnapshotGenerationAt,
    // server-only - excluded from clients via OrganisationConfig.SERVER_ONLY_SETTING_KEYS
    storageBackends, storageTargets
}
