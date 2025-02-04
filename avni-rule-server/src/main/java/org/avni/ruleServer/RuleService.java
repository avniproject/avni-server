package org.avni.ruleServer;

import org.avni.ruleServer.domain.Imports;
import org.avni.ruleServer.domain.RuleInput;
import org.avni.ruleServer.domain.RuleParams;
import org.avni.server.dao.DashboardRepository;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.domain.CHSBaseEntity;
import org.avni.server.domain.ProgramEnrolment;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

@Component
public class RuleService {
    private final DashboardRepository dashboardRepository;
    private final ProgramEnrolmentRepository programEnrolmentRepository;

    @Autowired
    public RuleService(DashboardRepository dashboardRepository, ProgramEnrolmentRepository programEnrolmentRepository) {
        this.dashboardRepository = dashboardRepository;
        this.programEnrolmentRepository = programEnrolmentRepository;
    }

    public void init() throws IOException {
        String projectPath = System.getProperty("user.dir");
        String jsFilePath = "avni-rule-server/build/resources/js/ruleInvoker.js";

        Context context = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("js.esm-eval-returns-exports", "true") // Enable ECMAScript modules
                .build();

        context.eval(Source.newBuilder("js", new File("avni-rule-server/build/resources/js/exports/ruleInvoker.js")).build());
        Value bindings = context.getBindings("js");
        Value ruleInvoker = bindings.getMember("ruleInvoker"); // npm module name

        String ruleFilePath = "avni-rule-server/build/resources/js/ruleSample.js";
        String ruleFileContents = new String(Files.readAllBytes(Paths.get(projectPath, ruleFilePath)));

        Value ruleCaller = ruleInvoker.getMember("callRule");
        ProgramEnrolment programEnrolment = programEnrolmentRepository.findOne(138234l);
        Value output = ruleCaller.execute(ruleFileContents, programEnrolment);
//        Value value = loadNPMModule(context);

//        Value rulesConfig = rulesConfigInvoker.execute();
        Source source = Source.newBuilder("js", Paths.get(projectPath, jsFilePath).toFile())
                .mimeType("application/javascript+module")
                .build();

//        ProgramEnrolment programEnrolment = programEnrolmentRepository.findOne(138234l);


//        Source ruleSource = Source.newBuilder("js", Paths.get(projectPath, ruleFile).toFile())
//                .mimeType("application/javascript+module")
//                .build();

//        Value invoker = context.eval("js", ruleSource.getCharacters());
//        RuleInput ruleInput = new RuleInput();
//        ruleInput.setImports(createImports(rulesConfig));
//        ruleInput.setParams(createParams());
//        Value mainFunction = invoker.invokeMember("mainFunction");
//        Value mainFunction = invoker.execute();
//        mainFunction.invokeMember("hello", "bar", dashboardRepository);
    }

    private Imports createImports(Value rulesConfig) {
        Imports imports = new Imports();
        imports.setRulesConfig(rulesConfig);
        return imports;
    }

    private RuleParams createParams(CHSBaseEntity entity) {
        RuleParams ruleParams = new RuleParams();
        ruleParams.setDecisions(new ArrayList<>());
        ruleParams.setEntity(entity);
        return ruleParams;
    }

    private Value loadNPMModule(Context context) {
        File file = new File("avni-rule-server/build/resources/js/node_modules/rules-config/rules.js");
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("load('file:/");
        stringBuffer.append(file.getAbsolutePath());
        stringBuffer.append("')");
        context.eval("js", stringBuffer.toString());
        Value bindings = context.getBindings("js");
        return bindings;
    }
}
