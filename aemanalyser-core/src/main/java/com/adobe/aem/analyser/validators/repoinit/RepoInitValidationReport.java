/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.validators.repoinit;

import org.apache.sling.feature.Feature;
import org.apache.sling.repoinit.parser.operations.CreatePath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RepoInitValidationReport {

    private final Map<Feature, List<CreatePath[]>> conflicts = new LinkedHashMap<>();

    public void addConflicts(Feature feature, List<CreatePath[]> featureConflicts) {
        if (!featureConflicts.isEmpty()) {
            conflicts.put(feature, featureConflicts);
        }
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    public List<String> generate() {
        List<String> messages = new ArrayList<>();
        messages.add("Repoinit validation results:");

        if (!hasConflicts()) {
            messages.add("No issues found");
        } else {
            for (Map.Entry<Feature, List<CreatePath[]>> entry : conflicts.entrySet()) {
                Feature feature = entry.getKey();
                List<CreatePath[]> featureConflicts = entry.getValue();

                messages.add("Incorrect repoinit for feature " + feature);
                messages.add("Found " + featureConflicts.size() + " sets of conflicting repoinit statements");

                for (CreatePath[] conflict : featureConflicts) {
                    messages.add("Conflicting statement :\n  "
                            + conflict[0].asRepoInitString().stripTrailing()
                            + "\n"
                            + conflict[1].asRepoInitString().stripTrailing());
                }
            }
        }

        return messages;
    }
}
