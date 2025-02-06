The execution of prototype happens on startup of the avni-rule-server jar. Before the execution we need the following setup for this prototype.

## Code
In avni-rule-server main there is js folder alongside java folder. This is an npm project with only one file `ruleInvoker.js`. There is a `package.json` and `webpack.config.js` to build the project. main.js is not required. As makefile `rule-server.mk` is used to build the project.
avni-rule-server is a graal vm application supported via `graal-sdk:23.0.0` and `org.graalvm.js:js`. Check the build.gradle for avni-rule-server for more details.

### Setup
1. In makefile run `make deps build-rule-invoker` to create npm module.

### Execution flow
1. org.avni.Main.java is the main class for avni-rule-server. It is the entry point for the application.
2. Main.java calls org.avni.ruleServer.RuleService.init() which loads the npm module in Java
3. It loads the rule that needs to be executed. Currently, a sample rule is loaded from the file system - `ruleSample.js`. 
4. It calls the exported entry point and then calls `callRule` method with the rule code as String and programEnrolment object loaded from repository.
5. The `ruleInvoker.js` has JS dependency on "rules-config" (and later any other JS libraries like lodash, moment).
6. `ruleInvoker.js` evals the rule code passed as string to it. It constructs the shape of the object expected by the rule. In this it uses the program enrolment object received from Java code.
7. The `sampleRule` uses complication builder which calls `findObservation` method (like it does on avni-model). When this method is called then it ends up calling newly added method org.avni.server.domain.ProgramEnrolment.findObservation in Java (passing parameters).
8. The `findObservation` method in Java code returns the results to the JS code.
9. The JS code then return string `[{name: "High Risk Conditions", value: []}]}` to Java code. We can use it as it is and send it back to caller (like web app) or use it in Java code by using ObjectMapper.
