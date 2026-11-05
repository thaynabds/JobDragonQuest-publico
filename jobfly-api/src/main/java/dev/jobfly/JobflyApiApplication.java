package dev.jobfly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobflyApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobflyApiApplication.class, args);
    }
}
