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

import java.io.IOException;

import com.adobe.aem.analyser.tasks.TaskResult;

public class Main {
    
    public static void main(final String[] args) {
        final CliParser parser = new CliParser();
        parser.parse(args);

        String level = "info";
        if ( parser.options.contains("v") ) {
            level = "debug";
        }
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level);
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.levelInBrackets", "true");
        System.setProperty("org.slf4j.simpleLogger.showLogName", "false");

        System.out.println("AEM Project Tool");
        System.out.println("THIS IS AN ALPHA VERSION - PLEASE USE WITH CAUTION!");

        final AbstractCommand command;
        if ( "validate-configs".equals(parser.command) ) {
            command = new AnalyseConfigsCommand();
        } else if ( "convert-configs".equals(parser.command) ) {
            command = new ConvertConfigsCommand();
        } else {
            if ( parser.command == null ) {
                throw new IllegalArgumentException("No command specified");
            }
            throw new IllegalArgumentException("Unknown command " + parser.command + " specified");
        }
        command.setParser(parser);
        command.validate();
        try {
            final TaskResult result = command.doExecute();
            if ( result != null ) {
                result.getWarnings().stream().forEach(a -> System.out.println(a.toActionString(command.isStrict() ? "error" : "warning")));
                result.getErrors().stream().forEach(a -> System.out.println(a.toActionString("error")));
                if ( !result.getErrors().isEmpty() || ( command.isStrict() && !result.getWarnings().isEmpty()) ) {
                    System.exit(1);
                }    
            }
        } catch ( final IOException ioe ) {
            throw new RuntimeException(ioe.getMessage(), ioe);
        }        
    }
}
