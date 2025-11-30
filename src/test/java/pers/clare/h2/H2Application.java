package pers.clare.h2;

import org.h2.tools.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.sql.SQLException;

@SpringBootApplication
public class H2Application {
    private static ConfigurableApplicationContext application;


    public static void main(String[] args) {
        if (application == null)
            application = SpringApplication.run(H2Application.class, "--spring.profiles.active=h2server");
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public Server inMemoryH2DatabaseServer() throws SQLException {
        return Server.createTcpServer(
                "-tcp", "-tcpAllowOthers", "-tcpPort", "9999");
    }
}
