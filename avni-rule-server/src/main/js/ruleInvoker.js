import * as rulesConfig from "rules-config";

export function callRule(ruleCode, entity) {
    console.log("performing eval", rulesConfig);
    const ruleFunction = eval(ruleCode);
    console.log("eval completed");
    const imports = {rulesConfig};
    return ruleFunction({params: {entity: entity, decisions: {enrolmentDecisions: []}}, imports: imports});
}
