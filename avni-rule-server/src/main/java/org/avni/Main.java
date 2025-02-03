package org.avni;

import org.avni.ruleServer.RuleService;
import org.avni.server.dao.CustomJpaRepositoryImpl;
import org.avni.server.dao.DashboardRepository;
import org.avni.server.domain.Dashboard;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;
import java.nio.file.Paths;

@SpringBootApplication
@EnableJpaRepositories(repositoryBaseClass = CustomJpaRepositoryImpl.class)
public class Main {
    public static void main(String[] args) throws IOException {
        ApplicationContext applicationContext = SpringApplication.run(Main.class);
        RuleService ruleService = applicationContext.getBean(RuleService.class);
        ruleService.init();
//        initialiseJavaScript();
//        System.out.println(dashboard.getName());
    }

    private static void initialiseJavaScript() throws IOException {
        String projectPath = System.getProperty("user.dir");
        String jsFilePath = "avni-rule-server/build/resources/js/main.js";

        try (Context context = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("js.esm-eval-returns-exports", "true")
                .build()) {

            Source source = Source.newBuilder("js", Paths.get(projectPath, jsFilePath).toFile())
                    .mimeType("application/javascript+module")
                    .build();
            Value result = context.eval(source);
            System.out.println(result);
        }
    }
}
