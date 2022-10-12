/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.project.tool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CliParser {

    public String command;

    public final Set<String> options = new HashSet<>();

    public final Map<String, String> arguments = new HashMap<>();

    public void parse(final String[] args) {
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("--")) {
                    final String arg = args[i].substring(2);
                    if (arguments.containsKey(arg)) {
                        throw new IllegalArgumentException("Argument " + arg + " specified more than once");
                    }
                    if (i == args.length - 1) {
                        throw new IllegalArgumentException("No value for argument " + arg);
                    }
                    arguments.put(arg, args[i + 1]);
                    i++;
                } else if (args[i].startsWith("-")) {
                    options.add(args[i].substring(1));
                } else {
                    if (command != null) {
                        throw new IllegalArgumentException("More than one command specified");
                    }
                    command = args[i];
                }
            }
        }
    }

    public boolean getBooleanArgument(final String key, final boolean defaultValue) {
        final String val = this.arguments.get(key);
        if ( val != null ) {
            if ( "true".equalsIgnoreCase(val) ) {
                return true;
            } else if ("false".equalsIgnoreCase(val) ) {
                return false;
            }
            throw new IllegalArgumentException("Invalid value for boolean argument " + key + " : " + val);
        }
        return defaultValue;
    }
}
