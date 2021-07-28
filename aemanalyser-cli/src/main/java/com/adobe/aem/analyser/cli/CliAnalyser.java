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
package com.adobe.aem.analyser.cli;

import java.util.List;
import java.util.Map;

import com.adobe.aem.analyser.AemAnalyser;

public class CliAnalyser extends AemAnalyser {
    @Override
    protected boolean checkClassifier(String classifier) {
        return true;
    }

    @Override
    protected void logOutput(List<String> output, Map<String, List<String>> messages, String type) {
        messages.values().forEach(output::addAll);
    }
}
