update organisation_config set settings = settings - 'enableApprovalWorkflow'
    where settings->>'enableApprovalWorkflow' is not null;
