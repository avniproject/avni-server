package org.avni;

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
    public static void main(String[] args) {
        ApplicationContext applicationContext = SpringApplication.run(Main.class);
        DashboardRepository dashboardRepository = applicationContext.getBean(DashboardRepository.class);
        Dashboard dashboard = dashboardRepository.findOne(1l);
        this.initialiseJavaScript();
        System.out.println(dashboard.getName());
    }

    private void initialiseJavaScript() throws IOException {

    }
}
