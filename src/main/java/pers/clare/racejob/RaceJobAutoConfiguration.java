package pers.clare.racejob;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import pers.clare.racejob.impl.JdbcRaceJobStoreImpl;
import pers.clare.racejob.impl.RaceJobSchedulerImpl;

import javax.sql.DataSource;

@Configuration
@ConditionalOnBean(RaceJobProperties.class)
public class RaceJobAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RaceJobScheduler.class)
    public RaceJobScheduler raceJobScheduler(
            RaceJobProperties jobProperties
            , RaceJobStore jobStore
            , @Nullable RaceJobEventBus jobEventService
    ) {
        return new RaceJobSchedulerImpl(jobProperties, jobStore, jobEventService);
    }

    @Bean
    @ConditionalOnMissingBean(RaceJobStore.class)
    public RaceJobStore jobStore(
            DataSource dataSource
    ) {
        return new JdbcRaceJobStoreImpl(dataSource);
    }
}
