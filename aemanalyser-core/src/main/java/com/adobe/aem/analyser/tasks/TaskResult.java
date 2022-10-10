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
package com.adobe.aem.analyser.tasks;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * This class holds the result from an analyse run.
 */
public class TaskResult {

    public static final class Annotation {
        private final String source;
        
        private final int lineNumber;

        private final int columnNumber;

        private final String message;

        public Annotation(final String message) {
            this(null, message);
        }

        public Annotation(final String source, final String message) {
            this(source, message, -1, -1);
        }

        public Annotation(final String source, final String message, final int lnr, final int cnr) {
            this.source = source;
            this.message = message;
            this.lineNumber = lnr;
            this.columnNumber = cnr;
        }

        /**
         * @return the source
         */
        public String getSource() {
            return source;
        }

        /**
         * @return the lineNumber
         */
        public int getLineNumber() {
            return lineNumber;
        }

        /**
         * @return the columnNumber
         */
        public int getColumnNumber() {
            return columnNumber;
        }

        /**
         * @return the message
         */
        public String getMessage() {
            return message;
        }

        public String toString() {
            if ( this.getSource() == null ) {
                return this.getMessage();
            }
            String location = null;
            if ( this.getLineNumber() != -1 ) {
                if ( this.getColumnNumber() != -1 ) {
                    location = "[".concat(String.valueOf(this.getLineNumber())).concat(":").concat(String.valueOf(this.getColumnNumber())).concat("]");
                } else {
                    location = "[".concat(String.valueOf(this.getLineNumber())).concat("]");
                }
            }
            return this.getSource().concat(location == null ? "" : location).concat(": ").concat(this.getMessage());
        }

        public String toActionString(final String level) {
            final String prefix = "::".concat(level).concat(" ");
            final String postfix = "::".concat(this.getMessage());
            if ( this.getSource() == null ) {
                return prefix.concat(postfix);
            }
            // TODO location
            return prefix.concat("file=").concat(this.getSource()).concat(postfix);
        }
    
        @Override
        public int hashCode() {
            return Objects.hash(source, lineNumber, columnNumber, message);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof Annotation))
                return false;
            Annotation other = (Annotation) obj;
            return Objects.equals(source, other.source) && lineNumber == other.lineNumber
                    && columnNumber == other.columnNumber && Objects.equals(message, other.message);
        }
    }

    private final Set<Annotation> errors = new LinkedHashSet<>();

    private final Set<Annotation> warnings = new LinkedHashSet<>();

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
    public Set<Annotation> getErrors() {
        return this.errors;
    }

    /**
     * Get the list of warnings. The list is mutable.
     * @return The list of warnings, might be empty.
     */
    public Set<Annotation> getWarnings() {
        return this.warnings;
    }
}