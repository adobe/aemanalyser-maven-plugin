/*
  Copyright 2024 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.impl;

import com.adobe.aem.analyser.validators.repoinit.RepoInitValidationReport;
import com.adobe.aem.analyser.validators.repoinit.RepoInitValidator;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.analyser.task.AnalyserTask;
import org.apache.sling.feature.analyser.task.AnalyserTaskContext;

import java.util.List;

/**
 * Analyser task that validates Repoinit definitions for conflicts within a feature.
 * <p>
 * This task runs the {@link RepoInitValidator} against the provided feature and checks
 * whether there are any conflicting Repoinit statements. If conflicts are detected,
 * a warning is reported to the analyser context.
 */
public class RepoInitConflictAnalyserTask implements AnalyserTask {


    @Override
    public String getId() {
        return "repoinit-conflict-validation";
    }

    @Override
    public String getName() {
        return "Repoinit Conflict Validation";
    }

    /**
     * Executes the Repoinit conflict validation.
     * <p>
     * This method retrieves the feature from the context, validates it using
     * {@link RepoInitValidator}, and reports a warning if any conflicts are found.
     *
     * @param context analyser task context containing the feature to validate
     */
    @Override
    public void execute(final AnalyserTaskContext context) {
        Feature feature = context.getFeature();

        RepoInitValidationReport report = RepoInitValidator.validateRepoinit(feature);

        if (!report.hasConflicts()) {
            return;
        }

        List<String> messages = report.generate();
        messages.forEach(context::reportWarning);
    }
}
