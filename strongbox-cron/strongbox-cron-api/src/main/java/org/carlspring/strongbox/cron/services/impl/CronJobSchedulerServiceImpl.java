package org.carlspring.strongbox.cron.services.impl;

import org.carlspring.strongbox.cron.domain.CronTaskConfigurationDto;
import org.carlspring.strongbox.cron.domain.GroovyScriptNamesDto;
import org.carlspring.strongbox.cron.jobs.GroovyCronJob;
import org.carlspring.strongbox.cron.services.CronJobSchedulerService;

import javax.inject.Inject;
import java.util.Set;
import java.util.UUID;

import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @author Yougeshwar
 * @author Pablo Tirado
 */
@Service
public class CronJobSchedulerServiceImpl
        implements CronJobSchedulerService
{

    private static final Logger logger = LoggerFactory.getLogger(CronJobSchedulerServiceImpl.class);

    @Inject
    private Scheduler scheduler;

    @Override
    public void scheduleJob(CronTaskConfigurationDto cronTaskConfiguration)
    {
        String jobClassName = cronTaskConfiguration.getJobClass();
        Class<? extends Job> jobClass;
        try
        {
            jobClass = (Class<? extends Job>) Class.forName(jobClassName);
        }
        catch (ClassNotFoundException e)
        {
            logger.error(String.format("Failed to schedule cron job [%s]", jobClassName), e);

            return;
        }

        // delete old job if exists
        deleteJob(cronTaskConfiguration.getUuid());

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("config", cronTaskConfiguration);

        JobKey jobKey = JobKey.jobKey(cronTaskConfiguration.getUuid().toString());
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                                        .withIdentity(jobKey)
                                        .setJobData(jobDataMap)
                                        .storeDurably()
                                        .build();

        try
        {
            scheduler.addJob(jobDetail, true);
            logger.debug("Job '{}' added to the Scheduler.", cronTaskConfiguration.getUuid());
        }
        catch (SchedulerException e)
        {
            logger.error(String.format("Failed to add Cron Job [%s] to the Scheduler", cronTaskConfiguration), e);
            return;
        }

        boolean scheduleJob = true;

        if (cronTaskConfiguration.shouldExecuteImmediately())
        {
            try
            {
                scheduler.triggerJob(jobKey);
                logger.debug("Job '{}' triggered by the Scheduler.", cronTaskConfiguration.getUuid());
            }
            catch (SchedulerException e)
            {
                logger.error(String.format("Failed to trigger Cron Job [%s] by the Scheduler", cronTaskConfiguration),
                             e);
                return;
            }

            scheduleJob = !cronTaskConfiguration.isOneTimeExecution();
        }

        if (!scheduleJob)
        {
            logger.debug("Job '{}' won't be scheduled based on the cron expression.", cronTaskConfiguration.getUuid());
            return;
        }

        TriggerKey triggerKey = TriggerKey.triggerKey(cronTaskConfiguration.getUuid().toString());
        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                                                               .withIdentity(triggerKey)
                                                               .forJob(jobDetail);

        String cronExpression = cronTaskConfiguration.getCronExpression();
        triggerBuilder.withSchedule(CronScheduleBuilder.cronSchedule(cronExpression));
        Trigger trigger = triggerBuilder.build();

        try
        {
            scheduler.scheduleJob(trigger);
            logger.debug("Job '{}' scheduled.", cronTaskConfiguration.getUuid());
        }
        catch (SchedulerException e)
        {
            logger.error(String.format("Failed to schedule Cron Job:%n [%s]", cronTaskConfiguration), e);

            return;
        }
    }

    @Override
    public void deleteJob(UUID cronTaskConfigurationUuid)
    {
        JobKey jobKey = JobKey.jobKey(cronTaskConfigurationUuid.toString());

        try
        {
            scheduler.deleteJob(jobKey);
            logger.debug("Job '{}' deleted and un-scheduled.", cronTaskConfigurationUuid);
        }
        catch (SchedulerException e)
        {
            logger.error(String.format("Failed to delete cron job [%s]", jobKey));
        }
    }

    @Override
    public GroovyScriptNamesDto getGroovyScriptsName()
    {
        GroovyScriptNamesDto groovyScriptNames = new GroovyScriptNamesDto();

        Set<JobKey> jobKeySet;
        try
        {
            jobKeySet = scheduler.getJobKeys(GroupMatcher.anyJobGroup());
        }
        catch (SchedulerException e)
        {
            return groovyScriptNames;
        }

        for (JobKey jobKey : jobKeySet)
        {
            JobDetail jobDetail;

            try
            {
                jobDetail = scheduler.getJobDetail(jobKey);
            }
            catch (SchedulerException e)
            {
                continue;
            }

            JobDataMap jobDataMap = jobDetail.getJobDataMap();

            CronTaskConfigurationDto configuration = (CronTaskConfigurationDto) jobDataMap.get("config");

            if (!GroovyCronJob.class.getName().equals(configuration.getJobClass()))
            {
                continue;
            }

            String groovyScriptName = configuration.getProperties().get("fileName");
            groovyScriptNames.addName(groovyScriptName);
        }

        return groovyScriptNames;
    }

}
