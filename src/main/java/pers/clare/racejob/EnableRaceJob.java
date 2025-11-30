package pers.clare.racejob;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@SuppressWarnings("unused")
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({RaceJobProperties.class})
@Configuration
public @interface EnableRaceJob {
    @AliasFor(
            annotation = Configuration.class
    )
    String value() default "";
}
