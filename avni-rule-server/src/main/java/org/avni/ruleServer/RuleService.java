package org.avni.ruleServer;

import org.avni.server.dao.DashboardRepository;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Paths;

@Component
public class RuleService {
    private final DashboardRepository dashboardRepository;

    @Autowired
    public RuleService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }
    
    public void init() {
        String projectPath = System.getProperty("user.dir");
        String jsFilePath = "avni-rule-server/build/resources/js/main.js";

        Context context = Context.newBuilder("js")
                .allowAllAccess(true) // Allow access to file system if needed
                .option("js.esm-eval-returns-exports", "true") // Enable ECMAScript modules
                .build();
        Source source = Source.newBuilder("js", Paths.get(projectPath, jsFilePath).toFile())
                .mimeType("application/javascript+module")
                .build();
        Value invokerO = context.eval(source);
        try (context) {
            // Read and execute the main JavaScript file
            
            
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }

    public void getDashboardReportCardsCount() {
        dashboardRepository.getAllNames();
    }
}
