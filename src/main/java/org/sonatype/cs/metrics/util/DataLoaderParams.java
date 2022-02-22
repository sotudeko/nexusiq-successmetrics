package org.sonatype.cs.metrics.util;

import org.springframework.stereotype.Component;

@Component
public class DataLoaderParams {

    public static final String smDatafile = "successmetrics.csv";
    public static final String smHeader = "applicationId,applicationName,applicationPublicId,";

    public static final String aeDatafile = "application_evaluations.csv";
    public static final String aeFileHeader = "applicationName,evaluationDate,stage";

    public static final String cwDatafile = "waivers.csv";
    public static final String cwFileHeader = "applicationName,stage,packageUrl,policyName,threatLevel,comment,createDate,expiryTime";

    public static final String pvDatafile = "policy_violations.csv";
    public static final String pvFileHeader = "policyName,reason,applicationName,openTime,component,stage,threatLevel";

    public static final String qcompDatafile = "quarantined_components.csv";
    public static final String qcompHeader = "repository,quarantineDate,dateCleared,displayName,format,quarantined,policyName,threatLevel,reason";

    public static final String qscompDatafile = "quarantined_components_summary.csv";
    public static final String qscompHeader = "repositoryCount,quarantineEnabledCount,quarantineenabled,totalComponentCount,quarantinedComponentCount";

    public static final String afqcomponentDatafile = "autoreleased_from_quarantine_components.csv";
    public static final String afqcomponentHeader = "displayName,repository,quarantineDate,dateCleared";

    public static final String afqsDatafile = "autoreleased_from_quarantine_summary.csv";
    public static final String qcsDatafile = "quarantined_components_summary.csv";

    public static final String afqconfigDatafile = "autoreleased_from_quarantine_config.csv";
    public static final String afqconfigHeader = "id,name,autoReleaseQuarantineEnabled";

    public static final String htmlTemplate = "templates/thymeleaf_template";
}
