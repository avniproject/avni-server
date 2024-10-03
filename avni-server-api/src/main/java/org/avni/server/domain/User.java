package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.validator.routines.EmailValidator;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.util.ValidationUtil;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.avni.server.web.validation.ValidationException;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Type;
import org.hibernate.proxy.HibernateProxyHelper;
import org.joda.time.DateTime;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;


@Entity
@Table(name = "users")
@BatchSize(size = 100)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class User {
    public static final String DEFAULT_SUPER_ADMIN = "5fed2907-df3a-4867-aef5-c87f4c78a31a";

    @Column
    @NotNull
    private String username;

    @Column
    private String name;

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @Id
    private Long id;

    @Column
    @NotNull
    private String uuid;

    @Column
    private Long organisationId;

    @Column
    private String email;

    @Column
    private String phoneNumber;

    @Column
    private boolean disabledInCognito;

    // Audit is not getting used for managing users because, the application goes in a loop managing audit information generically and automatically assigning the user to the entities
    @JsonIgnore
    @JoinColumn(name = "created_by_id")
    @ManyToOne(targetEntity = User.class)
    private User createdBy;

    private DateTime createdDateTime;

    @JsonIgnore
    @JoinColumn(name = "last_modified_by_id")
    @ManyToOne(targetEntity = User.class)
    private User lastModifiedBy;

    private DateTime lastModifiedDateTime;

    @Column
    private boolean isVoided;

    @Column
    private boolean isOrgAdmin;

    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "user")
    private Set<AccountAdmin> accountAdmin = new HashSet<>();

    @Transient
    /*
      Using transient field isAdmin is problematic when the user is not the current user. User UserService.isAdmin() instead, except in JDBC interceptor.
     */
    private boolean isAdmin;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "catchment_id")
    private Catchment catchment;

    @NotNull
    @Column(name = "operating_individual_scope")
    @Enumerated(value = EnumType.STRING)
    private OperatingIndividualScope operatingIndividualScope;

    @Column
    @Type(type = "jsonObject")
    private JsonObject settings;

    @Column(name = "sync_settings")
    @Type(type = "jsonObject")
    private JsonObject syncSettings;

    @Column(name = "ignore_sync_settings_in_dea")
    private boolean ignoreSyncSettingsInDEA;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "user")
    private List<UserGroup> userGroups;

    @JsonIgnore
    public boolean hasAllPrivileges() {
        return getUserGroups().stream().anyMatch(userGroup -> userGroup.getGroup().isHasAllPrivileges());
    }

    public List<UserSyncSettings> getSyncSettingsList() {
        User.SyncSettingKeys.subjectTypeSyncSettings.name();
        return ObjectMapperSingleton.getObjectMapper().convertValue(syncSettings.get(User.SyncSettingKeys.subjectTypeSyncSettings.name()), new TypeReference<List<UserSyncSettings>>() {});
    }

    public boolean isPartOfUserGroup(String userGroup) {
        return this.getUserGroups().stream().anyMatch(ug -> ug.getGroup().getName().equals(userGroup));
    }

    public enum SyncSettingKeys {
        syncAttribute1,
        syncAttribute2,
        syncAttribute1Values,
        syncAttribute2Values,
        subjectTypeSyncSettings,
    }

    public static final String USER = "user";

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static User newUser(String name, Long orgId) {
        User user = new User();
        user.setName(name);
        user.setOrganisationId(orgId);
        user.setUuid(UUID.randomUUID().toString());
        return user;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(Long organisationId) {
        this.organisationId = organisationId;
    }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isDisabledInCognito() {
        return disabledInCognito;
    }

    public void setDisabledInCognito(boolean disabledInCognito) {
        this.disabledInCognito = disabledInCognito;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Catchment getCatchment() {
        return catchment;
    }

    public Optional<Long> getCatchmentId() {
        return Optional.ofNullable(catchment != null ? catchment.getId() : null);
    }

    public void setCatchment(@NotNull Catchment catchment) {
        this.catchment = catchment;
    }

    public void removeCatchment() {
        this.catchment = null;
        this.operatingIndividualScope = OperatingIndividualScope.None;
    }

    public Boolean isVoided() {
        return isVoided;
    }

    public void setVoided(boolean voided) {
        isVoided = voided;
    }

    public List<UserGroup> getUserGroups() {
        if (userGroups == null) {
            userGroups = new ArrayList<>();
        }
        return userGroups.stream().filter(ug -> !ug.isVoided()).collect(Collectors.toList());
    }

    public void setUserGroups(List<UserGroup> userGroups) {
        this.userGroups = userGroups;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || HibernateProxyHelper.getClassWithoutInitializingProxy(this) != HibernateProxyHelper.getClassWithoutInitializingProxy(o)) return false;

        User other = (User) o;

        if (getId() != null ? !getId().equals(other.getId()) : other.getId() != null) return false;
        return getUuid() != null ? getUuid().equals(other.getUuid()) : other.getUuid() == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        return result;
    }

    @JsonIgnore
    public boolean isNew() {
        Long id = this.getId();
        return (id == null || id == 0);
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public void setCreatedDateTime(DateTime createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public void setLastModifiedBy(User lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public void setLastModifiedDateTime(DateTime lastModifiedDateTime) {
        this.lastModifiedDateTime = lastModifiedDateTime;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getCreatedByUserName() {
        return this.createdBy.getName();
    }

    public String getLastModifiedByUserName() {
        return this.lastModifiedBy.getName();
    }

    public DateTime getCreatedDateTime() {
        return createdDateTime;
    }

    public User getLastModifiedBy() {
        return lastModifiedBy;
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public JsonObject getSyncSettings() {
        return syncSettings;
    }

    public void setSyncSettings(JsonObject syncSettings) {
        this.syncSettings = syncSettings;
    }

    public boolean isIgnoreSyncSettingsInDEA() {
        return ignoreSyncSettingsInDEA;
    }

    public void setIgnoreSyncSettingsInDEA(boolean ignoreSyncSettingsInDEA) {
        this.ignoreSyncSettingsInDEA = ignoreSyncSettingsInDEA;
    }

    public String[] getRoles() {
        ArrayList<String> roles = new ArrayList<>();
        if (!isAdmin()) {
            roles.add(USER);
        }
        return roles.toArray(new String[0]);
    }

    public Set<AccountAdmin> getAccountAdmin() {
        return accountAdmin;
    }

    public void setAccountAdmin(AccountAdmin accountAdmin) {
        this.accountAdmin.clear();
        if (accountAdmin != null) {
            this.accountAdmin.add(accountAdmin);
        }
    }

    /**
     * Using transient field isAdmin is problematic when the user is not the current user. User UserService.isAdmin() instead, except in JDBC interceptor.
     */
    public boolean isAdmin() {
        return isAdmin;
    }

    /**
     * Using transient field isAdmin is problematic when the user is not the current user. User UserService.isAdmin() instead, except in JDBC interceptor.
     */
    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
    }

    @NotNull
    public OperatingIndividualScope getOperatingIndividualScope() {
        return operatingIndividualScope;
    }

    public void setOperatingIndividualScope(@NotNull OperatingIndividualScope operatingIndividualScope) {
        this.operatingIndividualScope = operatingIndividualScope;
    }

    /**
     * Use only for mapping to contracts
     */
    public JsonObject getSettings() {
        return settings;
    }

    @JsonIgnore
    public UserSettings getUserSettings() {
        return new UserSettings(this.settings);
    }

    public void setSettings(JsonObject settings) {
        this.settings = settings;
    }

    public void setAuditInfo(User currentUser) {
        if (this.getCreatedBy() == null) {
            this.setCreatedBy(currentUser);
            this.setCreatedDateTime(DateTime.now());
        }
        this.setLastModifiedBy(currentUser);
        this.setLastModifiedDateTime(DateTime.now());
    }

    public void assignUUID() {
        this.uuid = UUID.randomUUID().toString();
    }

    public void assignUUIDIfRequired() {
        if (this.uuid == null) this.assignUUID();
    }

    public static void validateEmail(String email) {
        if (!EmailValidator.getInstance().isValid(email)) {
            throw new ValidationException(String.format("Invalid email address %s", email));
        }
    }

    /**
     * username must be at least 7 char and
     * must be in the format of xxx@yyy
     * where yyy is {@link Organisation#getUsernameSuffix()} and xxx represents user
     */
    public static void validateUsername(String username, String userSuffix) {
        if (username == null || !username.contains("@") || username.trim().length() < 7) {
            throw new ValidationException(String.format("Invalid username '%s'. It must be at least 7 characters.", username));
        }
        if (username.trim().indexOf("@") < 4) {
            throw new ValidationException(String.format("Invalid username '%s'. Name part should be at least 4 characters", username));
        }
        if (!username.trim().endsWith(userSuffix)) {
            throw new ValidationException(String.format("Invalid username '%s'. Include correct userSuffix %s at the end", username, userSuffix));
        }
        if (ValidationUtil.checkNullOrEmptyOrContainsDisallowedCharacters(username.trim(), ValidationUtil.COMMON_INVALID_CHARS_PATTERN)) {
            throw new ValidationException(String.format("Invalid username '%s', contains at least one disallowed character %s", username, ValidationUtil.COMMON_INVALID_CHARS));
        }
    }

    /**
     * name must not be empty and not have invalid characters
     */
    public static void validateName(String name) {
        if (ValidationUtil.checkNullOrEmptyOrContainsDisallowedCharacters(name, ValidationUtil.NAME_INVALID_CHARS_PATTERN)) {
            throw new ValidationException(String.format("Invalid name '%s', contains at least one disallowed character %s", name, ValidationUtil.NAME_INVALID_CHARS));
        }
    }
}
