package org.avni.server.application;

public enum OrganisationConfigSettingKey {
    languages, searchFilters, myDashboardFilters, lowestAddressLevelType, saveDrafts, enableComments,
    searchResultFields, useMinioForStorage, useKeycloakAsIDP, skipRuleExecution, customRegistrationLocations, enableMessaging,
    donotRequirePasswordChangeOnFirstLogin, failOnValidationError, guideUserToRegisterButton,
    enableSqliteSnapshotGeneration, enabledSqliteSnapshotGenerationAt, disabledSqliteSnapshotGenerationAt, enableAbha,
    // ABDM record sharing (M2, M3) for an organisation with ABHA on; off unless set
    enableAbdmRecordSharing,
    // server-only - excluded from clients via OrganisationConfig.SERVER_ONLY_SETTING_KEYS
    storageBackends, storageTargets
}
