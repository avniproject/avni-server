import Types from "./SubjectType/Types";
import { default as UUID } from "uuid";
import _, { map } from "lodash";

export function programReducer(program, action) {
  switch (action.type) {
    case "name":
      return { ...program, name: action.payload };
    case "colour":
      return { ...program, colour: action.payload };
    case "active":
      return { ...program, active: action.payload };
    case "programSubjectLabel":
      return { ...program, programSubjectLabel: action.payload };
    case "enrolmentSummaryRule":
      return { ...program, enrolmentSummaryRule: action.payload };
    case "enrolmentEligibilityCheckRule":
      return { ...program, enrolmentEligibilityCheckRule: action.payload };
    case "programEnrolmentForm":
      return { ...program, programEnrolmentForm: action.payload };
    case "programExitForm":
      return { ...program, programExitForm: action.payload };
    case "setData":
      return {
        ...program,
        name: action.payload.name,
        colour: action.payload.colour,
        programSubjectLabel: action.payload.programSubjectLabel,
        enrolmentSummaryRule: action.payload.enrolmentSummaryRule,
        enrolmentEligibilityCheckRule: action.payload.enrolmentEligibilityCheckRule,
        active: action.payload.active
      };
    default:
      return program;
  }
}

export function encounterTypeReducer(encounterType, action) {
  switch (action.type) {
    case "name":
      return { ...encounterType, name: action.payload };
    case "active":
      return { ...encounterType, active: action.payload };
    case "encounterEligibilityCheckRule":
      return { ...encounterType, encounterEligibilityCheckRule: action.payload };
    case "programEncounterForm":
      return { ...encounterType, programEncounterForm: action.payload };
    case "programEncounterCancellationForm":
      return { ...encounterType, programEncounterCancellationForm: action.payload };
    case "setData":
      return {
        ...encounterType,
        name: action.payload.name,
        encounterEligibilityCheckRule: action.payload.encounterEligibilityCheckRule,
        active: action.payload.active
      };
    default:
      return encounterType;
  }
}

export function subjectTypeReducer(subjectType, action) {
  switch (action.type) {
    case "name":
      return { ...subjectType, name: action.payload };
    case "group":
      return { ...subjectType, group: action.payload.group, groupRoles: action.payload.groupRoles };
    case "household":
      return {
        ...subjectType,
        group: action.payload.group,
        household: action.payload.household,
        groupRoles: action.payload.groupRoles
      };
    case "groupRole":
      return { ...subjectType, groupRoles: action.payload };
    case "registrationForm":
      return { ...subjectType, registrationForm: action.payload };
    case "active":
      return { ...subjectType, active: action.payload };
    case "type":
      const type = action.payload;
      if (!Types.isGroup(type)) {
        return { ...subjectType, type, groupRoles: [] };
      }
      const groupRoles = Types.isHousehold(type) ? _getHouseholdRoles() : [];
      const memberSubjectType = Types.isHousehold(type)
        ? _.map(groupRoles, ({ subjectMemberName }) => subjectMemberName)[0]
        : "";
      return { ...subjectType, type, groupRoles, memberSubjectType };
    case "householdMemberSubject":
      const roles = map(
        subjectType.groupRoles,
        ({ groupRoleUUID, role, minimumNumberOfMembers, maximumNumberOfMembers }) => ({
          subjectMemberName: action.payload,
          groupRoleUUID,
          role,
          minimumNumberOfMembers,
          maximumNumberOfMembers
        })
      );
      return { ...subjectType, groupRoles: roles, memberSubjectType: action.payload };
    case "setData":
      return {
        ...subjectType,
        name: action.payload.name,
        groupRoles: action.payload.groupRoles,
        uuid: action.payload.uuid,
        active: action.payload.active,
        type: action.payload.type,
        memberSubjectType: Types.isHousehold(action.payload.type)
          ? _.map(action.payload.groupRoles, ({ subjectMemberName }) => subjectMemberName)[0]
          : "",
        subjectSummaryRule: action.payload.subjectSummaryRule
      };
    case "subjectSummaryRule":
      return { ...subjectType, subjectSummaryRule: action.payload };
    default:
      return subjectType;
  }
}

const _getHouseholdRoles = () => {
  const roles = [];
  roles.push(_getRole("Head of household", 1, 1));
  roles.push(_getRole("Member", 1, 100));
  return roles;
};

const _getRole = (role, minimumNumberOfMembers, maximumNumberOfMembers) => ({
  groupRoleUUID: UUID.v4(),
  role,
  minimumNumberOfMembers,
  maximumNumberOfMembers
});
