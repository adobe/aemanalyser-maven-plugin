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
package com.adobe.aem.analyser;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the result from an analyse run.
 */
public class AemAnalyserResult {

    private final List<String> errors = new ArrayList<>();

    private final List<String> warnings = new ArrayList<>();

    /**
     * Are there any errors?
     * @return {@code true} if an error exists
     */
    public boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    /**
     * Are there any warnings?
     * @return {@code true} if a warning exists
     */
    public boolean hasWarnings() {
        return !this.warnings.isEmpty();
    }

    /**
     * Get the list of errors. The list is mutable.
     * @return The list of errors, might be empty.
     */
    public List<String> getErrors() {
        return this.errors;
    }

    /**
     * Get the list of warnings. The list is mutable.
     * @return The list of warnings, might be empty.
     */
    public List<String> getWarnings() {
        return this.warnings;
    }
}